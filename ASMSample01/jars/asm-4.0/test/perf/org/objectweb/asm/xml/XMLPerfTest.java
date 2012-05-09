/***
 * ASM XML Adapter
 * Copyright (c) 2004-2011, Eugene Kuleshov
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
package org.objectweb.asm.xml;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

/**
 * Performance test suite for ASM XML
 *
 * @author Eugene Kuleshov
 */
public class XMLPerfTest {

    private static final String[] ENGINES = {
        "jd.xml.xslt.trax.TransformerFactoryImpl",
        "net.sf.saxon.TransformerFactoryImpl",
        "org.apache.xalan.processor.TransformerFactoryImpl", };

    private static final String[] TEMPLATES = {
        "copy.xsl",
        "linenumbers.xsl",
        "profile.xsl", };

    public static void main(final String[] args) throws Exception {
        System.err.println("Comparing XSLT performance for ASM XSLT");
        System.err.println("This may take 20 to 30 minutes\n");

        File examplesDir = new File(args[0]);
        if (!examplesDir.isDirectory()) {
            System.err.println(args[0] + " must be directory");
            return;
        }

        for (int i = 0; i < ENGINES.length; i++) {
            System.err.println(ENGINES[i]);
            process(null, ENGINES[i]);
            for (int j = 0; j < TEMPLATES.length; j++) {
                process(new File(examplesDir, TEMPLATES[j]).getAbsolutePath(),
                        ENGINES[i]);
            }
            System.err.println();
        }

    }

    private static void process(final String name, final String engine)
            throws Exception
    {
        System.setProperty("javax.xml.transform.TransformerFactory", engine);
        processRep(name, Processor.BYTECODE);
        processRep(name, Processor.MULTI_XML);
        processRep(name, Processor.SINGLE_XML);
    }

    private static void processRep(final String name, final int outRep) {
        long l1 = System.currentTimeMillis();
        int n = 0;
        try {
            Class<?> c = XMLPerfTest.class;
            String u = c.getResource("/java/lang/String.class").toString();
            final InputStream is = new BufferedInputStream(new URL(u.substring(4,
                    u.indexOf('!'))).openStream());
            final OutputStream os = new IgnoringOutputStream();
            final StreamSource xslt = name == null
                    ? null
                    : new StreamSource(new FileInputStream(name));

            Processor p = new DotObserver(Processor.BYTECODE,
                    outRep,
                    is,
                    os,
                    xslt);
            n = p.process();

        } catch (Exception ex) {
            System.err.println();
            System.err.println(ex);

        }

        long l2 = System.currentTimeMillis();

        System.err.println();
        System.err.println("  " + outRep + " " + name + "  " + (l2 - l1)
                + "ms  " + 1000f * n / (l2 - l1));
    }

    private static final class DotObserver extends Processor {

        public DotObserver(
            final int inRepresenation,
            final int outRepresentation,
            final InputStream input,
            final OutputStream output,
            final Source xslt)
        {
            super(inRepresenation, outRepresentation, input, output, xslt);
        }
    }

    static final class IgnoringOutputStream extends OutputStream {

        @Override
        public final void write(final int b) throws IOException {
        }

        @Override
        public final void write(final byte[] b) throws IOException {
        }

        @Override
        public final void write(final byte[] b, final int off, final int len)
                throws IOException
        {
        }
    }
}
