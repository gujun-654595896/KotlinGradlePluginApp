package com.gujun.lib_plugin

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

/**
 * gradle插件工具类
 */
public class BytecodeTransform extends Transform {

    private String baseApplicationPath

    BytecodeTransform(String baseApplicationPath) {
        this.baseApplicationPath = baseApplicationPath
    }

    @Override
    String getName() {
        return "BytecodeInsertUtil";
    }

    //设置输入类型，我们是针对class文件处理
    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    //设置输入范围，我们选择整个项目
    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    boolean isIncremental() {
        return false;
    }

    //重点就是该方法，我们需要将修改字节码的逻辑就从这里开始
    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        //处理class
        Collection<TransformInput> inputs = transformInvocation.getInputs();

        //删除旧的输出
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        if (null != outputProvider) {
            outputProvider.deleteAll();
        }

        Collection<String> names = getModuleApplicationClassName(transformInvocation)

        inputs.each {
            //对类型为“文件夹”的input进行遍历
            it.directoryInputs.each {
                handleDirectoryInput(it, outputProvider, names)
            }

            //对类型为jar文件的input进行遍历
            it.jarInputs.each {
                handleJarInput(it, outputProvider, names)
            }
        }

    }

    /**
     * 处理目录下的class文件
     * @param directoryInput
     * @param outputProvider
     */
    void handleDirectoryInput(DirectoryInput directoryInput, TransformOutputProvider outputProvider, Collection<String> names) {
        //是否为目录
        if (directoryInput.file.isDirectory()) {
            //列出目录所有文件（包含子文件夹，子文件夹内文件）
            directoryInput.file.eachFileRecurse {
                file ->
                    def name = file.name
                    if (isApplicationFile(name)) {
                        ClassReader classReader = new ClassReader(file.bytes)
                        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
                        ClassVisitor classVisitor = new ApplicationClassVisitor(baseApplicationPath, classWriter, names)
                        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
                        byte[] bytes = classWriter.toByteArray()
                        FileOutputStream fileOutputStream = new FileOutputStream(file.parentFile.absolutePath + File.separator + name)
                        fileOutputStream.write(bytes)
                        fileOutputStream.close()
                    }
            }
            def dest = outputProvider.getContentLocation(directoryInput.name, directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)
            FileUtils.copyDirectory(directoryInput.file, dest)
        }

    }


    /**
     * 处理Jar中的class文件
     * @param directoryInput
     * @param outputProvider
     */
    void handleJarInput(JarInput jarInput, TransformOutputProvider outputProvider, Collection<String> names) {
        if (jarInput.file.getAbsolutePath().endsWith(".jar")) {
            //重名名输出文件,因为可能同名,会覆盖
            def jarName = jarInput.name
            def md5Name = DigestUtils.md5Hex(jarInput.file.absolutePath)
            if (jarName.endsWith(".jar")) {
                jarName = jarName.substring(0, jarName.length() - 4)
            }
            JarFile jarFile = new JarFile(jarInput.file)
            Enumeration enumeration = jarFile.entries()
            File tempFile = new File(jarInput.file.parent + File.separator + "temp.jar")
            //避免上次的缓存被重复插入
            if (tempFile.exists()) {
                tempFile.delete()
            }
            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(tempFile))
            //保存
            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = enumeration.nextElement()
                String entryName = jarEntry.name
                ZipEntry zipEntry = new ZipEntry(entryName)
                InputStream inputStream = jarFile.getInputStream(zipEntry)
                if (isApplicationFile(entryName)) {
                    jarOutputStream.putNextEntry(zipEntry)
                    ClassReader classReader = new ClassReader(IOUtils.toByteArray(inputStream))
                    ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
                    ClassVisitor classVisitor = new ApplicationClassVisitor(baseApplicationPath, classWriter, names)
                    classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
                    byte[] bytes = classWriter.toByteArray()
                    jarOutputStream.write(bytes)
                } else {
                    jarOutputStream.putNextEntry(zipEntry)
                    jarOutputStream.write(IOUtils.toByteArray(inputStream))
                }
                jarOutputStream.closeEntry()
            }
            jarOutputStream.close()
            jarFile.close()
            def dest = outputProvider.getContentLocation(jarName + "_" + md5Name, jarInput.contentTypes, jarInput.scopes, Format.JAR)
            FileUtils.copyFile(tempFile, dest)
            tempFile.delete()
        }
    }

    /**
     * 判断是否为需要处理class文件
     * @param name
     * @return
     */
    boolean isClassFile(String name) {
        return (name.endsWith(".class") && !name.startsWith("R\$")
                && "R.class" != name && "BuildConfig.class" != name && name.contains("Activity"))
    }

    /**
     * 判断是否为需要处理class文件
     * @param name
     * @return
     */
    boolean isApplicationFile(String name) {
        return (name.endsWith(".class") && !name.startsWith("R\$")
                && "R.class" != name && "BuildConfig.class" != name && name.contains("Application"))
    }

    Collection<String> getModuleApplicationClassName(TransformInvocation transformInvocation) {
        Collection<String> names = new ArrayList<>();
        transformInvocation.inputs.each {
            //对类型为“文件夹”的input进行遍历
            it.directoryInputs.each {
                Collection<String> directoryNames = handleDirectoryApplicationInput(it)
                if (directoryNames != null)
                    names.addAll(directoryNames)
            }

            //对类型为jar文件的input进行遍历
            it.jarInputs.each {
                Collection<String> jarNames = handleJarApplicationInput(it)
                if (jarNames != null)
                    names.addAll(jarNames)
            }
        }
        return names
    }

    /**
     * 处理目录下的class文件
     * @param directoryInput
     * @param outputProvider
     */
    Collection<String> handleDirectoryApplicationInput(DirectoryInput directoryInput) {
        Collection<String> names = new ArrayList<>()
        //是否为目录
        if (directoryInput.file.isDirectory()) {
            //列出目录所有文件（包含子文件夹，子文件夹内文件）
            directoryInput.file.eachFileRecurse {
                file ->
                    def name = file.name
                    if (isApplicationFile(name)) {
                        ClassReader classReader = new ClassReader(file.bytes)
                        if (classReader.getInterfaces().contains(applicationInterface())) {
                            names.add(classReader.getClassName())
                        }
                    }
            }
        }
        return names
    }


    /**
     * 处理Jar中的class文件
     * @param directoryInput
     * @param outputProvider
     */
    Collection<String> handleJarApplicationInput(JarInput jarInput) {
        Collection<String> names = new ArrayList<>()
        if (jarInput.file.getAbsolutePath().endsWith(".jar")) {
            JarFile jarFile = new JarFile(jarInput.file)
            Enumeration enumeration = jarFile.entries()
            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = enumeration.nextElement()
                String entryName = jarEntry.name
                ZipEntry zipEntry = new ZipEntry(entryName)
                InputStream inputStream = jarFile.getInputStream(zipEntry)
                if (isApplicationFile(entryName)) {
                    ClassReader classReader = new ClassReader(IOUtils.toByteArray(inputStream))
                    if (classReader.getInterfaces().contains(applicationInterface())) {
                        names.add(classReader.getClassName())
                    }
                }
            }
        }
        return names
    }

    String applicationInterface() {
        return "com/gujun/common/base/application/IApplication"
    }

}

