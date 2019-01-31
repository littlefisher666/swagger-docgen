package com.github.litttlefisher.swagger.docgen.document.properties;

import java.util.List;

import com.google.common.collect.Lists;

/**
 * @author jinyn22648
 * @version $$Id: MavenParameterInitialization.java, v 0.1 2019/1/26 8:01 PM jinyn22648 Exp $$
 */
public class MavenParameterInitialization {

    /** target目录 */
    private static String buildDirectory;

    /** 依赖的jar包 */
    private static List<String> compileClasspathElements = Lists.newArrayList();

    public static String getBuildDirectory() {
        return buildDirectory;
    }

    public static void setBuildDirectory(String buildDirectory) {
        MavenParameterInitialization.buildDirectory = buildDirectory;
    }

    public static List<String> getCompileClasspathElements() {
        return compileClasspathElements;
    }

    public static void setCompileClasspathElements(List<String> compileClasspathElements) {
        MavenParameterInitialization.compileClasspathElements = compileClasspathElements;
    }
}
