/***
 * ASM tests
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.objectweb.asm;

import junit.framework.TestSuite;

/**
 * Annotations tests.
 *
 * @author Eric Bruneton
 */
public class AnnotationsTest extends AbstractTest {

    public static TestSuite suite() throws Exception {
        return new AnnotationsTest().getSuite();
    }

    @Override
    public void test() throws Exception {
        ClassReader cr = new ClassReader(is);
        ClassWriter cw1 = new ClassWriter(0);
        ClassWriter cw2 = new ClassWriter(0);
        cr.accept(new RemoveAnnotationsAdapter1(cw1), 0);
        cr.accept(new RemoveAnnotationsAdapter2(cw2), 0);
        assertEquals(new ClassReader(cw2.toByteArray()),
                new ClassReader(cw1.toByteArray()));
    }

    static class EmptyAnnotationVisitor extends AnnotationVisitor {

        public EmptyAnnotationVisitor() {
            super(Opcodes.ASM4);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String desc) {
            return this;
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            return this;
        }
    }

    static class RemoveAnnotationsAdapter1 extends ClassVisitor {

        public RemoveAnnotationsAdapter1(final ClassVisitor cv) {
            super(Opcodes.ASM4, cv);
        }

        @Override
        public AnnotationVisitor visitAnnotation(
            final String desc,
            final boolean visible)
        {
            return new EmptyAnnotationVisitor();
        }

        @Override
        public MethodVisitor visitMethod(
            final int access,
            final String name,
            final String desc,
            final String signature,
            final String[] exceptions)
        {
            return new MethodVisitor(Opcodes.ASM4, cv.visitMethod(access,
                    name,
                    desc,
                    signature,
                    exceptions))
            {

                @Override
                public AnnotationVisitor visitAnnotationDefault() {
                    return new EmptyAnnotationVisitor();
                }

                @Override
                public AnnotationVisitor visitAnnotation(
                    String desc,
                    boolean visible)
                {
                    return new EmptyAnnotationVisitor();
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(
                    int parameter,
                    String desc,
                    boolean visible)
                {
                    return new EmptyAnnotationVisitor();
                }
            };
        }
    }

    static class RemoveAnnotationsAdapter2 extends ClassVisitor {

        public RemoveAnnotationsAdapter2(final ClassVisitor cv) {
            super(Opcodes.ASM4, cv);
        }

        @Override
        public AnnotationVisitor visitAnnotation(
            final String desc,
            final boolean visible)
        {
            return null;
        }

        @Override
        public MethodVisitor visitMethod(
            final int access,
            final String name,
            final String desc,
            final String signature,
            final String[] exceptions)
        {
            return new MethodVisitor(Opcodes.ASM4, cv.visitMethod(access,
                    name,
                    desc,
                    signature,
                    exceptions))
            {

                @Override
                public AnnotationVisitor visitAnnotationDefault() {
                    return null;
                }

                @Override
                public AnnotationVisitor visitAnnotation(
                    String desc,
                    boolean visible)
                {
                    return null;
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(
                    int parameter,
                    String desc,
                    boolean visible)
                {
                    return null;
                }
            };
        }
    }
}
