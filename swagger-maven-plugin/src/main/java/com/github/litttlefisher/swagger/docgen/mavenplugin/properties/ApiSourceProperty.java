package com.github.litttlefisher.swagger.docgen.mavenplugin.properties;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugins.annotations.Parameter;

import com.github.litttlefisher.swagger.docgen.enums.DocumentSourceType;
import com.github.litttlefisher.swagger.docgen.enums.Output;

import lombok.Data;

/**
 * @author littlefisher
 */
@Data
public class ApiSourceProperty {

    /** swagger文件默认文件名 */
    public static final String DEFAULT_SWAGGER_FILE_NAME = "swagger";

    /**
     * 配置要扫描的类路径，用以解析{@link io.swagger.annotations.Api}等注解
     */
    @Parameter(required = true)
    private List<String> locations;

    @Parameter
    private InfoProperty info;

    /**
     * The basePath of your APIs.
     */
    @Parameter
    private String basePath;

    @Parameter(defaultValue = "false")
    private boolean removeBasePathFromEndpoints;

    /**
     * The host (name or ip) serving the API.
     * This MUST be the host only and does not include the scheme nor sub-paths.
     * It MAY include a port. If the host is not included, the host serving the documentation
     * is to be used (including the port). The host does not support path templating.
     */
    private String host;

    /**
     * The transfer protocols of the API. Values MUST be from the list: "http", "https", "ws", "wss"
     */
    private List<String> schemes;

    /**
     * 使用handlebars插件生成模板文件，具体请参考<url>https://github.com/jknack/handlebars.java</url>
     */
    @Parameter
    private String templatePath;

    @Parameter
    private String outputPath;

    /** swagger文件生成格式，可选值{@link Output} */
    @Parameter(defaultValue = "json")
    private String outputFormats = Output.json.name();

    @Parameter
    private String swaggerDirectory;

    /** swagger文件名称 */
    @Parameter(defaultValue = DEFAULT_SWAGGER_FILE_NAME)
    private String swaggerFileName = DEFAULT_SWAGGER_FILE_NAME;

    /**
     * <code>attachSwaggerArtifact</code> triggers plugin execution to attach the generated
     * swagger.json to Maven session for deployment purpose.  The attached classifier
     * is the directory name of <code>swaggerDirectory</code>
     */
    @Parameter
    private boolean attachSwaggerArtifact;

    /** 类转换，可以把actualClassName转换为expectClassName，要求格式为${actualClassName}:${expectClassName}，且为类全路径 */
    @Parameter
    private String modelSubstitute;

    @Parameter
    private String apiSortComparator;

    /**
     * Information about swagger filter that will be used for prefiltering
     */
    @Parameter
    private String swaggerInternalFilter;

    @Parameter
    private String swaggerApiReader;

    /**
     * List of full qualified class names of SwaggerExtension implementations to be
     * considered for the generation
     */
    @Parameter
    private List<String> swaggerExtensions;

    /** 注解解析类型 */
    @Parameter(required = true)
    private DocumentSourceType documentSourceType;

    /** 是否解析javadoc */
    @Parameter
    private boolean javadocEnabled;

    @Parameter
    private boolean useJAXBAnnotationProcessor;

    @Parameter
    private boolean useJAXBAnnotationProcessorAsPrimary = true;

    @Parameter
    private String swaggerSchemaConverter;

    @Parameter
    private List<SecurityDefinitionProperty> securityDefinitions;

    /**
     * 需要被过滤掉的类，要求类的全路径
     */
    @Parameter
    private List<String> typesToSkip = new ArrayList<>();

    @Parameter
    private List<String> apiModelPropertyAccessExclusions = new ArrayList<>();

    @Parameter
    private boolean jsonExampleValues = false;

    /** swagger描述 存储的地方 */
    @Parameter
    private File descriptionFile;

    /** model转换器类路径 */
    @Parameter
    private List<String> modelConverters;

    /** 是否过滤掉继承类 true-要过滤，false-不过滤 */
    @Parameter
    private boolean skipInheritingClasses = false;

    @Parameter
    private String operationIdFormat;

    @Parameter
    private ExternalDocsProperty externalDocs;
}