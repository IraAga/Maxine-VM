/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
/**
 * The main program of the VM.
 * Loads, verifies and mmaps the boot image,
 * hands control over to the VM's compiled code, which has been written in Java,
 * by calling a VM entry point as a C function.
 *
 * @author Bernd Mathiske
 */
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <alloca.h>

#include "log.h"
#include "image.h"
#include "threads.h"
#include "messenger.h"
#include "os.h"
#if os_DARWIN
#include <crt_externs.h>
#endif

#include "maxine.h"

#define IMAGE_FILE_NAME  "maxine.vm"
#define DARWIN_STACK_ALIGNMENT ((Address) 16)
#define ENABLE_CARD_TABLE_VERIFICATION 0

// Size of extra space that is allocated as part of auxiliary space passed to the primordial thread.
// This space is used to record the address of all the reference fields that are written to. The recorded
// references are checked against the card table for corresponding dirty cards.
// Note : The 1 Gb space is just a guess-timate which can hold only 128 Mb of 64 bit references

#if ENABLE_CARD_TABLE_VERIFICATION
#define REFERENCE_BUFFER_SIZE (1024*1024*1024)
#else
#define REFERENCE_BUFFER_SIZE (0)
#endif

#if os_DARWIN
static char *_executablePath;
#endif

static void getExecutablePath(char *result) {
#if os_DARWIN
    if (realpath(_executablePath, result) == NULL) {
        fprintf(stderr, "could not read %s\n", _executablePath);
        exit(1);
    }
    int numberOfChars = strlen(result);
#elif os_GUESTVMXEN
    result[0] = 0;
    return;
#elif os_LINUX
    char *linkName = "/proc/self/exe";
#elif os_SOLARIS
    char *linkName = "/proc/self/path/a.out";
#else
#   error getExecutablePath() not supported on other platforms yet
#endif

#if os_LINUX || os_SOLARIS
    // read the symbolic link to figure out what the executable is.
    int numberOfChars = readlink(linkName, result, MAX_PATH_LENGTH);
    if (numberOfChars < 0) {
        log_exit(1, "Could not read %s\n", linkName);
    }
#endif

#if !os_GUESTVMXEN
    char *p;
    // chop off the name of the executable
    for (p = result + numberOfChars; p >= result; p--) {
        if (*p == '/') {
            p[1] = 0;
            break;
        }
    }
#endif
}

static void getImageFilePath(char *result) {
#if !os_GUESTVMXEN
    getExecutablePath(result);

    // append the name of the image to the executable path
    strcpy(result + strlen(result), IMAGE_FILE_NAME);
#endif
}

static int loadImage(void) {
    char imageFilePath[MAX_PATH_LENGTH];
    getImageFilePath(imageFilePath);
    return image_load(imageFilePath);
}

static void *openDynamicLibrary(char *path) {
#if log_LINKER
    if (path == NULL) {
        log_println("openDynamicLibrary(null)");
    } else {
        log_println("openDynamicLibrary(\"%s\")", path);
    }
#endif
    void *result = dlopen(path, RTLD_LAZY);
#if log_LINKER
    if (path == NULL) {
        log_println("openDynamicLibrary(null) = %p", result);
    } else {
        log_println("openDynamicLibrary(\"%s\") = %p", path, result);
    }
#endif
    return result;
}

static void* loadSymbol(void* handle, const char* symbol) {
#if log_LINKER
    log_println("loadSymbol(%p, \"%s\")", handle, symbol);
#endif
    void* result = dlsym(handle, symbol);
#if log_LINKER
    Dl_info info;
    void* address = result;
    if (dladdr(address, &info) != 0) {
        log_println("loadSymbol(%p, \"%s\") = %p from %s", handle, symbol, result, info.dli_fname);
    } else {
        log_println("loadSymbol(%p, \"%s\") = %p", handle, symbol, result);
    }
#endif
    return result;
}

/**
 *  ATTENTION: this signature must match the signatures of 'com.sun.max.vm.MaxineVM.run()':
 */
typedef jint (*VMRunMethod)(
                Address primordialVmThreadLocals,
                Address bootHeapRegionStart,
                Address auxiliarySpace,
                void *openDynamicLibrary(char *),
                void *dlsym(void *, const char *),
                int argc,
                char *argv[]);

int maxine(int argc, char *argv[], char *executablePath) {
    VMRunMethod method;
    int exitCode = 0;
    int fd;

#if os_DARWIN
    _executablePath = executablePath;
    if (getenv("DYLD_FORCE_FLAT_NAMESPACE") == NULL) {
        /* Without this, libjava.jnilib library will use link against the JVM_* functions
         * in lib[client|server].dylib instead of those in Maxine's libjvm.dylib. */
        log_exit(11, "The environment variable DYLD_FORCE_FLAT_NAMESPACE must be defined.");
    }
#endif

#if log_LOADER
#if !os_GUESTVMXEN
    char *ldpath = getenv("LD_LIBRARY_PATH");
    if (ldpath == NULL) {
        log_println("LD_LIBRARY_PATH not set");
    } else {
        log_println("LD_LIBRARY_PATH=%s", ldpath);
    }
#endif
    log_println("Arguments: argc %d, argv %lx", argc, argv);
    int i;
    for (i = 0; i < argc; i++) {
        log_println("arg[%d]: %lx, \"%s\"", i, argv[i], argv[i]);
    }
#endif

    fd = loadImage();

    messenger_initialize();

    threads_initialize();

    method = (VMRunMethod) (image_heap() + (Address) image_header()->vmRunMethodOffset);

    // Allocate the primordial VM thread locals:
    Address primordialVmThreadLocals = (Address) alloca(image_header()->vmThreadLocalsSize + sizeof(Address));

    // Align primordial VM thread locals to Word boundary:
    primordialVmThreadLocals = wordAlign(primordialVmThreadLocals);

    // Initialize all primordial VM thread locals to 0/null:
    memset((char *) primordialVmThreadLocals, 0, image_header()->vmThreadLocalsSize);

#if log_LOADER
    log_println("primordial VM thread locals allocated at: %p", primordialVmThreadLocals);
#endif

    Address auxiliarySpace = 0;
    Size auxiliarySpaceSize = image_header()->auxiliarySpaceSize + REFERENCE_BUFFER_SIZE;
    if (auxiliarySpaceSize) {
        auxiliarySpace = (Address) malloc(image_header()->auxiliarySpaceSize + REFERENCE_BUFFER_SIZE);
        if (auxiliarySpace == 0) {
            log_exit(1, "Failed to allocate %lu bytes of auxiliary space", auxiliarySpaceSize);
        }
#if log_LOADER
        log_println("allocated %lu bytes of auxiliary space at 0x%p\n", image_header()->auxiliarySpaceSize, auxiliarySpace);
#endif
        memset((Word) auxiliarySpace, 1, image_header()->auxiliarySpaceSize + REFERENCE_BUFFER_SIZE);
    }

#if log_LOADER
    log_println("entering Java by calling MaxineVM::run(primordialVmThreadLocals=0x%p, bootHeapRegionStart=0x%p, auxiliarySpace=0x%p, openDynamicLibrary=0x%p, dlsym=0x%p, argc=%d, argv=0x%p)",
                    primordialVmThreadLocals, image_heap(), auxiliarySpace, openDynamicLibrary, loadSymbol, argc, argv);
#endif
    exitCode = (*method)(primordialVmThreadLocals, image_heap(), auxiliarySpace, openDynamicLibrary, loadSymbol, argc, argv);

#if log_LOADER
    log_println("start method exited with code: %d", exitCode);
#endif

    if (fd > 0) {
        int error = close(fd);
        if (error != 0) {
            log_println("WARNING: could not close image file");
        }
    }

#if log_LOADER
    log_println("exit code: %d", exitCode);
#endif

    return exitCode;
}

/*
 * Native support. These global natives can be called from Java to get some basic services
 * from the C language and environment.
 */
void *native_executablePath() {
    static char result[MAX_PATH_LENGTH];
    getExecutablePath(result);
    return result;
}

void native_exit(jint code) {
    exit(code);
}

void native_trap_exit(int code, Address address) {
    Dl_info info;
    if (dladdr((void *) address, &info) != 0) {
        if (info.dli_sname == NULL) {
            log_println("In %s (%p)", info.dli_fname, info.dli_fbase);
        } else {
            log_println("In %s (%p) at %s (%p%+d)",
                            info.dli_fname,
                            info.dli_fbase,
                            info.dli_sname,
                            info.dli_saddr,
                            address - (Address) info.dli_saddr);
        }
    }
    log_exit(code, "Trap in native code at %p\n", address);
}

#if os_DARWIN
void *native_environment() {
    void **environ = (void **)*_NSGetEnviron();
#if log_LOADER
    int i = 0;
    for (i = 0; environ[i] != NULL; i++)
    log_println("native_environment[%d]: %s", i, environ[i]);
#endif
    return (void *)environ;
}
#else
extern char ** environ;
void *native_environment() {
    return environ;
}
#endif
