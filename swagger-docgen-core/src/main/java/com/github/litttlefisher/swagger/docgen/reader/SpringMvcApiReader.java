package com.github.litttlefisher.swagger.docgen.reader;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.github.litttlefisher.swagger.docgen.exception.GenerateException;
import com.github.litttlefisher.swagger.docgen.spring.SpringResource;
import com.github.litttlefisher.swagger.docgen.spring.SpringSwaggerExtension;
import com.github.litttlefisher.swagger.docgen.util.SpringUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.AuthorizationScope;
import io.swagger.converter.ModelConverters;
import io.swagger.jaxrs.ext.SwaggerExtension;
import io.swagger.jaxrs.ext.SwaggerExtensions;
import io.swagger.models.Model;
import io.swagger.models.Operation;
import io.swagger.models.Response;
import io.swagger.models.SecurityRequirement;
import io.swagger.models.Swagger;
import io.swagger.models.Tag;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.utils.PropertyModelConverter;
import io.swagger.util.BaseReaderUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * SpringMvc api解析器
 *
 * @author littlefisher
 */
@Slf4j
public class SpringMvcApiReader extends AbstractReader implements ClassSwaggerReader {

    private static final ResponseContainerConverter RESPONSE_CONTAINER_CONVERTER = new ResponseContainerConverter();

    /**
     * Spring异常处理器的解析器
     */
    private final SpringExceptionHandlerReader exceptionHandlerReader;

    private List<String> resourcePaths;

    public SpringMvcApiReader(Swagger swagger) {
        super(swagger);
        exceptionHandlerReader = new SpringExceptionHandlerReader();
    }

    @Override
    protected void updateExtensionChain() {
        List<SwaggerExtension> extensions = new ArrayList<>();
        extensions.add(new SpringSwaggerExtension());
        SwaggerExtensions.setExtensions(extensions);
    }

    @Override
    public Swagger read(Set<Class<?>> classes) throws GenerateException {
        //relate all methods to one base request mapping if multiple controllers exist for that mapping
        //get all methods from each controller & find their request mapping
        //create map - resource string (after first slash) as key, new SpringResource as value
        Map<String, SpringResource> resourceMap = generateResourceMap(classes);
        exceptionHandlerReader.processExceptionHandlers(classes);
        for (SpringResource resource : resourceMap.values()) {
            read(resource);
        }

        return swagger;
    }

    /**
     * 解析{@link SpringResource}生成Swagger配置
     *
     * @param resource SpringMvc配置
     */
    public void read(SpringResource resource) {
        if (swagger == null) {
            swagger = new Swagger();
        }

        List<Method> methods = resource.getMethods();
        Map<String, Tag> tags = Maps.newHashMap();

        List<SecurityRequirement> resourceSecurities = new ArrayList<>();

        // Add the description from the controller api
        Class<?> controller = resource.getControllerClass();
        RequestMapping controllerRequestMapping = AnnotatedElementUtils.findMergedAnnotation(controller,
            RequestMapping.class);

        String[] controllerProduces = new String[0];
        String[] controllerConsumes = new String[0];
        if (controllerRequestMapping != null) {
            controllerConsumes = controllerRequestMapping.consumes();
            controllerProduces = controllerRequestMapping.produces();
        }

        if (controller.isAnnotationPresent(Api.class)) {
            Api api = AnnotatedElementUtils.findMergedAnnotation(controller, Api.class);
            if (!canReadApi(false, api)) {
                return;
            }
            tags = updateTagsForApi(null, api);
            resourceSecurities = getSecurityRequirements(api);
        }

        resourcePaths = resource.getControllerMapping();

        //collect api from method with @RequestMapping
        Map<String, List<Method>> apiMethodMap = collectApisByRequestMapping(methods);

        for (String path : apiMethodMap.keySet()) {
            for (Method method : apiMethodMap.get(path)) {
                RequestMapping requestMapping = AnnotatedElementUtils.findMergedAnnotation(method,
                    RequestMapping.class);
                if (requestMapping == null) {
                    continue;
                }
                ApiOperation apiOperation = AnnotatedElementUtils.findMergedAnnotation(method, ApiOperation.class);
                if (apiOperation != null && apiOperation.hidden()) {
                    continue;
                }

                Map<String, String> regexMap = Maps.newHashMap();
                String operationPath = parseOperationPath(path, regexMap);

                // http method
                for (RequestMethod requestMethod : requestMapping.method()) {
                    String httpMethod = requestMethod.toString().toLowerCase();
                    Operation operation = parseMethod(method, requestMethod);

                    updateOperationParameters(new ArrayList<>(), regexMap, operation);

                    parserOperationProtocols(apiOperation, operation);

                    String[] apiProduces = requestMapping.produces();
                    String[] apiConsumes = requestMapping.consumes();

                    apiProduces = (apiProduces.length == 0) ? controllerProduces : apiProduces;
                    apiConsumes = (apiConsumes.length == 0) ? controllerConsumes : apiConsumes;

                    apiConsumes = parserOperationConsumes(new String[0], apiConsumes, operation);
                    apiProduces = parserOperationProduces(new String[0], apiProduces, operation);

                    parserTagsForOperation(operation, apiOperation);
                    updateOperation(apiConsumes, apiProduces, tags, resourceSecurities, operation);
                    updatePath(operationPath, httpMethod, operation, method);
                }
            }
        }
    }

    /**
     * 解析方法
     *
     * @param method 方法
     * @param requestMethod 请求方式
     * @return 组装后的包装类
     */
    private Operation parseMethod(Method method, RequestMethod requestMethod) {
        // 默认为200，成功
        int responseCode = 200;
        Operation operation = new Operation();

        RequestMapping requestMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        Type responseClass = null;
        String responseContainer = null;
        // 生成一个操作编号
        String operationId = getOperationId(method, requestMethod.name());
        Map<String, Property> defaultResponseHeaders = null;

        ApiOperation apiOperation = AnnotatedElementUtils.findMergedAnnotation(method, ApiOperation.class);

        if (apiOperation != null) {
            if (apiOperation.hidden()) {
                return null;
            }
            if (!apiOperation.nickname().isEmpty()) {
                operationId = apiOperation.nickname();
            }

            defaultResponseHeaders = parseResponseHeaders(apiOperation.responseHeaders());

            operation.summary(getMethodSummary(apiOperation.value(), method)).description(apiOperation.notes());

            Map<String, Object> customExtensions = BaseReaderUtils.parseExtensions(apiOperation.extensions());
            operation.setVendorExtensions(customExtensions);

            if (!apiOperation.response().equals(Void.class)) {
                responseClass = apiOperation.response();
            }
            if (!apiOperation.responseContainer().isEmpty()) {
                responseContainer = apiOperation.responseContainer();
            }

            ///security
            List<SecurityRequirement> securities = new ArrayList<>();
            for (Authorization auth : apiOperation.authorizations()) {
                if (!auth.value().isEmpty()) {
                    SecurityRequirement security = new SecurityRequirement();
                    List<String> scopes = Arrays.stream(auth.scopes()).filter(input -> !input.scope().isEmpty()).map(
                        AuthorizationScope::scope).collect(Collectors.toList());
                    security.requirement(auth.value(), scopes);
                    securities.add(security);
                }
            }
            securities.forEach(operation::security);

            responseCode = apiOperation.code();
        }

        processResponseClass(method, responseCode, operation, responseClass, responseContainer, defaultResponseHeaders);

        operation.operationId(operationId);

        operation.produces(Arrays.stream(requestMapping.produces()).distinct().collect(Collectors.toList()));
        operation.consumes(Arrays.stream(requestMapping.consumes()).distinct().collect(Collectors.toList()));

        ApiResponses responseAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, ApiResponses.class);
        if (responseAnnotation != null) {
            parserApiResponse(operation, responseAnnotation);
        } else {
            ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(method, ResponseStatus.class);
            if (responseStatus != null) {
                operation.response(responseStatus.value().value(), new Response().description(responseStatus.reason()));
            }
        }

        List<ResponseStatus> errorResponses = exceptionHandlerReader.getResponseStatusesFromExceptions(method);
        errorResponses.forEach(responseStatus -> {
            int code = responseStatus.code().value();
            String description = StringUtils.defaultIfEmpty(responseStatus.reason(),
                responseStatus.code().getReasonPhrase());
            operation.response(code, new Response().description(description));
        });

        Deprecated annotation = AnnotationUtils.findAnnotation(method, Deprecated.class);
        if (annotation != null) {
            operation.deprecated(true);
        }

        processParameter(method, operation);

        if (operation.getResponses() == null) {
            operation.defaultResponse(new Response().description("successful operation"));
        }

        // Process @ApiImplicitParams
        this.readImplicitParameters(method, operation);

        processOperationDecorator(operation, method);

        return operation;
    }

    /**
     * 解析方法的入参
     *
     * @param method 方法
     * @param operation 返回信息对象
     */
    private void processParameter(Method method, Operation operation) {
        // process parameters
        Class[] parameterTypes = method.getParameterTypes();
        Type[] genericParameterTypes = method.getGenericParameterTypes();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        // paramTypes = method.getParameterTypes
        // genericParamTypes = method.getGenericParameterTypes
        for (int i = 0; i < parameterTypes.length; i++) {
            Type type = genericParameterTypes[i];
            List<Annotation> annotations = Arrays.asList(paramAnnotations[i]);
            List<Parameter> parameters = getParameters(type, annotations);

            for (Parameter parameter : parameters) {
                if (parameter.getName().isEmpty()) {
                    parameter.setName(parameterNames[i]);
                }
                operation.parameter(parameter);
            }
        }
    }

    /**
     * 解析填充返回类型相关信息
     *
     * @param method 解析的方法
     * @param responseCode 返回code
     * @param operation 返回信息对象
     * @param responseClass 返回对象class
     * @param responseContainer 返回对象容器
     * @param defaultResponseHeaders 返回信息header
     */
    private void processResponseClass(Method method, int responseCode, Operation operation, Type responseClass,
        String responseContainer, Map<String, Property> defaultResponseHeaders) {
        if (responseClass == null) {
            // pick out response from method declaration
            log.info("picking up response class from method " + method);
            responseClass = method.getGenericReturnType();
        }
        if (responseClass instanceof ParameterizedType && ResponseEntity.class.equals(
            ((ParameterizedType) responseClass).getRawType())) {
            responseClass = ((ParameterizedType) responseClass).getActualTypeArguments()[0];
        }
        boolean hasApiAnnotation = false;
        if (responseClass instanceof Class) {
            hasApiAnnotation = AnnotationUtils.findAnnotation((Class) responseClass, Api.class) != null;
        }
        if (responseClass != null && !responseClass.equals(Void.class) && !responseClass.equals(ResponseEntity.class)
            && !hasApiAnnotation) {
            if (isPrimitive(responseClass)) {
                Property property = ModelConverters.getInstance().readAsProperty(responseClass);
                if (property != null) {
                    Property responseProperty = RESPONSE_CONTAINER_CONVERTER.withResponseContainer(responseContainer,
                        property);
                    operation.response(responseCode, new Response().description("successful operation")
                        .responseSchema(new PropertyModelConverter().propertyToModel(responseProperty))
                        .headers(defaultResponseHeaders));
                }
            } else if (!responseClass.equals(Void.class) && !responseClass.equals(void.class)) {
                Map<String, Model> models = ModelConverters.getInstance().read(responseClass);
                if (models.isEmpty()) {
                    Property pp = ModelConverters.getInstance().readAsProperty(responseClass);
                    operation.response(responseCode, new Response().description("successful operation")
                        .responseSchema(new PropertyModelConverter().propertyToModel(pp))
                        .headers(defaultResponseHeaders));
                }
                for (String key : models.keySet()) {
                    Property responseProperty = RESPONSE_CONTAINER_CONVERTER.withResponseContainer(responseContainer,
                        new RefProperty().asDefault(key));
                    operation.response(responseCode, new Response().description("successful operation")
                        .responseSchema(new PropertyModelConverter().propertyToModel(responseProperty))
                        .headers(defaultResponseHeaders));
                    swagger.model(key, models.get(key));
                }
            }
            // 解析返回类型上的注解，组装成Model对象
            Map<String, Model> models = ModelConverters.getInstance().readAll(responseClass);
            models.forEach((name, model) -> swagger.model(name, model));
        }
    }

    /**
     * 一个api路径下对应的方法
     *
     * @param methods Controller下的方法
     * @return 一个api路径下对应的方法
     */
    private Map<String, List<Method>> collectApisByRequestMapping(List<Method> methods) {
        Map<String, List<Method>> apiMethodMap = Maps.newHashMap();
        for (Method method : methods) {
            RequestMapping requestMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (requestMapping != null) {
                List<String> paths;
                if (requestMapping.value().length != 0) {
                    paths = generateFullPath(requestMapping.value());
                } else {
                    paths = resourcePaths;
                }
                paths.forEach(path -> {
                    if (apiMethodMap.containsKey(path)) {
                        apiMethodMap.get(path).add(method);
                    } else {
                        List<Method> ms = new ArrayList<>();
                        ms.add(method);
                        apiMethodMap.put(path, ms);
                    }
                });

            }
        }

        return apiMethodMap;
    }

    /**
     * 方法上{@link RequestMapping}配置的path和Controller上{@link RequestMapping}配置的路径进行组合，组成完整的api路径
     *
     * @param paths 方法上的{@link RequestMapping}配置的path
     * @return 该方法上api的完整路径
     */
    private List<String> generateFullPath(String[] paths) {
        if (ArrayUtils.isNotEmpty(paths)) {
            List<String> newPaths = Lists.newArrayList();
            for (String path : paths) {
                for (String resourcePath : resourcePaths) {
                    newPaths.add(resourcePath + (path.startsWith("/") ? path : '/' + path));
                }
            }
            return newPaths;
        } else {
            return this.resourcePaths;
        }
    }

    /**
     * Helper method for loadDocuments()
     * 解析Controller
     *
     * @param controllerClazz Controller的class
     * @param description 描述
     * @return 解析后的Map
     */
    private Map<String, SpringResource> analyzeController(Class<?> controllerClazz, String description) {
        Map<String, SpringResource> resourceMap = Maps.newHashMap();
        String[] controllerRequestMappingValues = SpringUtils.getControllerRequestMapping(controllerClazz);

        for (String controllerRequestMappingValue : controllerRequestMappingValues) {
            for (Method method : controllerClazz.getMethods()) {
                // 排除由编译器生成的方法
                if (method.isSynthetic()) {
                    continue;
                }
                RequestMapping methodRequestMapping = AnnotatedElementUtils.findMergedAnnotation(method,
                    RequestMapping.class);

                // 解析方法上的@RequestMapping注解
                if (methodRequestMapping != null) {
                    RequestMethod[] requestMappingRequestMethods = methodRequestMapping.method();

                    // For each method-level @RequestMapping annotation, iterate over HTTP Verb
                    for (RequestMethod requestMappingRequestMethod : requestMappingRequestMethods) {
                        String[] methodRequestMappingValues = methodRequestMapping.value();

                        // Check for cases where method-level @RequestMapping#value is not set, and use the controllers @RequestMapping
                        if (methodRequestMappingValues.length == 0) {
                            cacheSpringResource(controllerClazz, resourceMap, description,
                                controllerRequestMappingValue, method, requestMappingRequestMethod);
                        } else {
                            // Here we know that method-level @RequestMapping#value is populated, so
                            // iterate over all the @RequestMapping#value attributes, and add them to the resource map.
                            for (String methodRequestMappingValue : methodRequestMappingValues) {
                                cacheSpringResource(controllerClazz, resourceMap, description,
                                    methodRequestMappingValue, method, requestMappingRequestMethod);
                            }
                        }
                    }
                }
            }
        }
        controllerClazz.getFields();
        controllerClazz.getDeclaredFields();
        //<--In case developer declares a field without an associated getter/setter.
        //this will allow NoClassDefFoundError to be caught before it triggers bamboo failure.

        return resourceMap;
    }

    /**
     * 缓存{@link SpringResource}
     *
     * @param controllerClazz controller的类路径
     * @param resourceMap {@link SpringResource}缓存Map
     * @param description 描述
     * @param requestMappingValue 该api对应的path
     * @param method 该api对应的方法
     * @param requestMappingRequestMethod 放api的请求类型，GET|POST这样的
     */
    private void cacheSpringResource(Class<?> controllerClazz, Map<String, SpringResource> resourceMap,
        String description, String requestMappingValue, Method method, RequestMethod requestMappingRequestMethod) {
        // The map key is a concat of the following:
        //   1. The controller package
        //   2. The controller class name
        //   3. The controller-level @RequestMapping#value
        String resourceKey = controllerClazz.getCanonicalName() + requestMappingValue + requestMappingRequestMethod;
        if (!resourceMap.containsKey(resourceKey)) {
            resourceMap.put(resourceKey,
                new SpringResource(controllerClazz, requestMappingValue, resourceKey, description));
        }
        resourceMap.get(resourceKey).addMethod(method);
    }

    /**
     * 解析Controller类，生成自己定义的{@link SpringResource}
     *
     * @param validClasses 要扫描的class
     * @return 解析到的 {@link SpringResource}
     * @throws GenerateException 异常
     */
    protected Map<String, SpringResource> generateResourceMap(Set<Class<?>> validClasses) throws GenerateException {
        Map<String, SpringResource> resourceMap = Maps.newHashMap();
        for (Class<?> validClass : validClasses) {
            try {
                // 分析Controller
                resourceMap.putAll(analyzeController(validClass, StringUtils.EMPTY));
            } catch (NoClassDefFoundError e) {
                log.error(e.getMessage());
                log.info(validClass.getName());
                //exception occurs when a method type or annotation is not recognized by the plugin
            }
        }

        return resourceMap;
    }
}
