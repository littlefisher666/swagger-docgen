package com.github.litttlefisher.swagger.docgen.document;

import java.io.File;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.core.annotation.AnnotationUtils;

import com.github.litttlefisher.swagger.docgen.constant.SymbolConstant;
import com.github.litttlefisher.swagger.docgen.document.properties.MavenParameterInitialization;
import com.github.litttlefisher.swagger.docgen.enums.DocumentSourceType;
import com.github.litttlefisher.swagger.docgen.enums.Output;
import com.github.litttlefisher.swagger.docgen.exception.GenerateException;
import com.github.litttlefisher.swagger.docgen.reader.JavaDocReader;
import com.google.common.base.Strings;

import io.swagger.annotations.SwaggerDefinition;
import io.swagger.models.Contact;
import io.swagger.models.ExternalDocs;
import io.swagger.models.Info;
import io.swagger.models.License;
import io.swagger.util.BaseReaderUtils;
import lombok.Builder;
import lombok.Data;

/**
 * @author littlefisher
 */
@Data
@Builder
public class ApiSource {

    /** swagger文件默认文件名 */
    public static final String DEFAULT_SWAGGER_FILE_NAME = "swagger";

    /**
     * 配置要扫描的类路径，用以解析{@link io.swagger.annotations.Api}等注解
     */
    private List<String> locations;

    private Info info;

    /**
     * The basePath of your APIs.
     */
    private String basePath;

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
    private String templatePath;

    private String outputPath;

    /** swagger文件生成格式，可选值{@link Output} */
    private String outputFormats = Output.json.name();

    private String swaggerDirectory;

    /** swagger文件名称 */
    private String swaggerFileName = DEFAULT_SWAGGER_FILE_NAME;

    /**
     * <code>attachSwaggerArtifact</code> triggers plugin execution to attach the generated
     * swagger.json to Maven session for deployment purpose.  The attached classifier
     * is the directory name of <code>swaggerDirectory</code>
     */
    private boolean attachSwaggerArtifact;

    /** 类转换，可以把actualClassName转换为expectClassName，要求格式为${actualClassName}:${expectClassName}，且为类全路径 */
    private String modelSubstitute;

    private String apiSortComparator;

    /**
     * Information about swagger filter that will be used for prefiltering
     */
    private String swaggerInternalFilter;

    private String swaggerApiReader;

    /**
     * List of full qualified class names of SwaggerExtension implementations to be
     * considered for the generation
     */
    private List<String> swaggerExtensions;

    /** 注解解析类型 */
    private DocumentSourceType documentSourceType;

    /** 是否解析javadoc */
    private boolean javadocEnabled;

    private boolean useJAXBAnnotationProcessor;

    private boolean useJAXBAnnotationProcessorAsPrimary = true;

    private String swaggerSchemaConverter;

    private List<SecurityDefinition> securityDefinitions;

    /**
     * 需要被过滤掉的类，要求类的全路径
     */
    private List<String> typesToSkip = new ArrayList<>();

    private List<String> apiModelPropertyAccessExclusions = new ArrayList<>();

    private boolean jsonExampleValues = false;

    /** swagger描述 存储的地方 */
    private File descriptionFile;

    /** model转换器类路径 */
    private List<String> modelConverters;

    /** 是否过滤掉继承类 true-要过滤，false-不过滤 */
    private boolean skipInheritingClasses = false;

    private String operationIdFormat;

    private ExternalDocs externalDocs;

    /** contextClassLoader */
    private ClassLoader contextClassLoader;

    /**
     * 加载所有被clazz注解了的类
     *
     * @param clazz 注解的class类
     * @return 所有被clazz注解了的类
     */
    public Set<Class<?>> getValidClasses(Class<? extends Annotation> clazz) {
        // 初始化ClassLoader
        initClassLoader();

        List<String> prefixes = new ArrayList<>();
        if (getLocations() == null) {
            prefixes.add(StringUtils.EMPTY);
        } else {
            // 不考虑解析location可以使用通配符，由于考虑使用spring的解析器，但是该工程是作为插件的，插件扫描不了应用里的依赖包，后续提供ApiSource接口，由应用自己配置所需的参数
            prefixes.addAll(getLocations());
        }

        // 使用reflection包解析都有哪些类被加了clazz注解

        // 继承的类不进行加载
        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
        prefixes.forEach(prefix -> configurationBuilder.setUrls(ClasspathHelper.forPackage(prefix)));
        Set<Class<?>> classes = new Reflections(configurationBuilder).getTypesAnnotatedWith(clazz, true);

        if (!skipInheritingClasses) {
            // 继承的类进行加载
            Set<Class<?>> inherited = new Reflections(configurationBuilder).getTypesAnnotatedWith(clazz);
            classes.addAll(inherited);
        }
        return classes;
    }

    /**
     * 初始化ClassLoader
     */
    private void initClassLoader() {
        if (contextClassLoader == null) {
            Set<URL> urls = new HashSet<>();
            for (String element : MavenParameterInitialization.getCompileClasspathElements()) {
                try {
                    urls.add(new File(element).toURI().toURL());
                } catch (MalformedURLException e) {
                    throw new GenerateException("MalformedURLException", e);
                }
            }

            contextClassLoader = URLClassLoader.newInstance(urls.toArray(new URL[0]),
                Thread.currentThread().getContextClassLoader());

            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
    }

    public Info getInfo() {
        if (info == null) {
            setInfoFromAnnotation();
        }
        return info;
    }

    private void setInfoFromAnnotation() {
        Info resultInfo = new Info();
        for (Class<?> aClass : getValidClasses(SwaggerDefinition.class)) {
            SwaggerDefinition swaggerDefinition = AnnotationUtils.findAnnotation(aClass, SwaggerDefinition.class);
            io.swagger.annotations.Info infoAnnotation = swaggerDefinition.info();

            Info info = new Info().title(infoAnnotation.title()).description(emptyToNull(infoAnnotation.description()))
                .version(infoAnnotation.version()).termsOfService(emptyToNull(infoAnnotation.termsOfService())).license(
                    from(infoAnnotation.license())).contact(from(infoAnnotation.contact()));

            Map<String, Object> customExtensions = BaseReaderUtils.parseExtensions(infoAnnotation.extensions());
            for (Map.Entry<String, Object> extension : customExtensions.entrySet()) {
                resultInfo.setVendorExtension(extension.getKey(), extension.getValue());
            }

            resultInfo.mergeWith(info);
        }
        info = resultInfo;
    }

    private Contact from(io.swagger.annotations.Contact contactAnnotation) {
        Contact contact = new Contact().name(emptyToNull(contactAnnotation.name())).email(
            emptyToNull(contactAnnotation.email())).url(emptyToNull(contactAnnotation.url()));
        if (contact.getName() == null && contact.getEmail() == null && contact.getUrl() == null) {
            contact = null;
        }
        return contact;
    }

    private License from(io.swagger.annotations.License licenseAnnotation) {
        License license = new License().name(emptyToNull(licenseAnnotation.name())).url(
            emptyToNull(licenseAnnotation.url()));
        if (license.getName() == null && license.getUrl() == null) {
            license = null;
        }
        return license;
    }

    private void setBasePathFromAnnotation() {
        for (Class<?> aClass : getValidClasses(SwaggerDefinition.class)) {
            SwaggerDefinition swaggerDefinition = AnnotationUtils.findAnnotation(aClass, SwaggerDefinition.class);
            basePath = emptyToNull(swaggerDefinition.basePath());
        }
    }

    private void setHostFromAnnotation() {
        for (Class<?> aClass : getValidClasses(SwaggerDefinition.class)) {
            SwaggerDefinition swaggerDefinition = AnnotationUtils.findAnnotation(aClass, SwaggerDefinition.class);
            host = emptyToNull(swaggerDefinition.host());
        }
    }

    private void setExternalDocsFromAnnotation() {
        for (Class<?> aClass : getValidClasses(SwaggerDefinition.class)) {
            SwaggerDefinition swaggerDefinition = AnnotationUtils.findAnnotation(aClass, SwaggerDefinition.class);
            io.swagger.annotations.ExternalDocs docsAnnotation = swaggerDefinition.externalDocs();

            if (!Strings.isNullOrEmpty(docsAnnotation.url())) {
                externalDocs = new ExternalDocs(docsAnnotation.value(), docsAnnotation.url());
                break;
            }
        }
    }

    public String getBasePathFromAnnotation() {
        if (basePath == null) {
            setBasePathFromAnnotation();
        }
        return basePath;
    }

    public String getHostFromAnnotation() {
        if (host == null) {
            setHostFromAnnotation();
        }
        return host;
    }

    public ExternalDocs getExternalDocsFromAnnotation() {
        if (externalDocs == null) {
            setExternalDocsFromAnnotation();
        }
        return externalDocs;
    }

    private String emptyToNull(String str) {
        return StringUtils.isEmpty(str) ? null : str;
    }

    /**
     * 解析javadoc信息
     */
    public void initJavaDoc() {
        if (isJavadocEnabled()) {
            List<String> compileClasspathElements = MavenParameterInitialization.getCompileClasspathElements();
            List<File> dependencies = compileClasspathElements.stream().map(
                input -> StringUtils.replaceAll(input, ".jar", SymbolConstant.MINUS + JavaDocReader.SOURCE_JAR_SUFFIX))
                .map(File::new).filter(File::exists).filter(File::isFile).collect(Collectors.toList());
            JavaDocReader.init(MavenParameterInitialization.getBuildDirectory(), dependencies);
        }
    }
}