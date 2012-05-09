/***
 * ASM performance test: measures the performances of asm package
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.verifier.structurals.ModifiedPass3bVerifier;
import org.objectweb.asm.commons.LocalVariablesSorter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.SimpleVerifier;

import serp.bytecode.BCClass;
import serp.bytecode.BCMethod;
import serp.bytecode.Code;
import serp.bytecode.Project;

/**
 * @author Eric Bruneton
 * @author Peter Lawrey
 */
public abstract class ALLPerfTest {

    static boolean compute;

    static boolean computeFrames;

    static boolean skipDebug;

    static int repeats;

    static List<byte[]> classes = new ArrayList<byte[]>();

    static List<String> classNames = new ArrayList<String>();

    private static final int MAX_ITERATION_SEC = Integer.getInteger("max.iteration.sec",
            10)
            .intValue();

    public static void main(final String[] args)
            throws IOException, InterruptedException
    {
        String clazz = System.getProperty("asm.test.class");
        List<String> jars = findFiles(System.getProperty("java.home"), ".jar");
        jars.addAll(findJars(File.pathSeparatorChar,
                System.getProperty("java.class.path")));
        repeats = Integer.getInteger("repeats", 3).intValue() + 1;

        Set<String> classesFound = new HashSet<String>();
        for (int i = 0; i < jars.size(); i++) {
            ZipFile zip;
            try {
                zip = new ZipFile(jars.get(i));
            } catch (IOException e) {
                System.err.println("Error openning " + jars.get(i));
                e.printStackTrace();
                continue;
            }

            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String s = e.getName();
                if (s.endsWith(".class")) {
                    s = s.substring(0, s.length() - 6).replace('/', '.');
                    if (!classesFound.add(s)) {
                        continue;
                    }
                    if (clazz == null || s.indexOf(clazz) != -1) {
                        InputStream is = zip.getInputStream(e);
                        byte[] bytes = new ClassReader(is).b;
                        classes.add(bytes);
                        classNames.add(s);
                        is.close();
                        if (classes.size() % 2500 == 0) {
                            System.out.println("... searching, found "
                                    + classes.size() + " classes.");
                        }
                    }
                }
            }
            zip.close();
        }
        System.out.println("Found " + classes.size() + " classes.");

        RunTest nullBCELAdapt = new RunTest() {
            @Override
            public void test(byte[] bytes, int[] errors) throws IOException {
                nullBCELAdapt(bytes);
            }
        };

        RunTest nullAspectjBCELAdapt = new RunTest() {
            @Override
            public void test(byte[] bytes, int[] errors) throws IOException {
                nullAspectjBCELAdapt(bytes);
            }
        };

        RunTest nullJavassistAdapt = new RunTest() {
            ClassPool pool;
            @Override
            public void init() {
                pool = new ClassPool(null);
            }
            @Override
            public void test(byte[] bytes, int[] errors) throws Exception {
                nullJavassistAdapt(pool, bytes);
            }
        };

        RunTest nullSERPAdapt = new RunTest() {
            Project p;
            BCClass c;
            @Override
            public void init() {
                p = new Project();
                c = null;
            }
            @Override
            public void test(byte[] bytes, int[] errors) throws Exception {
                c = nullSERPAdapt(p, c, bytes);
            }
        };

        // get class info and deserialize tests

        runTestAll("get class info", "", new RunTest() {
            @Override
            public void test(byte[] bytes, int[] errors) {
                ClassReader cr = new ClassReader(bytes);
                cr.getAccess();
                cr.getClassName();
                cr.getSuperName();
                cr.getInterfaces();
            }
        });

        runTestAll("deserialize", "", new RunTest() {
            @Override
            public void test(byte[] bytes, int[] errors) {
                new ClassReader(bytes).accept(new EmptyVisitor(), 0);
            }
        });

        runTest("deserialize", "tree package", new RunTest() {
            @Override
            public void test(byte[] bytes, int[] errors) {
                new ClassReader(bytes).accept(new ClassNode(), 0);
            }
        });

        System.out.println();

        // deserialize and reserialize tests

        runTestAll("deserialize and reserialize", "", new RunTest() {
            @Override
            public void test(byte[] bytes, int[] errors) {
                ClassReader cr = new ClassReader(bytes);
                ClassWriter cw = new ClassWriter(0);
                cr.accept(cw, 0);
                cw.toByteArray();
            }
        });

        runTestAll("deserialize and reserialize",
                "copyPool",
                new RunTest() {
                    @Override
                    public void test(byte[] bytes, int[] errors) {
                        ClassReader cr = new ClassReader(bytes);
                        ClassWriter cw = new ClassWriter(cr, 0);
                        cr.accept(cw, 0);
                        cw.toByteArray();
                    }
                });

        runTest("deserialize and reserialize",
                "tree package",
                new RunTest() {
                    @Override
                    public void test(byte[] bytes, int[] errors) {
                        ClassWriter cw = new ClassWriter(0);
                        ClassNode cn = new ClassNode();
                        new ClassReader(bytes).accept(cn, 0);
                        cn.accept(cw);
                        cw.toByteArray();
                    }
                });

        compute = false;
        computeFrames = false;

        runTest("deserialize and reserialize", "BCEL", nullBCELAdapt);

        runTest("deserialize and reserialize",
                "Aspectj BCEL",
                nullAspectjBCELAdapt);

        runTest("deserialize and reserialize",
                "Javassist",
                nullJavassistAdapt);

        runTest("deserialize and reserialize",
                "SERP",
                nullSERPAdapt);

        System.out.println();

        // deserialize and reserialize tests with computeMaxs

        runTest("deserialize and reserialize",
                "computeMaxs",
                new RunTest() {
                    @Override
                    public void test(byte[] bytes, int[] errors) {
                        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                        new ClassReader(bytes).accept(cw, 0);
                        cw.toByteArray();
                    }
                });

        compute = true;
        computeFrames = false;

        runTest("deserialize and reserialize",
                "BCEL and computeMaxs",
                nullBCELAdapt);

        runTest("deserialize and reserialize",
                "Aspectj BCEL and computeMaxs",
                nullAspectjBCELAdapt);

        // misc. tests

        runTest("deserialize and reserialize",
                "LocalVariablesSorter",
                new RunTest() {
                    @Override
                    public void test(byte[] bytes, int[] errors) {
                        ClassWriter cw = new ClassWriter(0);
                        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM4, cw) {

                            @Override
                            public MethodVisitor visitMethod(
                                final int access,
                                final String name,
                                final String desc,
                                final String signature,
                                final String[] exceptions)
                            {
                                return new LocalVariablesSorter(access,
                                        desc,
                                        cv.visitMethod(access,
                                                name,
                                                desc,
                                                signature,
                                                exceptions));
                            }
                        }, ClassReader.EXPAND_FRAMES);
                        cw.toByteArray();
                    }
                });

        // This test repeatedly tests the same classes as SimpleVerifier
        // actually calls Class.forName() on the class which fills the PermGen
        runTestSome("analyze", "SimpleVerifier", new RunTest() {
            @Override
            public void test(byte[] bytes, int[] errors) {
                ClassReader cr = new ClassReader(bytes);
                ClassNode cn = new ClassNode();
                cr.accept(cn, ClassReader.SKIP_DEBUG);
                 List<MethodNode> methods = cn.methods;
                for (int k = 0; k < methods.size(); ++k) {
                    MethodNode method = methods.get(k);
                    Analyzer<?> a = new Analyzer<BasicValue>(new SimpleVerifier());
                    try {
                        a.analyze(cn.name, method);
                    } catch (Throwable th) {
                        // System.err.println(th);
                        ++errors[0];
                    }
                }
            }
        });

        System.out.println();

        // deserialize and reserialize tests with computeFrames

        runTest("deserialize and reserialize",
                "computeFrames",
                new RunTest() {
                    @Override
                    public void test(byte[] bytes, int[] errors) {
                        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                        new ClassReader(bytes).accept(cw, 0);
                        cw.toByteArray();
                    }
                });

        // the BCEL+computeFrames tests must be done only at the end, because
        // after them other tests run very slowly, for some unknown reason
        // (memory usage?)
        compute = false;
        computeFrames = true;
        runTest("deserialize and reserialize",
                "BCEL and computeFrames",
                nullBCELAdapt);

        runTest("deserialize and reserialize",
                "Aspectj BCEL and computeFrames",
                nullAspectjBCELAdapt);
    }

    public static List<String> findFiles(String directory, String suffix) {
        List<String> matches = new ArrayList<String>();
        findFiles(matches, new File(directory), suffix);
        return matches;
    }

    static void findFiles(List<String> matches, File directory, String suffix) {
        File[] files = directory.listFiles();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isDirectory()) {
                findFiles(matches, file, suffix);
            } else if (file.getName().endsWith(suffix)) {
                matches.add(file.getAbsolutePath());
            }
        }
    }

    static abstract class RunTest {

        public void init() {
        }

        public abstract void test(byte[] bytes, int[] errors) throws Exception;
    }

    static void runTestAll(
        String testName,
        String with,
        RunTest runTest) throws InterruptedException
    {
        runTest0(1, true, testName, with, runTest);
    }

    static void runTest(
        String testName,
        String with,
        RunTest runTest) throws InterruptedException
    {
        runTest0(repeats - 1, false, testName, with, runTest);
    }

    static void runTestSome(
        String testName,
        String with,
        RunTest runTest) throws InterruptedException
    {
        runTest0(repeats - 1, true, testName, with, runTest);
    }

    private static void runTest0(
        int testSkip,
        boolean startAtZero,
        String testName,
        String with,
        RunTest runTest) throws InterruptedException
    {
        if (with.length() > 0) {
            with = " with " + with;
        }
        boolean skipBigClasses = with.contains("BCEL and computeFrames");
        int totalCount = 0;
        long totalSize = 0;
        long totalTime = 0;
        System.out.println("\nStarting " + testName + with + " test.");
        for (int i = 0; i < repeats; ++i) {
            runTest.init();
            long t = System.currentTimeMillis();
            int count = 0;
            long size = 0;
            int[] errors = { 0 };
            long longest = 0;
            int longestSize = 0;
            int skipped = 0;
            for (int j = startAtZero ? 0 : i; j < classes.size(); j += testSkip)
            {
                count++;
                byte[] b = classes.get(j);
                if (skipBigClasses && b.length > 16 * 1024) {
                    skipped++;
                    continue;
                }
                size += b.length;
                try {
                    long start = System.currentTimeMillis();
                    runTest.test(b, errors);
                    long end = System.currentTimeMillis();
                    long time = end - start;
                    if (longest < time) {
                        longest = time;
                        longestSize = b.length;
                    }
                    if (time > MAX_ITERATION_SEC * 1000 / 10) {
                        System.out.println("--- time to " + testName
                                + " the class " + classNames.get(j) + with
                                + " took " + time + " ms. bytes=" + b.length);
                    }
                    if (end - t > MAX_ITERATION_SEC * 1000) {
                        // System.out.println("Stopping iteration due to a
                        // longer run time.");
                        break;
                    }
                } catch (Exception ignored) {
                    errors[0]++;
                } catch (Throwable e) {
                    System.err.println(classNames.get(j) + ": " + e);
                    errors[0]++;
                }
            }
            t = System.currentTimeMillis() - t;
            String errorStr = errors[0] > 0
                    ? " (" + errors[0] + " errors)"
                    : "";
            String skippedStr = skipped == 0
                    ? ""
                    : " ("
                            + skipped
                            + " skipped as BCEL/computeFrames on >16K classes is very slow)";
            String longestStr = "";
            if (longest > 50) {
                longestStr = " the longest took " + longest + " ms (" + longestSize + " bytes)";
            }
            if (i > 0) {
                System.out.println("- to " + testName + ' ' + count
                        + " classes" + with + " = " + t + " ms" + errorStr
                        + longestStr + skippedStr + '.');
                totalCount += count;
                totalSize += size;
                totalTime += t;
            }
        }
        System.out.println("Time to " + testName + ' ' + totalCount
                + " classes" + with + " = " + totalTime + " ms.\n"
                + "Processing rate = " + totalCount * 1000 / totalTime
                + " classes per sec (" + totalSize * 1000 / totalTime / 1024
                + " kB per sec).");
        System.gc();
        Thread.sleep(2500);
    }

    private static List<String> findJars(char pathSeparatorChar, String s) {
        List<String> ret = new ArrayList<String>();
        int start = 0;
        int pos = s.indexOf(pathSeparatorChar);
        while (pos >= 0) {
            String name = s.substring(start, pos);
            if (name.endsWith(".jar")) {
                ret.add(name);
            }
            start = pos + 1;
            pos = s.indexOf(pathSeparatorChar, start);
        }
        return ret;
    }

    static void nullBCELAdapt(final byte[] b) throws IOException {
        JavaClass jc = new ClassParser(new ByteArrayInputStream(b),
                "class-name").parse();
        ClassGen cg = new ClassGen(jc);
        ConstantPoolGen cp = cg.getConstantPool();
        Method[] ms = cg.getMethods();
        for (int k = 0; k < ms.length; ++k) {
            MethodGen mg = new MethodGen(ms[k], cg.getClassName(), cp);
            boolean lv = ms[k].getLocalVariableTable() == null;
            boolean ln = ms[k].getLineNumberTable() == null;
            if (lv) {
                mg.removeLocalVariables();
            }
            if (ln) {
                mg.removeLineNumbers();
            }
            mg.stripAttributes(skipDebug);
            InstructionList il = mg.getInstructionList();
            if (il != null) {
                InstructionHandle ih = il.getStart();
                while (ih != null) {
                    ih = ih.getNext();
                }
                if (compute) {
                    mg.setMaxStack();
                    mg.setMaxLocals();
                }
                if (computeFrames) {
                    ModifiedPass3bVerifier verif;
                    verif = new ModifiedPass3bVerifier(jc, k);
                    verif.do_verify();
                }
            }
            cg.replaceMethod(ms[k], mg.getMethod());
        }
        cg.getJavaClass().getBytes();
    }

    static void nullAspectjBCELAdapt(final byte[] b) throws IOException {
        org.aspectj.apache.bcel.classfile.JavaClass jc = new org.aspectj.apache.bcel.classfile.ClassParser(new ByteArrayInputStream(b),
                "class-name").parse();
        org.aspectj.apache.bcel.generic.ClassGen cg = new org.aspectj.apache.bcel.generic.ClassGen(jc);
        org.aspectj.apache.bcel.generic.ConstantPoolGen cp = cg.getConstantPool();
        org.aspectj.apache.bcel.classfile.Method[] ms = cg.getMethods();
        for (int k = 0; k < ms.length; ++k) {
            org.aspectj.apache.bcel.generic.MethodGen mg = new org.aspectj.apache.bcel.generic.MethodGen(ms[k],
                    cg.getClassName(),
                    cp);
            boolean lv = ms[k].getLocalVariableTable() == null;
            boolean ln = ms[k].getLineNumberTable() == null;
            if (lv) {
                mg.removeLocalVariables();
            }
            if (ln) {
                mg.removeLineNumbers();
            }
            mg.stripAttributes(skipDebug);
            org.aspectj.apache.bcel.generic.InstructionList il = mg.getInstructionList();
            if (il != null) {
                org.aspectj.apache.bcel.generic.InstructionHandle ih = il.getStart();
                while (ih != null) {
                    ih = ih.getNext();
                }
                if (compute) {
                    mg.setMaxStack();
                    mg.setMaxLocals();
                }
                if (computeFrames) {
                    org.aspectj.apache.bcel.verifier.structurals.ModifiedPass3bVerifier verif = new org.aspectj.apache.bcel.verifier.structurals.ModifiedPass3bVerifier(jc,
                            k);
                    verif.do_verify();
                }
            }
            cg.replaceMethod(ms[k], mg.getMethod());
        }
        cg.getJavaClass().getBytes();
    }

    static void nullJavassistAdapt(ClassPool pool, final byte[] b) throws Exception {
        CtClass cc = pool.makeClass(new ByteArrayInputStream(b));
        CtMethod[] ms = cc.getDeclaredMethods();
        for (int j = 0; j < ms.length; ++j) {
            if (skipDebug) {
                // is there a mean to remove the debug attributes?
            }
            if (compute) {
                // how to force recomputation of maxStack and maxLocals?
            }
        }
        cc.toBytecode();
    }

    static BCClass nullSERPAdapt(Project p, BCClass c, final byte[] b) throws Exception {
        if (c != null) {
            p.removeClass(c);
        }
        c = p.loadClass(new ByteArrayInputStream(b));
        c.getDeclaredFields();
        BCMethod[] methods = c.getDeclaredMethods();
        for (int i = 0; i < methods.length; ++i) {
            Code code = methods[i].getCode(false);
            if (code != null) {
                while (code.hasNext()) {
                    code.next();
                }
                if (compute) {
                    code.calculateMaxStack();
                    code.calculateMaxLocals();
                }
            }
        }
        c.toByteArray();
        return c;
    }

    static class EmptyVisitor extends ClassVisitor {

        AnnotationVisitor av = new AnnotationVisitor(Opcodes.ASM4) {

            @Override
            public AnnotationVisitor visitAnnotation(
                String name,
                String desc)
            {
                return this;
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                return this;
            }
        };

        public EmptyVisitor() {
            super(Opcodes.ASM4);
        }

        @Override
        public AnnotationVisitor visitAnnotation(
            String desc,
            boolean visible)
        {
            return av;
        }

        @Override
        public FieldVisitor visitField(
            int access,
            String name,
            String desc,
            String signature,
            Object value)
        {
            return new FieldVisitor(Opcodes.ASM4) {

                @Override
                public AnnotationVisitor visitAnnotation(
                    String desc,
                    boolean visible)
                {
                    return av;
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(
            int access,
            String name,
            String desc,
            String signature,
            String[] exceptions)
        {
            return new MethodVisitor(Opcodes.ASM4) {

                @Override
                public AnnotationVisitor visitAnnotationDefault() {
                    return av;
                }

                @Override
                public AnnotationVisitor visitAnnotation(
                    String desc,
                    boolean visible)
                {
                    return av;
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(
                    int parameter,
                    String desc,
                    boolean visible)
                {
                    return av;
                }
            };
        }
    }
}
