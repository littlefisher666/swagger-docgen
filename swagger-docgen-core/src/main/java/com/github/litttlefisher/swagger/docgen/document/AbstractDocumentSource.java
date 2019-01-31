package com.github.litttlefisher.swagger.docgen.document;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule.Priority;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.StringHelpers;
import com.github.litttlefisher.swagger.docgen.constant.SymbolConstant;
import com.github.litttlefisher.swagger.docgen.enums.Output;
import com.github.litttlefisher.swagger.docgen.exception.GenerateException;
import com.github.litttlefisher.swagger.docgen.jackson.EnhancedSwaggerModule;
import com.github.litttlefisher.swagger.docgen.reader.AbstractReader;
import com.github.litttlefisher.swagger.docgen.reader.ClassSwaggerReader;
import com.github.litttlefisher.swagger.docgen.reader.ModelJavaDocConverter;
import com.github.litttlefisher.swagger.docgen.reader.ModelModifier;
import com.google.common.collect.Maps;

import io.swagger.annotations.Api;
import io.swagger.config.FilterFactory;
import io.swagger.converter.ModelConverter;
import io.swagger.converter.ModelConverters;
import io.swagger.core.filter.SpecFilter;
import io.swagger.core.filter.SwaggerSpecFilter;
import io.swagger.jaxrs.ext.SwaggerExtension;
import io.swagger.jaxrs.ext.SwaggerExtensions;
import io.swagger.models.Path;
import io.swagger.models.Scheme;
import io.swagger.models.Swagger;
import io.swagger.models.auth.SecuritySchemeDefinition;
import io.swagger.models.properties.Property;
import io.swagger.util.Json;
import io.swagger.util.Yaml;
import lombok.extern.slf4j.Slf4j;

/**
 * @author littlefisher
 */
@Slf4j
public abstract class AbstractDocumentSource {

    /** 本模块定义的swagger相关配置 */
    protected final ApiSource apiSource;
    /** 日志 */
    /** 需要忽略的class */
    protected final List<Type> typesToSkip = new ArrayList<>();
    /** 文件输出位置 */
    private final String outputPath;
    private final String templatePath;
    /** swagger文件生成路径 */
    private final String swaggerPath;
    /** 类转换，可以把actualClassName转换为expectClassName，要求格式为${actualClassName}:${expectClassName}，且为类全路径 */
    private final String modelSubstitute;
    private final boolean jsonExampleValues;
    /** swagger本身的配置 */
    protected Swagger swagger;
    protected String swaggerSchemaConverter;
    /** 字符集 */
    protected String encoding = StandardCharsets.UTF_8.displayName();
    /** jackson对象 */
    private ObjectMapper mapper = Json.mapper();
    private boolean isSorted = false;

    public AbstractDocumentSource(ApiSource apiSource) {
        this.outputPath = apiSource.getOutputPath();
        this.templatePath = apiSource.getTemplatePath();
        this.swaggerPath = apiSource.getSwaggerDirectory();
        this.modelSubstitute = apiSource.getModelSubstitute();
        this.jsonExampleValues = apiSource.isJsonExampleValues();

        swagger = new Swagger();
        if (apiSource.getSchemes() != null) {
            for (String scheme : apiSource.getSchemes()) {
                swagger.scheme(Scheme.forValue(scheme));
            }
        }

        // read description from file
        if (apiSource.getDescriptionFile() != null) {
            try (InputStream is = new FileInputStream(apiSource.getDescriptionFile())) {
                apiSource.getInfo().setDescription(IOUtils.toString(is));
            } catch (IOException e) {
                throw new GenerateException(e.getMessage(), e);
            }
        }

        swagger.setHost(apiSource.getHostFromAnnotation());
        swagger.setInfo(apiSource.getInfo());
        swagger.setBasePath(apiSource.getBasePathFromAnnotation());
        swagger.setExternalDocs(apiSource.getExternalDocsFromAnnotation());

        this.apiSource = apiSource;
    }

    public void loadDocuments() throws GenerateException {
        ClassSwaggerReader reader = resolveApiReader();

        loadSwaggerExtensions(apiSource);

        swagger = reader.read(getValidClasses());

        swagger = removeBasePathFromEndpoints(swagger, apiSource.isRemoveBasePathFromEndpoints());

        swagger = addSecurityDefinitions(swagger, apiSource);

        swagger = doFilter(swagger);
    }

    private Swagger doFilter(Swagger swagger) throws GenerateException {
        String filterClassName = apiSource.getSwaggerInternalFilter();
        if (filterClassName != null) {
            try {
                log.debug(String.format("Setting filter configuration: %s", filterClassName));
                FilterFactory.setFilter((SwaggerSpecFilter) Class.forName(filterClassName).newInstance());
            } catch (Exception e) {
                throw new GenerateException("Cannot load: " + filterClassName, e);
            }
        }

        SwaggerSpecFilter filter = FilterFactory.getFilter();
        if (filter == null) {
            return swagger;
        }
        return new SpecFilter().filter(swagger, filter, new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    private Swagger addSecurityDefinitions(final Swagger swagger, ApiSource apiSource) throws GenerateException {
        if (apiSource.getSecurityDefinitions() == null) {
            return swagger;
        }
        Map<String, SecuritySchemeDefinition> definitions = new TreeMap<>();
        for (SecurityDefinition sd : apiSource.getSecurityDefinitions()) {
            for (Map.Entry<String, SecuritySchemeDefinition> entry : sd.generateSecuritySchemeDefinitions()
                .entrySet()) {
                definitions.put(entry.getKey(), entry.getValue());
            }
        }
        swagger.setSecurityDefinitions(definitions);
        return swagger;
    }

    /**
     * 加载个性化配置的Swagger扩展解析器
     */
    private void loadSwaggerExtensions(ApiSource apiSource) throws GenerateException {
        if (apiSource.getSwaggerExtensions() != null) {
            List<SwaggerExtension> extensions = SwaggerExtensions.getExtensions();
            extensions.addAll(resolveSwaggerExtensions());
        }
    }

    public void toSwaggerDocuments(String outputFormats, String encoding) throws GenerateException {
        toSwaggerDocuments(outputFormats, null, encoding);
    }

    public void toSwaggerDocuments(String outputFormats, String fileName, String encoding) throws GenerateException {
        mapper.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false);

        mapper = mapper.copy();

        if (jsonExampleValues) {
            mapper.addMixIn(Property.class, PropertyExampleMixIn.class);
        }

        if (swaggerPath == null) {
            return;
        }

        if (!isSorted) {
            Utils.sortSwagger(swagger);
            isSorted = true;
        }

        File dir = new File(swaggerPath);
        if (dir.isFile()) {
            throw new GenerateException(String.format("Swagger-outputDirectory[%s] must be a directory!", swaggerPath));
        }

        if (!dir.exists()) {
            try {
                FileUtils.forceMkdir(dir);
            } catch (IOException e) {
                throw new GenerateException(String.format("Create Swagger-outputDirectory[%s] failed.", swaggerPath));
            }
        }

        for (String format : outputFormats.split(SymbolConstant.COMMA)) {
            try {
                Output output = Output.valueOf(format.toLowerCase());
                switch (output) {
                    case json:
                        ObjectWriter jsonWriter = mapper.writer(new DefaultPrettyPrinter());
                        FileUtils.write(new File(dir, fileName + SymbolConstant.PERIOD + output.name()),
                            jsonWriter.writeValueAsString(swagger), encoding);
                        break;
                    case yaml:
                        FileUtils.write(new File(dir, fileName + SymbolConstant.PERIOD + output.name()),
                            Yaml.pretty().writeValueAsString(swagger), encoding);
                        break;
                    default:
                        throw new GenerateException(
                            String.format("Declared output format [%s] is not supported.", format));
                }
            } catch (Exception e) {
                throw new GenerateException(String.format("Declared output format [%s] is not supported.", format));
            }
        }
    }

    public void loadModelModifier() throws GenerateException {
        ObjectMapper objectMapper = Json.mapper();
        if (apiSource.isUseJAXBAnnotationProcessor()) {
            JaxbAnnotationModule jaxbAnnotationModule = new JaxbAnnotationModule();
            if (apiSource.isUseJAXBAnnotationProcessorAsPrimary()) {
                jaxbAnnotationModule.setPriority(Priority.PRIMARY);
            } else {
                jaxbAnnotationModule.setPriority(Priority.SECONDARY);
            }
            objectMapper.registerModule(jaxbAnnotationModule);

            // to support @ApiModel on class level.
            // must be registered only if we use JaxbAnnotationModule before. Why?
            objectMapper.registerModule(new EnhancedSwaggerModule());
        }
        ModelModifier modelModifier = new ModelModifier(objectMapper);

        List<String> apiModelPropertyAccessExclusions = apiSource.getApiModelPropertyAccessExclusions();
        if (CollectionUtils.isNotEmpty(apiModelPropertyAccessExclusions)) {
            modelModifier.setApiModelPropertyAccessExclusions(apiModelPropertyAccessExclusions);
        }

        if (modelSubstitute != null) {
            // 解析modelSubstitute字符串，转为actualClass和expectClass
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getClass().getResourceAsStream(this.modelSubstitute)))) {
                String line = reader.readLine();
                while (line != null) {
                    String[] classes = line.split(SymbolConstant.COLON);
                    if (classes.length != 2) {
                        throw new GenerateException(
                            "Bad format of override model file, it should be ${actualClassName}:${expectClassName}");
                    }
                    modelModifier.addModelSubstitute(classes[0].trim(), classes[1].trim());
                    line = reader.readLine();
                }
            } catch (IOException e) {
                throw new GenerateException(e);
            }
        }

        ModelConverters.getInstance().addConverter(modelModifier);
    }

    /**
     * 加载javadoc对象转换器
     */
    public void loadModelJavaDocConverter() {
        ModelConverters.getInstance().addConverter(new ModelJavaDocConverter(Json.mapper()));
    }

    /**
     * 加载model转换器
     */
    public void loadModelConverters() {
        final List<String> modelConverters = apiSource.getModelConverters();
        if (modelConverters == null) {
            return;
        }

        for (String modelConverter : modelConverters) {
            try {
                final Class<?> modelConverterClass = Class.forName(modelConverter);
                if (ModelConverter.class.isAssignableFrom(modelConverterClass)) {
                    final ModelConverter modelConverterInstance = (ModelConverter) modelConverterClass.newInstance();
                    ModelConverters.getInstance().addConverter(modelConverterInstance);
                } else {
                    throw new GenerateException(String
                        .format("Class %s has to be a subclass of %s", modelConverterClass.getName(),
                            ModelConverter.class));
                }
            } catch (ClassNotFoundException e) {
                throw new GenerateException(String.format("Could not find custom model converter %s", modelConverter),
                    e);
            } catch (InstantiationException | IllegalAccessException e) {
                throw new GenerateException(
                    String.format("Unable to instantiate custom model converter %s", modelConverter), e);
            }
        }
    }

    /**
     * 加载需要过滤的class类
     */
    public void loadTypesToSkip() {
        List<String> typesToSkip = apiSource.getTypesToSkip();
        if (typesToSkip == null) {
            return;
        }
        for (String typeToSkip : typesToSkip) {
            try {
                Type type = Class.forName(typeToSkip);
                this.typesToSkip.add(type);
            } catch (ClassNotFoundException e) {
                throw new GenerateException(e);
            }
        }
    }

    protected Swagger removeBasePathFromEndpoints(Swagger swagger, boolean removeBasePathFromEndpoints) {
        if (!removeBasePathFromEndpoints) {
            return swagger;
        }
        String basePath = swagger.getBasePath();
        if (StringUtils.isEmpty(basePath)) {
            return swagger;
        }
        Map<String, Path> oldPathMap = swagger.getPaths();
        Map<String, Path> newPathMap = Maps.newHashMap();
        for (Map.Entry<String, Path> entry : oldPathMap.entrySet()) {
            newPathMap.put(entry.getKey().replace(basePath, StringUtils.EMPTY), entry.getValue());
        }
        swagger.setPaths(newPathMap);
        return swagger;
    }

    protected File createFile(File dir, String outputResourcePath) throws IOException {
        File serviceFile;
        int i = outputResourcePath.lastIndexOf("/");
        if (i != -1) {
            String fileName = outputResourcePath.substring(i + 1);
            String subDir = outputResourcePath.substring(0, i);
            File finalDirectory = new File(dir, subDir);
            finalDirectory.mkdirs();
            serviceFile = new File(finalDirectory, fileName);
        } else {
            serviceFile = new File(dir, outputResourcePath);
        }
        while (!serviceFile.createNewFile()) {
            serviceFile.delete();
        }
        log.info("Creating file " + serviceFile.getAbsolutePath());
        return serviceFile;
    }

    public void toDocuments() throws GenerateException {
        if (!isSorted) {
            // 对Swagger中的path、response、definition、tag进行排序
            Utils.sortSwagger(swagger);
            isSorted = true;
        }
        log.info("Writing doc to " + outputPath + "...");

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(outputPath);
            OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream, encoding);

            TemplatePath tp = Utils.parseTemplateUrl(templatePath);

            Handlebars handlebars = new Handlebars(tp.getLoader());
            initHandlebars(handlebars);

            Template template = handlebars.compile(tp.getName());

            template.apply(swagger, writer);
            writer.close();
            log.info("Done!");
        } catch (IOException e) {
            throw new GenerateException(e);
        }
    }

    private void initHandlebars(Handlebars handlebars) {
        handlebars.registerHelper("ifeq", (Helper<String>) (value, options) -> {
            if (value == null || options.param(0) == null) {
                return options.inverse();
            }
            if (value.equals(options.param(0))) {
                return options.fn();
            }
            return options.inverse();
        });

        handlebars.registerHelper("basename", (Helper<String>) (value, options) -> {
            if (value == null) {
                return null;
            }
            int lastSlash = value.lastIndexOf("/");
            if (lastSlash == -1) {
                return value;
            } else {
                return value.substring(lastSlash + 1);
            }
        });

        handlebars.registerHelper(StringHelpers.join.name(), StringHelpers.join);
        handlebars.registerHelper(StringHelpers.lower.name(), StringHelpers.lower);
    }

    /**
     * 加载api解析器，由子类实现
     *
     * @return ClassSwaggerReader to use
     * @throws GenerateException if the reader cannot be created / resolved
     */
    protected abstract ClassSwaggerReader resolveApiReader() throws GenerateException;

    /**
     * Returns the set of classes which should be included in the scanning.
     *
     * @return Set containing all valid classes
     */
    protected Set<Class<?>> getValidClasses() {
        return apiSource.getValidClasses(Api.class);
    }

    /**
     * 解析所有{@link SwaggerExtension}实现类，并配置到Swagger中
     *
     * @return List of {@link SwaggerExtension} which should be added to the swagger configuration
     * @throws GenerateException if the swagger extensions could not be created / resolved
     */
    protected List<SwaggerExtension> resolveSwaggerExtensions() throws GenerateException {
        List<String> classes = apiSource.getSwaggerExtensions();
        List<SwaggerExtension> resolved = new ArrayList<>();
        if (classes != null) {
            for (String clazz : classes) {
                SwaggerExtension extension;
                try {
                    extension = (SwaggerExtension) Class.forName(clazz).newInstance();
                } catch (Exception e) {
                    throw new GenerateException("Cannot load Swagger extension: " + clazz, e);
                }
                resolved.add(extension);
            }
        }
        return resolved;
    }

    protected ClassSwaggerReader getCustomApiReader(String customReaderClassName) throws GenerateException {
        try {
            log.info("Reading custom API reader: " + customReaderClassName);
            Class<?> clazz = Class.forName(customReaderClassName);
            if (AbstractReader.class.isAssignableFrom(clazz)) {
                Constructor<?> constructor = clazz.getConstructor(Swagger.class);
                return (ClassSwaggerReader) constructor.newInstance(swagger);
            } else {
                return (ClassSwaggerReader) clazz.newInstance();
            }
        } catch (Exception e) {
            throw new GenerateException("Cannot load Swagger API reader: " + customReaderClassName, e);
        }
    }
}

