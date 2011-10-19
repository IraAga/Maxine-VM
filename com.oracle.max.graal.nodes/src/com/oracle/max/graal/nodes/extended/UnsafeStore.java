/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.nodes.extended;

import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;

/**
 * Store of a value at a location specified as an offset relative to an object.
 */
public class UnsafeStore extends StateSplit implements Lowerable {

    @Input private ValueNode object;
    @Input private ValueNode offset;
    @Input private ValueNode value;
    @Data private CiKind storeKind;

    public UnsafeStore(ValueNode object, ValueNode offset, ValueNode value, CiKind kind) {
        super(CiKind.Void);
        this.object = object;
        this.offset = offset;
        this.value = value;
        this.storeKind = kind;
    }

    public ValueNode object() {
        return object;
    }

    public ValueNode offset() {
        return offset;
    }

    public ValueNode value() {
        return value;
    }

    public CiKind storeKind() {
        return storeKind;
    }

    @Override
    public void lower(CiLoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }

    // specialized on value type until boxing/unboxing is sorted out in intrinsification
    @NodeIntrinsic
    public static void store(@NodeParameter Object object, @NodeParameter long offset, @NodeParameter Object value, CiKind kind) {
        throw new UnsupportedOperationException();
    }

    @NodeIntrinsic
    public static void store(@NodeParameter Object object, @NodeParameter long offset, @NodeParameter boolean value, CiKind kind) {
        throw new UnsupportedOperationException();
    }

    @NodeIntrinsic
    public static void store(@NodeParameter Object object, @NodeParameter long offset, @NodeParameter byte value, CiKind kind) {
        throw new UnsupportedOperationException();
    }

    @NodeIntrinsic
    public static void store(@NodeParameter Object object, @NodeParameter long offset, @NodeParameter char value, CiKind kind) {
        throw new UnsupportedOperationException();
    }

    @NodeIntrinsic
    public static void store(@NodeParameter Object object, @NodeParameter long offset, @NodeParameter double value, CiKind kind) {
        throw new UnsupportedOperationException();
    }

    @NodeIntrinsic
    public static void store(@NodeParameter Object object, @NodeParameter long offset, @NodeParameter float value, CiKind kind) {
        throw new UnsupportedOperationException();
    }

    @NodeIntrinsic
    public static void store(@NodeParameter Object object, @NodeParameter long offset, @NodeParameter int value, CiKind kind) {
        throw new UnsupportedOperationException();
    }

    @NodeIntrinsic
    public static void store(@NodeParameter Object object, @NodeParameter long offset, @NodeParameter long value, CiKind kind) {
        throw new UnsupportedOperationException();
    }

    @NodeIntrinsic
    public static void store(@NodeParameter Object object, @NodeParameter long offset, @NodeParameter short value, CiKind kind) {
        throw new UnsupportedOperationException();
    }
}