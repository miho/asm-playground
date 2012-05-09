package eu.mihosoft.vrl.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class Sample01 implements Opcodes {

    private static final String PACKAGE_NAME = "eu/mihosoft/vrl/asm";
    private static final String INTERFACE_NAME = PACKAGE_NAME + "/ASMInterface";
    private static final String CLASS_NAME = PACKAGE_NAME + "/ASMClass";

    public static byte[] compile() {

        ClassWriter cw = new ClassWriter(
                ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES);

        // create i variable and initialize with 0
        cw.visitField(ACC_PUBLIC, "i", "I", null, 0);

        MethodVisitor mv;

        cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, CLASS_NAME, null,
                "java/lang/Object",
                new String[]{INTERFACE_NAME});

        // constructor
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        Label label0 = new Label();
        mv.visitLabel(label0);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>",
                "()V");

        // initialize i variable
        mv.visitVarInsn(ALOAD, 0);
        mv.visitLdcInsn(123);
        mv.visitFieldInsn(PUTFIELD, CLASS_NAME, "i", "I");

        // initialize this variable and return
        mv.visitInsn(RETURN);
        Label label1 = new Label();
        mv.visitLabel(label1);
        mv.visitLocalVariable("this", "L" + CLASS_NAME + ";", null, label0, label1, 0);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        // build the add() method
        mv = cw.visitMethod(ACC_PUBLIC, "add",
                "(II)I", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ILOAD, 1);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitIntInsn(IADD, 0);
        mv.visitInsn(IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // build the get() method
        mv = cw.visitMethod(ACC_PUBLIC, "get",
                "()I", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, CLASS_NAME, "i", "I");
        mv.visitInsn(IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();

        return cw.toByteArray();
    }

    /**
     *
     * Reads a class and print methods and fields.
     *
     * @param cls class to analyze
     */
    public static void readClass(byte[] cls) {

        final MethodVisitor mvs = new MethodVisitor(ASM4) {

            @Override
            public void visitLocalVariable(
                    String name, String desc,
                    String signature, Label start,
                    Label end, int index) {
                System.out.println(name + " " + desc);
                super.visitLocalVariable(name, desc, signature, start, end, index);
            }

            @Override
            public void visitVarInsn(int opcode, int var) {
                System.out.println("Opcode: " + opcode + " " + var);
                super.visitVarInsn(opcode, var);
            }
        };

        ClassVisitor cvs = new ClassVisitor(ASM4) {

            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String desc,
                    String signature,
                    Object value) {
                System.out.println(
                        "Field-Name: " + name
                        + ", Type: " + desc
                        + ", Value: " + value);
                return super.visitField(access, name, desc, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String desc,
                    String signature,
                    String[] exceptions) {

                System.out.println(
                        "Method-Name: " + name
                        + ", Desc: " + desc
                        + ", Signature: " + signature);

                return mvs;
            }

            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces) {

                System.out.println(">> Visit-Class:\n"
                        + "\n --> CLS-Version: " + version
                        + "\n --> Access: " + access
                        + "\n --> Name: " + name
                        + "\n --> Signature: " + signature
                        + "\n --> SuperName: " + superName
                        + "\n-----------------------------------------\n"
                        + "\n>> Class-Body:\n");

                super.visit(
                        version, access, name, signature, superName, interfaces);
            }
        };

        ClassReader cr = new ClassReader(cls);

        cr.accept(cvs, ClassReader.EXPAND_FRAMES);

        System.out.println("\n");

    }

    /**
     * The dynamic class loader that will load our handcrafted class.
     */
    public static class DynamicClassLoader extends ClassLoader {

        public DynamicClassLoader(ClassLoader parent) {
            super(parent);
        }

        public Class<?> define(String className, byte[] bytecode) {
            return super.defineClass(className, bytecode, 0, bytecode.length);
        }
    };

    public static void main(String[] args) throws Exception {

        System.out.println("\n--- (1) DEFINE CLASS (Byte Array) & Test ---\n");

        DynamicClassLoader loader = new DynamicClassLoader(
                Thread.currentThread().getContextClassLoader());

        System.out.println(">> Create the handcrafted class");

        Class<?> testClass = loader.define((CLASS_NAME).replace("/", "."), compile());
        ASMInterface asmClass = (ASMInterface) testClass.newInstance();
        
        System.out.println(">> Test the instance:\n");
        
        int result = asmClass.add(2, 8);

        System.out.println(" --> Result:    " + result);

        System.out.println(" --> Get-Field: " + asmClass.get());

        // reading classes

        System.out.println("\n--- (2) READING CLASS Byte Array ---\n");

        readClass(compile());
    }
}