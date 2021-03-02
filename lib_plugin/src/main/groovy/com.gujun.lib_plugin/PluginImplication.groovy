package com.gujun.lib_plugin

import com.android.build.gradle.AppExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

public class PluginImplication implements Plugin<Project> {

    @Override
    void apply(Project project) {
        //每个在build.gradle文件中添加插件（apply plugin: 'com.gujun.plugintest'）的都会执行此方法

        //build apks时格式：[:app:assembleDebug, :business:home:assembleDebug, :business:mine:assembleDebug]
        //单个运行的格式：[:app:assembleDebug]
        //Studio打开项目的格式：[]
        //clean project的格式：[clean]
        //rebuild project的格式：[clean, :business:mine:assembleDebug, :business:home:assembleDebug,...]
        //assembleDebug的格式:[assembleDebug]
        List<String> taskNames = project.gradle.startParameter.taskNames
        String curBuildModule = project.project.path
        if (taskNames.size() > 0) {
            //如果任务不为空，并且第一个任务是当前正在编译的module
            String firstTask = taskNames.get(0)
            boolean isAssemble = firstTask.toUpperCase().contains("ASSEMBLE") || firstTask.contains("aR") || firstTask.toUpperCase().contains("RESGUARD")
            if (isAssemble) {
                //编译运行生成apk,检测是否是assembleDebug等命令
                if (firstTask.lastIndexOf(":") > 0) {
                    String firstTaskModule = firstTask.substring(0, firstTask.lastIndexOf(":"))
                    if (curBuildModule == firstTaskModule) {
                        //当前编译的module和任务的module是一样的，代表当前module是app
                        //设置app
                        setApp(project, curBuildModule)
                        //设置ApplicationId
                        //测试发现在代码中设置，运行时无法找到启动的Activity，导致无法直接打开页面，所以先暂时不动态设置
//                        setApplicationIdInfo(project)
                        //添加需要依赖的app类型的module,需要在gradle.properties中配置
                        addCompileComponents(project)
                    } else {
                        //不是运行的module,代表是library
                        if (":app" != curBuildModule) {
                            //设置成library
                            setLibrary(project)
                        }
                    }
                } else {
                    //assembleDebug等命令
                    if (":app" != curBuildModule) {
                        //设置成library
                        setLibrary(project)
                    } else {
                        //添加需要依赖的app类型的module,需要在gradle.properties中配置
                        addCompileComponents(project)
                    }
                }
            } else {
                //普通编译
                setNotAssemble(project, curBuildModule)
            }
        } else {
            //普通编译
            setNotAssemble(project, curBuildModule)
        }

        //字节码插桩技术
        //AppExtension对应的是在build.gradle文件中配置了  project.apply plugin: 'com.android.application'
        AppExtension appExtension = project.extensions.findByType(AppExtension.class)
        String baseApplicationPath = project.properties.get("baseApplicationPath")
        if (appExtension != null)
            appExtension.registerTransform(new BytecodeTransform(baseApplicationPath), Collections.EMPTY_LIST)
    }

    /**
     * 普通编译，不生成apk,此时是否是app根据当前module下的gradle.properties的isApp来确定
     * @param project
     * @param curBuildModule
     */
    void setNotAssemble(Project project, String curBuildModule) {
        //如果没有任务把除了app和在gradle.properties中设置isApp=true的所有module设置成library
        boolean isApp = Boolean.parseBoolean((project.properties.get("isApp")))
        if (":app" == curBuildModule) {
            isApp = true
        }
        if (isApp) {
            //设置app
            setApp(project, curBuildModule)
        } else {
            //设置成library
            setLibrary(project)
        }
    }

    /**
     * 将当前project设置成app
     * @param project
     */
    void setApp(Project project, String curBuildModule) {
        if (":app" != curBuildModule) {
            project.apply plugin: 'com.android.application'
            //设置资源相关
            project.android.sourceSets {
                main {
                    //设置独立运行所需资源的加载位置，需提前配置好,创建完对应的文件夹之后关闭Project从新打开，否则java和res文件夹不识别
                    manifest.srcFile 'src/main/runalone/AndroidManifest.xml'
                    java.srcDirs = ['src/main/java', 'src/main/runalone/java']
                    res.srcDirs = ['src/main/res', 'src/main/runalone/res']
                }
            }
        }
    }

    /**
     * 设置applicationId相关数据
     * @param project
     */
    void setApplicationIdInfo(Project project) {
        project.android {
            defaultConfig {
                applicationId project.getRootProject().ext.android.applicationId
            }
            buildTypes {
                debug {
                    //applicationId扩展
                    applicationIdSuffix project.getRootProject().ext.android.applicationIdSuffixDebug
                }
                release {
                    //applicationId扩展
                    applicationIdSuffix project.getRootProject().ext.android.applicationIdSuffixRelease
                }
            }
        }
    }

    /**
     * 将当前project设置成library
     * @param project
     */
    void setLibrary(Project project) {
        project.apply plugin: 'com.android.library'
    }

    /**
     * 添加需要依赖的app类型的module,需要在gradle.properties中配置,格式：compileComponents=business:mine,business:media
     * @param project
     */
    void addCompileComponents(Project project) {
        String compileComponents = project.properties.get("compileComponents")
        if (compileComponents == null || compileComponents.isEmpty()) return
        String[] compileComponentsArray = compileComponents.split(",")
        if (compileComponentsArray == null || compileComponentsArray.length == 0) {
            System.out.println("there is no compileComponents")
            return
        }
        for (String str : compileComponentsArray) {
            project.dependencies.add("implementation", project.project(':' + str))
        }
    }

}