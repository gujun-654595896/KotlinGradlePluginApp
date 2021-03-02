package com.gujun.lib_plugin

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

public class ApplicationClassVisitor extends ClassVisitor implements Opcodes {

    private Collection<String> names;

    private String mClassName;
    private String mSuperClassName;

    private String baseApplicationPath

    private static final String METHOD_NAME_ONCREATE = "onCreate";

    public ApplicationClassVisitor(String baseApplicationPath, ClassVisitor cv, Collection<String> names) {
        super(Opcodes.ASM5, cv);
        this.names = names
        this.baseApplicationPath = baseApplicationPath
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
                      String[] interfaces) {
        mClassName = name;
        mSuperClassName = superName;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                     String[] exceptions) {
        MethodVisitor methodVisitor = cv.visitMethod(access, name, desc, signature, exceptions);
        if (baseApplicationPath != null && baseApplicationPath.equals(mSuperClassName)) {
            if (METHOD_NAME_ONCREATE.equals(name)) {
                System.out.println("-------------------- ApplicationClassVisitor,visit method:" + name +
                        " --------------------");
                return new ApplicationOnCreateMethodVisitor(Opcodes.ASM5, methodVisitor, names, mClassName);
            }
        }
        return methodVisitor;
    }
}
