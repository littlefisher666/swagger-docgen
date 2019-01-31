package com.github.litttlefisher.swagger.docgen.reader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import org.apache.commons.lang3.StringUtils;

import com.github.litttlefisher.swagger.docgen.constant.SymbolConstant;
import com.github.litttlefisher.swagger.docgen.exception.GenerateException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.RootDoc;

import lombok.extern.slf4j.Slf4j;

/**
 * @author jinyn22648
 * @version $$Id: JavaDocReader.java, v 0.1 2019/1/21 4:26 PM jinyn22648 Exp $$
 */
@Slf4j
public class JavaDocReader {

    /** java文件后缀 */
    private static final String JAVA_FILE_SUFFIX = ".java";

    /** source包后缀 */
    public static final String SOURCE_JAR_SUFFIX = "sources.jar";

    /** javadoc命令中doclet参数 */
    private static final String JAVADOC_COMMAND_OPTION_DOCLET = "-doclet";

    private static Map<String, ClassDoc> classDocMap = Maps.newHashMap();

    /**
     * 第一层中key为jar包的file类，value为jar包下的java文件
     * 第二层中key为java文件的class类路径，value为该文件存储在系统中的路径
     */
    private static Map<File, Map<String, String>> jarFileMap = Maps.newHashMap();

    /**
     * 解析javadoc需要该方法，具体参考{@link com.sun.javadoc.Doclet}
     *
     * @param root 入参
     * @return 出参
     */
    public static boolean start(RootDoc root) {
        Map<String, ClassDoc> thisRootDoc = Arrays.stream(root.classes()).collect(
            Collectors.toMap(input -> input.asClassDoc().qualifiedTypeName(), input -> input));
        JavaDocReader.classDocMap.putAll(thisRootDoc);
        return true;
    }

    /**
     * 根据class类路径获取javadoc
     *
     * @param className class类拒绝
     * @return java文件的javadoc
     */
    public static ClassDoc getClassJavaDoc(String className) {
        ClassDoc classDoc = classDocMap.get(className);
        if (classDoc == null) {
            for (Map<String, String> javaFileMap : jarFileMap.values()) {
                if (StringUtils.isNotBlank(javaFileMap.get(className))) {
                    readJavaDoc(new ArrayList<>(javaFileMap.values()));
                    return getClassJavaDoc(className);
                }
            }
            return null;
        } else {
            return classDoc;
        }
    }

    private static void readJavaDoc(List<String> javaFilePath) {
        List<String> docArgs = Lists.newArrayList(JAVADOC_COMMAND_OPTION_DOCLET, JavaDocReader.class.getName());
        docArgs.addAll(javaFilePath);
        com.sun.tools.javadoc.Main.execute(docArgs.toArray(new String[0]));
    }

    /**
     * 解析jar包
     *
     * @param jarFiles jar包
     */
    public static void init(String targetPath, List<File> jarFiles) {
        readFilePath(targetPath, jarFiles);
    }

    /**
     * 解析jar包，解压到target目录下
     *
     * @param targetPath target目录路径
     * @param jarFiles jar文件
     */
    private static void readFilePath(String targetPath, List<File> jarFiles) {
        try {
            doReadFilePath(targetPath, jarFiles);
        } catch (IOException e) {
            throw new GenerateException("文件读取错误");
        }
    }

    /**
     * 解析jar包，解压到target目录下
     *
     * @param targetPath target目录路径
     * @param jarFiles jar文件
     * @throws IOException 异常
     */
    private static void doReadFilePath(String targetPath, List<File> jarFiles) throws IOException {
        for (File jarFile : jarFiles) {
            Map<String, String> javaFileMap = Maps.newHashMap();
            String targetFilePath = generateFilePath(targetPath, jarFile.getName());
            File targetFile = new File(targetFilePath);
            JarInputStream jarIn = new JarInputStream(new BufferedInputStream(new FileInputStream(jarFile)));
            byte[] bytes = new byte[1024];
            while (true) {
                ZipEntry entry = jarIn.getNextJarEntry();
                if (entry == null) {
                    break;
                }

                File desTemp = new File(targetFile.getAbsoluteFile() + File.separator + entry.getName());
                //jar条目是空目录
                if (entry.isDirectory()) {
                    if (!desTemp.exists()) {
                        desTemp.mkdirs();
                    }
                } else if (entry.getName().endsWith(JAVA_FILE_SUFFIX)) {
                    javaFileMap.put(entry.getName().substring(0, entry.getName().length() - JAVA_FILE_SUFFIX.length())
                            .replaceAll(File.separator, SymbolConstant.PERIOD),
                        targetFile.getAbsolutePath() + File.separator + entry.getName());
                    //jar条目是文件
                    BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(desTemp));
                    int len = jarIn.read(bytes, 0, bytes.length);
                    while (len != -1) {
                        out.write(bytes, 0, len);
                        len = jarIn.read(bytes, 0, bytes.length);
                    }

                    out.flush();
                    out.close();
                }
                jarIn.closeEntry();
            }
            jarFileMap.put(jarFile, javaFileMap);
        }
    }

    /**
     * 生成文件路径
     *
     * @param targetPath target目录地址
     * @param jarFilePath jar文件地址
     * @return jar解压后放在target目录下的位置
     */
    private static String generateFilePath(String targetPath, String jarFilePath) {
        int lastSeparatorIndex = jarFilePath.lastIndexOf(File.separator);
        String fileName;
        if (lastSeparatorIndex < 0) {
            fileName = jarFilePath.substring(0, jarFilePath.length() - SOURCE_JAR_SUFFIX.length() - 1);
        } else {
            fileName = jarFilePath.substring(lastSeparatorIndex, jarFilePath.length() - SOURCE_JAR_SUFFIX.length() - 1);
        }
        return targetPath + File.separator + "sources" + File.separator + fileName;
    }
}
