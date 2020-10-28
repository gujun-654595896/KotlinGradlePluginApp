package com.gujun.lib_plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

public class PluginImplication implements Plugin<Project> {
    void apply(Project project) {
        project.task('testTask')  {
            println "Hello gradle plugin"
        }
    }
}