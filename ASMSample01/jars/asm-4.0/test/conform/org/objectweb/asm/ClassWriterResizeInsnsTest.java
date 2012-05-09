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

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.HashSet;

import junit.framework.TestSuite;

import org.objectweb.asm.attrs.CodeComment;

public class ClassWriterResizeInsnsTest extends AbstractTest {

    public static void premain(
        final String agentArgs,
        final Instrumentation inst)
    {
        inst.addTransformer(new ClassFileTransformer() {
            public byte[] transform(
                final ClassLoader loader,
                final String className,
                final Class<?> classBeingRedefined,
                final ProtectionDomain domain,
                byte[] b) throws IllegalClassFormatException
            {
                String n = className.replace('/', '.');
                if (agentArgs.length() == 0 || n.indexOf(agentArgs) != -1) {
                    try {
                        b = transformClass(b, ClassWriter.COMPUTE_FRAMES);
                        if (n.equals("pkg.FrameMap")) {
                            transformClass(b, 0);
                        }
                        return b;
                    } catch (Throwable e) {
                        return transformClass(b, 0);
                    }
                } else {
                    return null;
                }
            }
        });
    }

    static byte[] transformClass(final byte[] clazz, final int flags) {
        ClassReader cr = new ClassReader(clazz);
        ClassWriter cw = new ComputeClassWriter(flags);
        ClassVisitor ca = new ClassVisitor(Opcodes.ASM4, cw) {

            boolean transformed = false;

            @Override
            public void visit(
                int version,
                int access,
                String name,
                String signature,
                String superName,
                String[] interfaces)
            {
                if (flags == ClassWriter.COMPUTE_FRAMES) {
                    version = (version & 0xFFFF) < Opcodes.V1_6
                            ? Opcodes.V1_6
                            : version;
                }
                super.visit(version,
                        access,
                        name,
                        signature,
                        superName,
                        interfaces);
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
                    private final HashSet<Label> labels = new HashSet<Label>();

                    @Override
                    public void visitLabel(final Label label) {
                        super.visitLabel(label);
                        labels.add(label);
                    }

                    @Override
                    public void visitJumpInsn(
                        final int opcode,
                        final Label label)
                    {
                        super.visitJumpInsn(opcode, label);
                        if (opcode != Opcodes.GOTO) {
                            if (!transformed && !labels.contains(label)) {
                                transformed = true;
                                for (int i = 0; i < 33000; ++i) {
                                    mv.visitInsn(Opcodes.NOP);
                                }
                            }
                        }
                    }
                };
            }
        };
        cr.accept(ca, new Attribute[] { new CodeComment() }, 0);
        return cw.toByteArray();
    }

    public static TestSuite suite() throws Exception {
        return new ClassWriterResizeInsnsTest().getSuite();
    }

    @Override
    public void test() throws Exception {
        try {
            Class.forName(n, true, getClass().getClassLoader());
        } catch (NoClassDefFoundError ncdfe) {
            // ignored
        } catch (UnsatisfiedLinkError ule) {
            // ignored
        } catch (ClassFormatError cfe) {
            fail(cfe.getMessage());
        } catch (VerifyError ve) {
            fail(ve.toString());
        }
    }
}
