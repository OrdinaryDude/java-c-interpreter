/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.realitysink.cover.runtime;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.realitysink.cover.nodes.access.SLReadPropertyCacheNodeGen;
import com.realitysink.cover.nodes.access.SLWritePropertyCacheNodeGen;
import com.realitysink.cover.nodes.call.SLDispatchNodeGen;
import com.realitysink.cover.nodes.interop.SLForeignToSLTypeNodeGen;
import com.realitysink.cover.CoverLanguage;
import com.realitysink.cover.nodes.access.SLReadPropertyCacheNode;
import com.realitysink.cover.nodes.access.SLWritePropertyCacheNode;
import com.realitysink.cover.nodes.call.SLDispatchNode;
import com.realitysink.cover.nodes.interop.SLForeignToSLTypeNode;

/**
 * The class containing all message resolution implementations of an SL object.
 */
@MessageResolution(receiverType = SLObjectType.class, language = CoverLanguage.class)
public class SLObjectMessageResolution {
    /*
     * An SL object resolves the WRITE message and maps it to an object property write access.
     */
    @Resolve(message = "WRITE")
    public abstract static class SLForeignWriteNode extends Node {

        @Child private SLWritePropertyCacheNode write = SLWritePropertyCacheNodeGen.create();
        @Child private SLForeignToSLTypeNode nameToSLType = SLForeignToSLTypeNodeGen.create();
        @Child private SLForeignToSLTypeNode valueToSLType = SLForeignToSLTypeNodeGen.create();

        public Object access(VirtualFrame frame, DynamicObject receiver, Object name, Object value) {
            Object convertedName = nameToSLType.executeConvert(frame, name);
            Object convertedValue = valueToSLType.executeConvert(frame, value);
            write.executeWrite(frame, receiver, convertedName, convertedValue);
            return convertedValue;
        }
    }

    /*
     * An SL object resolves the READ message and maps it to an object property read access.
     */
    @Resolve(message = "READ")
    public abstract static class SLForeignReadNode extends Node {

        @Child private SLReadPropertyCacheNode read = SLReadPropertyCacheNodeGen.create();
        @Child private SLForeignToSLTypeNode nameToSLType = SLForeignToSLTypeNodeGen.create();

        public Object access(VirtualFrame frame, DynamicObject receiver, Object name) {
            Object convertedName = nameToSLType.executeConvert(frame, name);
            return read.executeRead(frame, receiver, convertedName);
        }
    }

    /*
     * An SL object resolves the INVOKE message and maps it to an object property read access
     * followed by an function invocation. The object property must be an SL function object, which
     * is executed eventually.
     */
    @Resolve(message = "INVOKE")
    public abstract static class SLForeignInvokeNode extends Node {

        @Child private SLDispatchNode dispatch = SLDispatchNodeGen.create();

        public Object access(VirtualFrame frame, DynamicObject receiver, String name, Object[] arguments) {
            Object property = receiver.get(name);
            if (property instanceof SLFunction) {
                SLFunction function = (SLFunction) property;
                Object[] arr = new Object[arguments.length];
                // Before the arguments can be used by the SLFunction, they need to be converted to
                // SL
                // values.
                for (int i = 0; i < arguments.length; i++) {
                    arr[i] = SLContext.fromForeignValue(arguments[i]);
                }
                Object result = dispatch.executeDispatch(frame, function, arr);
                return result;
            } else {
                throw UnknownIdentifierException.raise(name);
            }
        }
    }

}
