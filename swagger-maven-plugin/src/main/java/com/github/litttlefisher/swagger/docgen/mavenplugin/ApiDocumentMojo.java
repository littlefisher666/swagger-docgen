package com.github.litttlefisher.swagger.docgen.mavenplugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.litttlefisher.swagger.docgen.constant.MavenPropertyConstant;
import com.github.litttlefisher.swagger.docgen.constant.SymbolConstant;
import com.github.litttlefisher.swagger.docgen.document.AbstractDocumentSource;
import com.github.litttlefisher.swagger.docgen.document.ApiSource;
import com.github.litttlefisher.swagger.docgen.document.JaxrsDocumentSource;
import com.github.litttlefisher.swagger.docgen.document.SpringMvcApiDocumentSource;
import com.github.litttlefisher.swagger.docgen.document.properties.MavenParameterInitialization;
import com.github.litttlefisher.swagger.docgen.enums.DocumentSourceType;
import com.github.litttlefisher.swagger.docgen.exception.GenerateException;
import com.github.litttlefisher.swagger.docgen.mavenplugin.converter.ApiSourceConverter;
import com.github.litttlefisher.swagger.docgen.mavenplugin.properties.ApiSourceProperty;
import com.github.litttlefisher.swagger.docgen.mavenplugin.properties.SwaggerToMarkupProperty;
import com.github.litttlefisher.swagger.docgen.mavenplugin.swagger2markup.SwaggerToMarkupGenerator;

import io.swagger.util.Json;

/**
 * @author littlefisher
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.COMPILE,
    requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
    requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class ApiDocumentMojo extends AbstractMojo {

    /**
     * swagger生成所需的配置参数
     * 如果有多个，则根据不同的配置，生成多份配置
     */
    @Parameter
    private List<ApiSourceProperty> apiSources;

    /**
     * jackson工具中，要启用的feature，
     * 可选
     * {@link SerializationFeature}
     * {@link DeserializationFeature}
     * {@link JsonGenerator.Feature}
     * {@link JsonParser.Feature}
     * {@link MapperFeature}
     */
    @Parameter
    private List<String> enabledObjectMapperFeatures;

    /**
     * jackson工具中，要关闭的feature
     * 可选
     * {@link SerializationFeature}
     * {@link DeserializationFeature}
     * {@link JsonGenerator.Feature}
     * {@link JsonParser.Feature}
     * {@link MapperFeature}
     */
    @Parameter
    private List<String> disabledObjectMapperFeatures;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    /**
     * 编码格式
     */
    private String projectEncoding;

    @Component
    private MavenProjectHelper projectHelper;

    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
    private List<String> compileClasspathElements;

    /**
     * swagger生成是否要skip掉
     */
    @Parameter(property = "swagger.skip", defaultValue = "false")
    private boolean skipSwaggerGeneration;

    /**
     * 使用是否swagger2Makeup插件生成adoc文件
     */
    @Parameter
    private boolean swaggerToMarkupEnabled;

    /**
     * swagger2Makeup的配置参数
     */
    @Parameter
    private SwaggerToMarkupProperty swaggerToMarkup;

    /**
     * 生成文件时，文件的编码格式
     */
    @Parameter(property = "file.encoding")
    private String encoding;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private String buildDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        initMavenParameter();
        if (project != null) {
            projectEncoding = project.getProperties().getProperty(MavenPropertyConstant.PROJECT_BUILD_SOURCE_ENCODING);
        }
        generateSwagger();
        generateADoc();
    }

    /**
     * 生成swagger文件
     *
     * @throws MojoExecutionException 异常
     * @throws MojoFailureException 异常
     */
    private void generateSwagger() throws MojoExecutionException, MojoFailureException {

        if (skipSwaggerGeneration) {
            getLog().info("Swagger生成被过滤掉了.");
            return;
        }

        // 校验
        check();

        try {
            getLog().debug(apiSources.toString());

            if (enabledObjectMapperFeatures != null) {
                configureObjectMapperFeatures(enabledObjectMapperFeatures, true);

            }

            if (disabledObjectMapperFeatures != null) {
                configureObjectMapperFeatures(disabledObjectMapperFeatures, false);
            }

            for (ApiSource apiSource : apiSources.stream().map(ApiSourceConverter::convert).collect(
                Collectors.toList())) {
                validateConfiguration(apiSource);
                apiSource.initJavaDoc();

                AbstractDocumentSource documentSource = getDocumentSource(apiSource);

                documentSource.loadTypesToSkip();
                documentSource.loadModelModifier();
                documentSource.loadModelConverters();
                if (apiSource.isJavadocEnabled()) {
                    documentSource.loadModelJavaDocConverter();
                }
                documentSource.loadDocuments();

                createOutputDirs(apiSource.getOutputPath());

                if (apiSource.getTemplatePath() != null) {
                    documentSource.toDocuments();
                }
                String swaggerFileName = apiSource.getSwaggerFileName();
                documentSource.toSwaggerDocuments(apiSource.getOutputFormat(), swaggerFileName, projectEncoding);

                if (apiSource.isAttachSwaggerArtifact() && apiSource.getSwaggerDirectory() != null && project != null) {
                    String outputFormats = apiSource.getOutputFormat();
                    if (outputFormats != null) {
                        for (String format : outputFormats.split(SymbolConstant.COMMA)) {
                            String classifier = ApiSource.DEFAULT_SWAGGER_FILE_NAME.equals(swaggerFileName) ?
                                getSwaggerDirectoryName(apiSource.getSwaggerDirectory()) : swaggerFileName;
                            File swaggerFile = new File(apiSource.getSwaggerDirectory(),
                                swaggerFileName + SymbolConstant.PERIOD + format.toLowerCase());
                            projectHelper.attachArtifact(project, format.toLowerCase(), classifier, swaggerFile);
                        }
                    }
                }
            }
        } catch (GenerateException e) {
            throw new MojoFailureException(e.getMessage(), e);
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    /**
     * 通过swagger2markup工具生成adoc文件
     *
     * @throws MojoExecutionException 异常
     * @throws MojoFailureException 异常
     */
    private void generateADoc() throws MojoExecutionException, MojoFailureException {
        if (swaggerToMarkupEnabled) {
            try {
                if (swaggerToMarkup == null) {
                    throw new GenerateException("如果要使用swagger2markup 则`<swaggerToMarkup>`必须配置！");
                }
                for (ApiSourceProperty apiSource : apiSources) {
                    String swaggerFile = apiSource.getSwaggerDirectory() + File.separator + apiSource
                        .getSwaggerFileName() + SymbolConstant.PERIOD + apiSource.getOutputFormat();
                    if (getLog().isDebugEnabled()) {
                        getLog().debug("convertSwagger2markup goal started");
                        getLog().debug("outputDir: " + swaggerToMarkup.getOutputDir());
                        swaggerToMarkup.getConfig().forEach((key, value) -> getLog().debug(key + ": " + value));
                    }

                    new SwaggerToMarkupGenerator(getLog(), swaggerFile, swaggerToMarkup.getOutputDir(),
                        swaggerToMarkup.getConfig()).generate();
                }

            } catch (GenerateException e) {
                throw new MojoFailureException(e.getMessage(), e);
            } catch (Exception e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }

    /**
     * 初始化maven参数
     */
    private void initMavenParameter() {
        MavenParameterInitialization.setCompileClasspathElements(compileClasspathElements);
        MavenParameterInitialization.setBuildDirectory(buildDirectory);
    }

    /**
     * 获取解析器
     *
     * @param apiSource apiSource配置
     * @return {@link AbstractDocumentSource}
     * @throws MojoExecutionException 异常
     */
    private AbstractDocumentSource getDocumentSource(ApiSource apiSource) throws MojoExecutionException {
        AbstractDocumentSource documentSource;
        if (apiSource.getDocumentSourceType() == DocumentSourceType.SPRING_MVC) {
            documentSource = new SpringMvcApiDocumentSource(apiSource, projectEncoding);
        } else if (apiSource.getDocumentSourceType() == DocumentSourceType.JAXRS) {
            documentSource = new JaxrsDocumentSource(apiSource, projectEncoding);
        } else {
            throw new MojoExecutionException("apiSource.documentSourceType必须配置");
        }
        return documentSource;
    }

    /**
     * 校验
     *
     * @throws MojoFailureException 异常
     * @throws MojoExecutionException 异常
     */
    private void check() throws MojoFailureException, MojoExecutionException {
        if (CollectionUtils.isEmpty(apiSources)) {
            throw new MojoFailureException("你至少需要配置一个apiSource对象");
        }
        if (useSwaggerSpec11()) {
            throw new MojoExecutionException("你可能正在使用一个不支持swagger-maven-plugin 2.0+ 的老的swagger版本\n"
                + "swagger-maven-plugin 2.0+ 仅支持 swagger-core 1.3.x");
        }

        if (useSwaggerSpec13()) {
            throw new MojoExecutionException("你可能正在使用一个不支持swagger-maven-plugin 3.0+ 的老的swagger版本\n"
                + "swagger-maven-plugin 3.0+ 仅支持 swagger spec 2.0");
        }
    }

    /**
     * 创建输出文件的路径
     *
     * @param outputPath 要输出文件的路径
     * @throws MojoExecutionException 异常
     */
    private void createOutputDirs(String outputPath) throws MojoExecutionException {
        if (outputPath != null) {
            File outputDirectory = new File(outputPath).getParentFile();
            if (outputDirectory != null && !outputDirectory.exists()) {
                if (!outputDirectory.mkdirs()) {
                    throw new MojoExecutionException(
                        String.format("Create directory [%s] for output failed.", outputPath));
                }
            }
        }
    }

    /**
     * 校验swagger spec和该插件必须配置的内容
     *
     * @param apiSource <apiSource>标签内的内容
     */
    private void validateConfiguration(ApiSource apiSource) {
        if (apiSource == null) {
            throw new GenerateException("你并没有配置任何的apiSource!");
        } else if (apiSource.getInfo() == null) {
            throw new GenerateException("`<info>` 是 Swagger Spec中必须配置的.");
        }
        if (apiSource.getInfo().getTitle() == null) {
            throw new GenerateException("`<info><title>`是 Swagger Spec必须配置的.");
        }

        if (apiSource.getInfo().getVersion() == null) {
            throw new GenerateException("`<info><version>` 是 Swagger Spec必须配置的.");
        }

        if (apiSource.getInfo().getLicense() != null && apiSource.getInfo().getLicense().getName() == null) {
            throw new GenerateException("`<info><license><name>` 是 Swagger Spec必须配置的.");
        }

        if (apiSource.getLocations() == null) {
            throw new GenerateException("<locations> 是该插件必须配置的.");
        }

    }

    /**
     * 当前工程所使用的swagger版本是否是Spec11
     *
     * @return true-是，false-否
     */
    private boolean useSwaggerSpec11() {
        try {
            Class.forName("com.wordnik.swagger.annotations.ApiErrors");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 当前工程所使用的swagger版本是否是Spec13
     *
     * @return true-是，false-否
     */
    private boolean useSwaggerSpec13() {
        try {
            Class.forName("com.wordnik.swagger.model.ApiListing");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private String getSwaggerDirectoryName(String swaggerDirectory) {
        return new File(swaggerDirectory).getName();
    }

    /**
     * 配置jackson {@link com.fasterxml.jackson.databind.ObjectMapper}参数
     *
     * @param features 参数
     * @param enabled 是否启用
     * @throws Exception 异常
     */
    private void configureObjectMapperFeatures(List<String> features, boolean enabled) throws Exception {
        for (String feature : features) {
            int i = feature.lastIndexOf(SymbolConstant.PERIOD);
            Class clazz = Class.forName(feature.substring(0, i));
            Enum e = Enum.valueOf(clazz, feature.substring(i + 1));
            getLog().debug("enabling " + e.getDeclaringClass().toString() + SymbolConstant.PERIOD + e.name());
            Method method = Json.mapper().getClass().getMethod("configure", e.getClass(), boolean.class);
            method.invoke(Json.mapper(), e, enabled);
        }
    }

}
