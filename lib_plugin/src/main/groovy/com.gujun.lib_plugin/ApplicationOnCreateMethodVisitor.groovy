package com.gujun.lib_plugin

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

public class ApplicationOnCreateMethodVisitor extends MethodVisitor {
    private Collection<String> names
    private String mClassName

    public ApplicationOnCreateMethodVisitor(int api, MethodVisitor mv, Collection<String> names, String mClassName) {
        super(api, mv);
        this.names = names
        this.mClassName = mClassName
    }

    @Override
    public void visitCode() {
        names.each {
            name ->
                mv.visitTypeInsn(Opcodes.NEW, name);
                mv.visitInsn(Opcodes.DUP);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, name, "<init>", "()V", false);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitTypeInsn(Opcodes.CHECKCAST, "android/app/Application");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, "init", "(Landroid/app/Application;)V", false);
        }

        super.visitCode();
    }

    @Override
    public void visitInsn(int opcode) {
        super.visitInsn(opcode);
    }
}
