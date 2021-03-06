/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.max.jdwp.generate;

import java.io.*;

/**
 * @author JDK7: jdk/make/tools/src/build/tools/jdwpgen
 */
public abstract class AbstractTypeNode extends AbstractNamedNode implements TypeNode {

    abstract String docType();

    // public abstract void genJavaWrite(PrintWriter writer, int depth, String writeLabel);
    // public abstract void genJavaToString(PrintWriter writer, int depth, String writeLabel);

    abstract String javaRead();

    @Override
    public String javaType() {
        return docType(); // default
    }

    @Override
    public void genJavaRead(PrintWriter writer, int depth, String readLabel) {
        indent(writer, depth);
        writer.print(readLabel);
        writer.print(" = ");
        writer.print(javaRead());
        writer.println(";");
    }

    public void genJavaDeclaration(PrintWriter writer, int depth) {
        writer.println();
        indent(writer, depth);
        writer.print("public ");
        writer.print(javaType());
        writer.print(" " + fieldName());
        writer.println(";");
    }

    public String javaParam() {
        return javaType() + " " + name();
    }
}
