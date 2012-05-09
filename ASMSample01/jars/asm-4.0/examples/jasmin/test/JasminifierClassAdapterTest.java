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

import jas.jasError;
import jasmin.ClassFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.TraceClassVisitor;

class ClassFilter extends ClassVisitor {

    public ClassFilter() {
        super(Opcodes.ASM4, null);
    }

    public void setNext(final ClassVisitor cv) {
        this.cv = cv;
    }
}

class Comment extends Attribute {

    public Comment() {
        super("Comment");
    }

    @Override
    public boolean isUnknown() {
        return false;
    }

    @Override
    protected Attribute read(
        final ClassReader cr,
        final int off,
        final int len,
        final char[] buf,
        final int codeOff,
        final Label[] labels)
    {

        return new Comment();
    }

    @Override
    protected ByteVector write(
        final ClassWriter cw,
        final byte[] code,
        final int len,
        final int maxStack,
        final int maxLocals)
    {
        return new ByteVector();
    }
}

class CodeComment extends Attribute {

    public CodeComment() {
        super("CodeComment");
    }

    @Override
    public boolean isUnknown() {
        return false;
    }

    @Override
    public boolean isCodeAttribute() {
        return true;
    }

    @Override
    protected Attribute read(
        final ClassReader cr,
        final int off,
        final int len,
        final char[] buf,
        final int codeOff,
        final Label[] labels)
    {
        return new CodeComment();
    }

    @Override
    protected ByteVector write(
        final ClassWriter cw,
        final byte[] code,
        final int len,
        final int maxStack,
        final int maxLocals)
    {
        return new ByteVector();
    }

    @Override
    protected Label[] getLabels() {
        super.getLabels();
        return new Label[] { new Label() };
    }
}

/**
 * JasminifierAdapterTest tests.
 *
 * @author Eric Bruneton
 */
public class JasminifierClassAdapterTest extends TestCase {

    protected String n;

    protected InputStream is;

    public static TestSuite suite() throws Exception {
        return new JasminifierClassAdapterTest().getSuite();
    }

    public JasminifierClassAdapterTest() {
        super("test");
    }

    protected void init(final String n, final InputStream is) {
        this.n = n;
        this.is = is;
    }

    protected TestSuite getSuite() throws Exception {
        TestSuite suite = new TestSuite(getClass().getName());
        String files = System.getProperty("asm.test") + ",";
        String clazz = System.getProperty("asm.test.class");
        String partcount = System.getProperty("parts");
        String partid = System.getProperty("part");
        int parts = partcount == null ? 1 : Integer.parseInt(partcount);
        int part = partid == null ? 0 : Integer.parseInt(partid);
        int id = 0;
        while (files.indexOf(',') != -1) {
            String file = files.substring(0, files.indexOf(','));
            files = files.substring(files.indexOf(',') + 1);
            File f = new File(file);
            if (f.isDirectory()) {
                scanDirectory("", f, suite, clazz);
            } else {
                ZipFile zip = new ZipFile(file);
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    String n = e.getName();
                    String p = n.replace('/', '.');
                System.out.println(n+" "+clazz);
                    if (n.endsWith(".class") && (clazz == null || p.indexOf(clazz) != -1)) {
                        n = p.substring(0, p.length() - 6);
                        if (id % parts == part) {
                            JasminifierClassAdapterTest t;
                            InputStream is = zip.getInputStream(e);
                            t = new JasminifierClassAdapterTest();
                            t.init(n, is);
                            suite.addTest(t);
                        }
                        ++id;
                    }
                }
            }
        }
        return suite;
    }

    private void scanDirectory(
        final String path,
        final File f,
        final TestSuite suite,
        final String clazz) throws Exception
    {
        File[] fs = f.listFiles();
        for (int i = 0; i < fs.length; ++i) {
            String n = fs[i].getName();
            String qn = path.length() == 0 ? n : path + "." + n;
            if (fs[i].isDirectory()) {
                scanDirectory(qn,
                        fs[i],
                        suite,
                        clazz);
            } else if (qn.endsWith(".class") && (clazz == null || qn.indexOf(clazz) != -1))
            {
                qn = qn.substring(0, qn.length() - 6);
                InputStream is = new FileInputStream(fs[i]);
                JasminifierClassAdapterTest t;
                t = new JasminifierClassAdapterTest();
                t.init(qn, is);
                suite.addTest(t);
            }
        }
    }

    public void assertEquals(final ClassReader cr1, final ClassReader cr2)
            throws Exception
    {
        assertEquals(cr1, cr2, null, null);
    }

    public void assertEquals(
        final ClassReader cr1,
        final ClassReader cr2,
        final ClassFilter filter1,
        final ClassFilter filter2) throws Exception
    {
        if (!Arrays.equals(cr1.b, cr2.b)) {
            StringWriter sw1 = new StringWriter();
            StringWriter sw2 = new StringWriter();
            ClassVisitor cv1 = new TraceClassVisitor(new PrintWriter(sw1));
            ClassVisitor cv2 = new TraceClassVisitor(new PrintWriter(sw2));
            if (filter1 != null) {
                filter1.setNext(cv1);
            }
            if (filter2 != null) {
                filter2.setNext(cv2);
            }
            cr1.accept(filter1 == null ? cv1 : filter1, 0);
            cr2.accept(filter2 == null ? cv2 : filter2, 0);
            String s1 = sw1.toString();
            String s2 = sw2.toString();
            assertEquals("different data", s1, s2);
        }
    }

    @Override
    public String getName() {
        return super.getName() + ": " + n;
    }

    public void test() throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ClassReader cr = new ClassReader(is);
        ClassWriter cw = new ClassWriter(0);
        ClassVisitor cv = new JasminifierClassAdapter(pw, cw);
        cr.accept(cv,
                new Attribute[] { new Comment(), new CodeComment() },
                ClassReader.EXPAND_FRAMES);
        pw.close();
        String jasmin = sw.toString();

        ClassFile cf = new ClassFile();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        cf.readJasmin(new StringReader(jasmin), "test", false);
        if (cf.errorCount() != 0) {
            throw new jasError();
        }
        cf.write(bos);
        bos.close();

        assertEquals(cr,
                new ClassReader(bos.toByteArray()),
                new ClassFilter() {

                    @Override
                    public void visit(
                        int version,
                        int access,
                        String name,
                        String signature,
                        String superName,
                        String[] interfaces)
                    {
                        access |= Opcodes.ACC_SUPER; // Jasmin bug workaround
                        super.visit(version,
                                access,
                                name,
                                signature,
                                superName,
                                interfaces);
                    }

                },
                null);
    }
}
