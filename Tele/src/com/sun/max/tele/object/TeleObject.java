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
package com.sun.max.tele.object;

import java.lang.reflect.*;
import java.util.*;

import com.sun.max.jdwp.vm.proxy.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.reference.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 * Canonical surrogate for a heap object in the {@link TeleVM}.
 *
 * This class and its subclasses play the role of typed wrappers for References to heap objects in the {@link TeleVM},
 * encapsulating implementation details for working with those objects remotely.
 *
 * @author Michael Van De Vanter
 */
public abstract class TeleObject extends AbstractTeleVMHolder implements ObjectProvider {

    /**
     * Controls tracing for object copying.
     */
    protected static final int COPY_TRACE_VALUE = 4;

    /**
     * The factory method {@link TeleObjectFactory#make(Reference)} ensures synchronized TeleObjects creation.
     */
    protected TeleObject(TeleVM teleVM, Reference reference) {
        super(teleVM);
        _reference = (TeleReference) reference;
        _oid = _reference.makeOID();
    }

    protected void refresh(long processEpoch) {
    }

    private final TeleReference _reference;

    /**
     * @return canonical reference to this object in the {@link teleVM}
     */
    public TeleReference reference() {
        return _reference;
    }

    /**
     * @return current absolute location of the beginning of the object's memory allocation in the {@link TeleVM},
     * subject to relocation by GC.
     */
    public Pointer getCurrentCell() {
        return teleVM().referenceToCell(_reference);
    }

    /**
     * @return the current size of memory allocated for the object in the {@link TeleVM}.
     */
    public Size getCurrentSize() {
        return classActorForType().dynamicTupleSize();
    }

    /**
     * @return current absolute location of the object's origin (not necessarily beginning of memory)
     *  in the {@link TeleVM}, subject to relocation by GC
     */
    public Pointer getCurrentOrigin() {
        return _reference.toOrigin();
    }

    private final long _oid;

    /**
     * @return a number that uniquely identifies this object in the {@link TeleVM} for the duration of the inspection
     */
    public long getOID() {
        return _oid;
    }

    @Override
    public String toString() {
        return getClass().toString() + "<" + _oid + ">";
    }

    private TeleHub _teleHub = null;

    /**
     * @return a short string describing the role played by this object if it is of special interest in the Maxine
     *         implementation, null if any other kind of object.
     */
    public String maxineRole() {
        return null;
    }

    /**
     * @return an extremely short, abbreviated version of the string {@link #maxineRole()}, describing the role played
     *         by this object in just a few characters.
     */
    public String maxineTerseRole() {
        return maxineRole();
    }

    /**
     * @return the local surrogate for the Hub of this object
     */
    public TeleHub getTeleHub() {
        if (_teleHub == null) {
            final Reference hubReference = teleVM().wordToReference(teleVM().layoutScheme().generalLayout().readHubReferenceAsWord(_reference));
            _teleHub = (TeleHub) teleVM().makeTeleObject(hubReference);
        }
        return _teleHub;
    }

    /**
     * @return the "misc" word from the header of this object in the teleVM
     */
    public Word getMiscWord() {
        return teleVM().layoutScheme().generalLayout().readMisc(_reference);
    }

    /**
     * @return local {@link ClassActor}, equivalent to the one in the teleVM that describes the type
     * of this object in the {@link TeleVM}.
     * Note that in the singular instance of {@link StaticTuple} this does not correspond to the actual type of the
     * object, which is an exceptional Maxine object that has no ordinary Java type; it returns in this case
     * the type of the class that the tuple helps implement.
     */
    public ClassActor classActorForType() {
        return getTeleHub().getTeleClassActor().classActor();
    }

    /**
     * return local surrogate for the{@link ClassMethodActor} associated with this object in the {@link TeleVM}, either
     * because it is a {@link ClassMethodActor} or because it is a class closely associated with a method that refers to
     * a {@link ClassMethodActor}. Null otherwise.
     */
    public TeleClassMethodActor getTeleClassMethodActorForObject() {
        return null;
    }

    /**
     *  Gets the fields for either a tuple or hybrid object, returns empty set for arrays.
     *  Returns static fields in the special case of a {@link StaticTuple} object.
     */
    public Set<FieldActor> getFieldActors() {
        final Set<FieldActor> instanceFieldActors = new HashSet<FieldActor>();
        collectInstanceFieldActors(classActorForType(), instanceFieldActors);
        return instanceFieldActors;
    }

    private void collectInstanceFieldActors(ClassActor classActor, Set<FieldActor> instanceFieldActors) {
        if (classActor != null) {
            for (FieldActor fieldActor : classActor.localInstanceFieldActors()) {
                instanceFieldActors.add(fieldActor);
            }
            collectInstanceFieldActors(classActor.superClassActor(), instanceFieldActors);
        }
    }

    /**
     * @param fieldActor local {@link FieldActor}, part of the {@link ClassActor} for the type of this object, that
     *            describes a field in this object in the {@link TeleVM}
     * @return contents of the designated field in this object in the {@link TeleVM}
     */
    public abstract Value readFieldValue(FieldActor fieldActor);

    /**
     * @return a shallow copy of the object in the teleVM, with any references in it nulled out
     */
    public abstract Object shallowCopy();

    /**
     * Filter for pruning the object graph copied during a {@linkplain TeleObject#deepCopy}.
     */
    protected static interface FieldIncludeChecker {

        /**
         * Determines if a given field is to be traversed and copied during a deep copy.
         *
         * @param level  the depth of the sub-graph currently being copied
         * @param fieldActor  the field to be queried
         */
        boolean include(int level, FieldActor fieldActor);
    }

    protected static final class DeepCopyContext {

        private int _level = 0;
        private final FieldIncludeChecker _fieldIncludeChecker;
        private final Map<TeleObject, Object> _teleObjectToObject = new HashMap<TeleObject, Object>();

        private static final FieldIncludeChecker _defaultIFieldIncludeChecker = new FieldIncludeChecker() {
            public boolean include(int level, FieldActor fieldActor) {
                return true;
            }
        };

        /**
         * Creates a context for a deep copy.
         */
        protected DeepCopyContext() {
            _fieldIncludeChecker = _defaultIFieldIncludeChecker;
        }

        /**
         * Creates a context for a deep copy in which a filter suppresses copying of specified fields.
         */
        protected DeepCopyContext(FieldIncludeChecker fieldIncludeChecker) {
            _fieldIncludeChecker = fieldIncludeChecker;
        }

        /**
         * @return the depth of the object graph currently being copied
         */
        protected int level() {
            return _level;
        }

        /**
         * Registers a newly copied object in the context to avoid duplication.
         */
        protected void register(TeleObject teleObject, Object newObject) {
            _teleObjectToObject.put(teleObject, newObject);
        }

        /**
         * @return whether the specified object field at this level of the object graph should be copied.
         */
        protected boolean include(int level, FieldActor fieldActor) {
            return _fieldIncludeChecker.include(level, fieldActor);
        }

    }

    /**
     * @return produces a deep copy of an object as part of
     * a larger deep copy in which this particular object may have
     * already been copied.
     */
    protected final Object makeDeepCopy(DeepCopyContext context) {
        Object newObject = context._teleObjectToObject.get(this);
        if (newObject == null) {
            context._level++;
            newObject = createDeepCopy(context);
            context.register(this, newObject);
            context._level--;
        }
        return newObject;
    }

    /**
     * @return creates a local deep copy of the object, using Maxine-specific shortcuts when
     * possible to produce a local equivalent without copying.
     * Implementations that copy recursively must call {@link TeleObject#makeDeepCopy(DeepCopyContext)},
     * and must register newly allocated objects before doing so.  This will result in redundant registrations
     * in those cases.
     */
    protected abstract Object createDeepCopy(DeepCopyContext context);

    /**
     * @return a best effort deep copy - with certain substitutions
     */
    public final Object deepCopy() {
        Trace.begin(COPY_TRACE_VALUE, "Deep copying from VM: " + this);
        final Object objectCopy = makeDeepCopy(new DeepCopyContext());
        Trace.end(COPY_TRACE_VALUE, "Deep copying from VM: " + this);
        return objectCopy;
    }

    /**
     * @return a best effort deep copy - with certain substitutions, and with
     * certain specified field omissions.
     */
    public final Object deepCopy(FieldIncludeChecker fieldIncludeChecker) {
        Trace.begin(COPY_TRACE_VALUE, "Deep copying from VM: " + this);
        final Object objectCopy = makeDeepCopy(new DeepCopyContext(fieldIncludeChecker));
        Trace.end(COPY_TRACE_VALUE, "Deep copying from VM: " + this);
        return objectCopy;
    }

    /**
     * Updates the field of an object or class from the {@link TeleVM}.
     *
     * @param teleObject surrogate for a tuple in the {@link TeleVM}. This will be a static tuple if the field is static.
     * @param tuple the local object to be updated in the host VM. This value is ignored if the field is static.
     * @param fieldActor the field to be copied/updated
     */
    protected static final void copyField(DeepCopyContext context, final TeleObject teleObject, final Object newTuple, final FieldActor fieldActor) throws TeleError {
        if (context.include(context.level(), fieldActor)) {
            if (!fieldActor.isInjected()) {
                final Field field = fieldActor.toJava();
                field.setAccessible(true);
                try {
                    final Value value = teleObject.readFieldValue(fieldActor);
                    final Object newJavaValue;
                    if (fieldActor.kind() == Kind.REFERENCE) {
                        final TeleObject teleFieldReferenceObject = teleObject.teleVM().makeTeleObject(value.asReference());
                        if (teleFieldReferenceObject == null) {
                            newJavaValue = null;
                        } else {
                            newJavaValue = teleFieldReferenceObject.makeDeepCopy(context);
                        }
                    } else if (fieldActor.kind() == Kind.WORD) {
                        final Class<Class< ? extends Word>> type = null;
                        final Class< ? extends Word> wordType = StaticLoophole.cast(type, fieldActor.toJava().getType());
                        newJavaValue = value.asWord().as(wordType);
                    } else {
                        newJavaValue = value.asBoxedJavaValue();
                    }
                    field.set(newTuple, newJavaValue);
                } catch (IllegalAccessException illegalAccessException) {
                    throw new TeleError("could not access field: " + field, illegalAccessException);
                }
            }
        }
    }

    /**
     * Updates the static fields of a specified local class from the {@link TeleVM}.
     */
    public static void copyStaticFields(TeleVM teleVM, Class javaClass) {
        final ClassActor classActor = ClassActor.fromJava(javaClass);
        final TeleClassActor teleClassActor = teleVM.findTeleClassActorByClass(javaClass);
        final TeleStaticTuple teleStaticTuple = teleClassActor.getTeleStaticTuple();

        Trace.begin(COPY_TRACE_VALUE, "Copying static fields of " + javaClass + "from VM");
        try {
            for (FieldActor fieldActor : classActor.localStaticFieldActors()) {
                copyField(new DeepCopyContext(), teleStaticTuple, null, fieldActor);
            }
        } finally {
            Trace.end(COPY_TRACE_VALUE, "Copying static fields of " + javaClass + "from VM");
        }
    }

    public Reference getReference() {
        return this.reference();
    }

    public ReferenceTypeProvider getReferenceType() {
        return teleVM().findTeleClassActorByType(classActorForType().typeDescriptor());
    }
}
