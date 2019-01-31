package com.github.litttlefisher.swagger.docgen.mavenplugin.swagger2markup;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.logging.Log;

import io.github.swagger2markup.Swagger2MarkupConfig;
import io.github.swagger2markup.Swagger2MarkupConverter;
import io.github.swagger2markup.builder.Swagger2MarkupConfigBuilder;

/**
 * @author jinyn22648
 * @version $$Id: SwaggerToMarkupGenerator.java, v 0.1 2019/1/31 3:55 PM jinyn22648 Exp $$
 */
public class SwaggerToMarkupGenerator {

    private Log log;

    /**
     * swagger文件路径
     */
    private String swaggerInput;

    /**
     * 输出文件路径
     */
    private File outputDir;

    /**
     * 配置
     */
    private Map<String, String> config;

    public SwaggerToMarkupGenerator(Log log, String swaggerInput, File outputDir, Map<String, String> config) {
        this.log = log;
        this.swaggerInput = swaggerInput;
        this.outputDir = outputDir;
        this.config = config;
    }

    /**
     * 生成文件
     */
    public void generate() {
        Swagger2MarkupConfig swagger2MarkupConfig = new Swagger2MarkupConfigBuilder(config).build();
        Swagger2MarkupConverter converter = Swagger2MarkupConverter.from(new File(swaggerInput).toURI()).withConfig(
            swagger2MarkupConfig).build();
        swaggerToMarkup(converter);
    }

    /**
     * 找出所有的swagger文件
     *
     * @param directory 目录
     * @return 文件
     */
    private List<File> getSwaggerFiles(File directory, boolean recursive) {
        return new ArrayList<>(FileUtils.listFiles(directory, new String[] {"yaml", "yml", "json"}, recursive));
    }

    /**
     * 文件生成
     *
     * @param converter 转换器
     */
    private void swaggerToMarkup(Swagger2MarkupConverter converter) {
        if (outputDir != null) {
            File effectiveOutputDir = getEffectiveOutputDir(converter);
            if (log.isInfoEnabled()) {
                log.info("Converting input to multiple files in folder: '" + effectiveOutputDir + "'");
            }
            converter.toFolder(effectiveOutputDir.toPath());
        } else {
            throw new IllegalArgumentException("outputFile must be used");
        }
    }

    /**
     * 获取一个有效的输出路径
     *
     * @param converter
     * @return
     */
    private File getEffectiveOutputDir(Swagger2MarkupConverter converter) {
        String outputDirAddendum = getInputDirStructurePath(converter);
        if (multipleSwaggerFilesInSwaggerLocationFolder(converter)) {
            /*
             * If the folder the current Swagger file resides in contains at least one other Swagger file then the
             * output dir must have an extra subdir per file to avoid markdown files getting overwritten.
             */
            outputDirAddendum += File.separator + extractSwaggerFileNameWithoutExtension(converter);
        }
        return new File(outputDir, outputDirAddendum);
    }

    /**
     * 是否有多个文件需要在本地生成
     *
     * @param converter
     * @return
     */
    private boolean multipleSwaggerFilesInSwaggerLocationFolder(Swagger2MarkupConverter converter) {
        Collection<File> swaggerFiles = getSwaggerFiles(
            new File(converter.getContext().getSwaggerLocation()).getParentFile(), false);
        return swaggerFiles.size() > 1;
    }

    /**
     * 获取需要输出的文件名，不包含后缀
     *
     * @param converter
     * @return
     */
    private String extractSwaggerFileNameWithoutExtension(Swagger2MarkupConverter converter) {
        return FilenameUtils.removeExtension(new File(converter.getContext().getSwaggerLocation()).getName());
    }

    private String getInputDirStructurePath(Swagger2MarkupConverter converter) {
        /*
         * When the Swagger input is a local folder (e.g. /Users/foo/) you'll want to group the generated output in the
         * configured output directory. The most obvious approach is to replicate the folder structure from the input
         * folder to the output folder. Example:
         * - swaggerInput is set to /Users/foo
         * - there's a single Swagger file at /Users/foo/bar-service/v1/bar.yaml
         * - outputDir is set to /tmp/asciidoc
         * -> markdown files from bar.yaml are generated to /tmp/asciidoc/bar-service/v1
         */
        // /Users/foo/bar-service/v1/bar.yaml
        String swaggerFilePath = new File(converter.getContext().getSwaggerLocation()).getAbsolutePath();
        // /Users/foo/bar-service/v1
        String swaggerFileFolder = StringUtils.substringBeforeLast(swaggerFilePath, File.separator);
        // /bar-service/v1
        return StringUtils.remove(swaggerFileFolder, getSwaggerInputAbsolutePath());
    }

    /**
     * 获取swagger文件的绝对路径
     *
     * @return
     */
    private String getSwaggerInputAbsolutePath() {
        return new File(swaggerInput).getParentFile().getAbsolutePath();
    }
}
