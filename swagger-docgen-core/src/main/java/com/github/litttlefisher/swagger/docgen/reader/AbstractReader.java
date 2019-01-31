package com.github.litttlefisher.swagger.docgen.reader;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.BeanParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.apache.commons.lang3.text.StrBuilder;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;

import com.github.litttlefisher.swagger.docgen.constant.SymbolConstant;
import com.github.litttlefisher.swagger.docgen.util.TypeExtracter;
import com.github.litttlefisher.swagger.docgen.util.TypeWithAnnotations;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.jersey.api.core.InjectParam;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.AuthorizationScope;
import io.swagger.annotations.ResponseHeader;
import io.swagger.converter.ModelConverters;
import io.swagger.jaxrs.ext.SwaggerExtension;
import io.swagger.jaxrs.ext.SwaggerExtensions;
import io.swagger.models.ArrayModel;
import io.swagger.models.Model;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.RefModel;
import io.swagger.models.Response;
import io.swagger.models.Scheme;
import io.swagger.models.SecurityRequirement;
import io.swagger.models.Swagger;
import io.swagger.models.Tag;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.FormParameter;
import io.swagger.models.parameters.HeaderParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.PathParameter;
import io.swagger.models.parameters.QueryParameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.util.ParameterProcessor;
import io.swagger.util.PathUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * @author littlefisher
 */
@Data
@Slf4j
public abstract class AbstractReader {
    /**
     * Supported parameters: {{packageName}}, {{className}}, {{methodName}}, {{httpMethod}}
     * Suggested default value is: "{{className}}_{{methodName}}_{{httpMethod}}"
     */
    public static final String OPERATION_ID_FORMAT_DEFAULT = "{{methodName}}";
    /** {@link ResponseHeader#responseContainer()}所支持的容器类型-List */
    private static final String RESPONSE_HEADER_CONTAINER_LIST = "List";
    /** {@link ResponseHeader#responseContainer()}所支持的容器类型-Set */
    private static final String RESPONSE_HEADER_CONTAINER_SET = "Set";
    /** 方法参数上有效的注解类型 */
    private static final List<Type> VALID_PARAMETER_ANNOTATIONS = Lists.newArrayList(ModelAttribute.class,
        BeanParam.class, InjectParam.class, ApiParam.class, PathParam.class, QueryParam.class, HeaderParam.class,
        FormParam.class, RequestParam.class, RequestBody.class, PathVariable.class, RequestHeader.class,
        RequestPart.class, CookieValue.class);
    protected Swagger swagger;
    protected String operationIdFormat;
    private Set<Type> typesToSkip = new HashSet<>();

    /** 是否解析javadoc来配置描述 */
    private boolean javadocEnabled;

    public AbstractReader(Swagger swagger) {
        this.swagger = swagger;
        updateExtensionChain();
    }

    public void setTypesToSkip(List<Type> typesToSkip) {
        this.typesToSkip = new HashSet<>(typesToSkip);
    }

    public void addTypeToSkippedTypes(Type type) {
        this.typesToSkip.add(type);
    }

    /**
     * swagger扩展点
     */
    protected void updateExtensionChain() {
        // default implementation does nothing
    }

    /**
     * 解析权限控制配置
     *
     * @param api 注解
     * @return 权限控制配置
     */
    protected List<SecurityRequirement> getSecurityRequirements(Api api) {
        List<SecurityRequirement> securities = new ArrayList<>();
        if (api == null) {
            return securities;
        }

        for (Authorization auth : api.authorizations()) {
            if (auth.value().isEmpty()) {
                continue;
            }
            SecurityRequirement security = new SecurityRequirement();
            List<String> scopes = Arrays.stream(auth.scopes()).filter(input -> !input.scope().isEmpty()).map(
                AuthorizationScope::scope).collect(Collectors.toList());
            security.requirement(auth.value(), scopes);

            securities.add(security);
        }
        return securities;
    }

    /**
     * 对api路径进行处理，去除例如冒号等内容，获得最原始的api路径，具体冒号等符号的作用，请自行查询
     *
     * @param operationPath api全路径
     * @param regexMap 缓存
     * @return 解析后的api路径
     */
    protected String parseOperationPath(String operationPath, Map<String, String> regexMap) {
        return PathUtils.parsePath(operationPath, regexMap);
    }

    protected void updateOperationParameters(List<Parameter> parentParameters, Map<String, String> regexMap,
        Operation operation) {
        if (parentParameters != null) {
            for (Parameter param : parentParameters) {
                operation.parameter(param);
            }
        }
        for (Parameter param : operation.getParameters()) {
            String pattern = regexMap.get(param.getName());
            if (pattern != null) {
                param.setPattern(pattern);
            }
        }
    }

    /**
     * 解析响应header
     *
     * @param headers 注解
     * @return 响应header
     */
    protected Map<String, Property> parseResponseHeaders(ResponseHeader[] headers) {
        if (headers == null) {
            return null;
        }
        Map<String, Property> responseHeaders = null;
        for (ResponseHeader header : headers) {
            if (header.name().isEmpty()) {
                continue;
            }
            if (responseHeaders == null) {
                responseHeaders = Maps.newHashMap();
            }
            Class<?> cls = header.response();

            if (!cls.equals(Void.class) && !cls.equals(void.class)) {
                Property property = ModelConverters.getInstance().readAsProperty(cls);
                if (property != null) {
                    Property responseProperty;

                    // 响应头容器类型
                    if (RESPONSE_HEADER_CONTAINER_LIST.equalsIgnoreCase(header.responseContainer())) {
                        responseProperty = new ArrayProperty(property);
                    } else if (RESPONSE_HEADER_CONTAINER_SET.equalsIgnoreCase(header.responseContainer())) {
                        responseProperty = new MapProperty(property);
                    } else {
                        responseProperty = property;
                    }
                    responseProperty.setDescription(header.description());
                    responseHeaders.put(header.name(), responseProperty);
                }
            }
        }
        return responseHeaders;
    }

    /**
     * 解析api路径
     *
     * @param operationPath operationPath
     * @param httpMethod 请求方式
     * @param operation 业务返回对象
     * @param method 方法
     */
    protected void updatePath(String operationPath, String httpMethod, Operation operation, Method method) {
        if (httpMethod == null) {
            return;
        }
        Path path = swagger.getPath(operationPath);
        if (path == null) {
            path = new Path();
            swagger.path(operationPath, path);
        }
        path.set(httpMethod, operation);
        operation.summary(getMethodSummary(null, method));
    }

    /**
     * 获取uri的描述
     *
     * @param apiOperationValue {@link ApiOperation#value()}值
     * @param method 方法
     * @return uri的描述
     */
    protected String getMethodSummary(String apiOperationValue, Method method) {
        String methodJavaDocComment = getMethodJavaDocFromSuper(method.getName(), method);
        if (isJavadocEnabled()) {
            if (StringUtils.isNotBlank(methodJavaDocComment)) {
                return methodJavaDocComment;
            } else {
                return StringUtils.isNotBlank(apiOperationValue) ? apiOperationValue :
                    method.getDeclaringClass().getSimpleName() + SymbolConstant.PERIOD + method.getName();
            }
        } else {
            return apiOperationValue;
        }
    }

    /**
     * 从父类或父接口中查找javadoc注释
     *
     * @param needMethodName 需要匹配的方法名
     * @param method 当前方法
     * @return javadoc注释
     */
    private String getMethodJavaDocFromSuper(String needMethodName, Method method) {
        // 查询当前方法是否有javadoc注释
        String returnComment = getMethodJavaDoc(method);
        if (StringUtils.isNotBlank(returnComment) && method.getName().equals(needMethodName)) {
            return returnComment;
        }
        // 是否有父类或父接口 true-没有父类或父接口
        boolean notHasSuper =
            (method.getDeclaringClass().getSuperclass() == null || method.getDeclaringClass().getSuperclass().equals(
                Object.class)) && ArrayUtils.isEmpty(method.getDeclaringClass().getInterfaces());
        if (notHasSuper) {
            return null;
        } else {
            if (!method.getDeclaringClass().getSuperclass().equals(Object.class)) {
                // 递归父类
                for (Method declaredMethod : method.getDeclaringClass().getSuperclass().getDeclaredMethods()) {
                    MethodJavadoc methodJavadoc = new MethodJavadoc(needMethodName, declaredMethod).invoke();
                    if (methodJavadoc.is()) {
                        return returnComment;
                    }
                    returnComment = methodJavadoc.getReturnComment();
                }
            }
            // 如果父类都没有javadoc，则递归查询接口中是否有
            if (StringUtils.isBlank(returnComment) && ArrayUtils.isNotEmpty(
                method.getDeclaringClass().getInterfaces())) {
                for (Class<?> anInterface : method.getDeclaringClass().getInterfaces()) {
                    for (Method declaredMethod : anInterface.getDeclaredMethods()) {
                        MethodJavadoc methodJavadoc = new MethodJavadoc(needMethodName, declaredMethod).invoke();
                        if (methodJavadoc.is()) {
                            return returnComment;
                        }
                    }
                }
            }
            return returnComment;
        }
    }

    /**
     * 根据方法找到该方法的javadoc
     *
     * @param method 方法
     * @return javadoc注释
     */
    private String getMethodJavaDoc(Method method) {
        ClassDoc classJavaDoc = JavaDocReader.getClassJavaDoc(method.getDeclaringClass().getCanonicalName());
        if (classJavaDoc != null) {
            MethodDoc methodDoc = Arrays.stream(classJavaDoc.methods(false)).filter(
                input -> input.name().equals(method.getName())).findFirst().orElse(null);
            if (methodDoc != null) {
                return methodDoc.commentText();
            }
        }
        return null;
    }

    /**
     * 解析tag
     *
     * @param operation 业务返回对象
     * @param apiOperation {@link ApiOperation}对象
     */
    protected void parserTagsForOperation(Operation operation, ApiOperation apiOperation) {
        if (apiOperation == null) {
            return;
        }
        for (String tag : apiOperation.tags()) {
            if (!tag.isEmpty()) {
                operation.tag(tag);
                swagger.tag(new Tag().name(tag));
            }
        }
    }

    /**
     * {@link Api} 注解是否需要隐藏
     *
     * @param readHidden 是否隐藏
     * @param api 注解
     * @return true-隐藏，false-不隐藏
     */
    protected boolean canReadApi(boolean readHidden, Api api) {
        return (api == null) || readHidden || !api.hidden();
    }

    /**
     * 解析Tag注解
     *
     * @param api api参数
     * @return Tag列表
     */
    protected Set<Tag> extractTags(Api api) {
        Set<Tag> output = new LinkedHashSet<>();
        if (api == null) {
            return output;
        }

        boolean hasExplicitTags = false;
        for (String tag : api.tags()) {
            if (!tag.isEmpty()) {
                hasExplicitTags = true;
                output.add(new Tag().name(tag));
            }
        }
        if (!hasExplicitTags) {
            // derive tag from api path + description
            String tagString = api.value().replace("/", "");
            if (!tagString.isEmpty()) {
                Tag tag = new Tag().name(tagString);
                output.add(tag);
            }
        }
        return output;
    }

    /**
     * 解析方法的请求类型，是http还是https等等
     *
     * @param apiOperation {@link ApiOperation}注解
     * @param operation 返回参数信息
     */
    protected void parserOperationProtocols(ApiOperation apiOperation, Operation operation) {
        if (apiOperation == null) {
            return;
        }
        String[] protocols = apiOperation.protocols().split(",");
        for (String protocol : protocols) {
            String trimmed = protocol.trim();
            if (!trimmed.isEmpty()) {
                operation.scheme(Scheme.forValue(trimmed));
            }
        }
    }

    protected Map<String, Tag> updateTagsForApi(Map<String, Tag> parentTags, Api api) {
        // the value will be used as a tag for 2.0 UNLESS a Tags annotation is present
        Map<String, Tag> tagsMap = Maps.newHashMap();
        for (Tag tag : extractTags(api)) {
            tagsMap.put(tag.getName(), tag);
        }
        if (parentTags != null) {
            tagsMap.putAll(parentTags);
        }
        for (Tag tag : tagsMap.values()) {
            swagger.tag(tag);
        }
        return tagsMap;
    }

    protected boolean isPrimitive(Type cls) {
        return com.github.litttlefisher.swagger.docgen.util.TypeUtils.isPrimitive(cls);
    }

    /**
     * 重新更新一下{@link Operation}
     *
     * @param apiConsumes consumes
     * @param apiProduces produces
     * @param tags tags
     * @param securities securities
     * @param operation {@link Operation}
     */
    protected void updateOperation(String[] apiConsumes, String[] apiProduces, Map<String, Tag> tags,
        List<SecurityRequirement> securities, Operation operation) {
        if (operation == null) {
            return;
        }
        if (operation.getConsumes() == null) {
            for (String mediaType : apiConsumes) {
                operation.consumes(mediaType);
            }
        }
        if (operation.getProduces() == null) {
            for (String mediaType : apiProduces) {
                operation.produces(mediaType);
            }
        }

        if (operation.getTags() == null) {
            for (String tagString : tags.keySet()) {
                operation.tag(tagString);
            }
        }
        for (SecurityRequirement security : securities) {
            operation.security(security);
        }
    }

    /**
     * 校验参数是否加了{@link ApiParam}注解，且设置hidden参数为true
     *
     * @param parameterAnnotations 参数上的注解
     * @return true-要过滤，false-不要过滤
     */
    private boolean isApiParamHidden(List<Annotation> parameterAnnotations) {
        for (Annotation parameterAnnotation : parameterAnnotations) {
            if (parameterAnnotation instanceof ApiParam) {
                return ((ApiParam) parameterAnnotation).hidden();
            }
        }

        return false;
    }

    /**
     * 校验参数上的注解是否是有用注解
     *
     * @param parameterAnnotations 参数上的注解
     * @return true-有用的，false-无用的
     */
    private boolean hasValidAnnotations(List<Annotation> parameterAnnotations) {
        return parameterAnnotations.stream().anyMatch(
            potentialAnnotation -> VALID_PARAMETER_ANNOTATIONS.contains(potentialAnnotation.annotationType()));
    }

    /**
     * 解析方法入参
     *
     * @param type 方法的入参
     * @param annotations 该参数上的注解
     * @return 组装成 {@link Parameter}类型
     */
    protected final List<Parameter> getParameters(Type type, List<Annotation> annotations) {
        return getParameters(type, annotations, typesToSkip);
    }

    /**
     * 解析方法入参
     *
     * @param type 方法的入参
     * @param annotations 该参数上的注解
     * @param typesToSkip 需要过滤掉的入参类型
     * @return 组装成 {@link Parameter}类型
     */
    protected List<Parameter> getParameters(Type type, List<Annotation> annotations, Set<Type> typesToSkip) {
        if (!hasValidAnnotations(annotations) || isApiParamHidden(annotations)) {
            return Collections.emptyList();
        }

        Iterator<SwaggerExtension> chain = SwaggerExtensions.chain();
        List<Parameter> parameters = new ArrayList<>();
        Class<?> cls = TypeUtils.getRawType(type, type);
        log.debug("Looking for path/query/header/form/cookie params in " + cls);

        if (chain.hasNext()) {
            SwaggerExtension extension = chain.next();
            log.debug("trying extension " + extension);
            parameters = extension.extractParameters(annotations, type, typesToSkip, chain);
        }

        if (!parameters.isEmpty()) {
            for (Parameter parameter : parameters) {
                ParameterProcessor.applyAnnotations(swagger, parameter, type, annotations);
            }
        } else {
            log.debug("Looking for body params in " + cls);
            // parameters is guaranteed to be empty at this point, replace it with a mutable collection
            parameters = Lists.newArrayList();
            if (!typesToSkip.contains(type)) {
                Parameter param = ParameterProcessor.applyAnnotations(swagger, null, type, annotations);
                if (param != null) {
                    parameters.add(param);
                }
            }
        }
        return parameters;
    }

    /**
     * 解析{@link ApiResponses}注解
     *
     * @param operation 存储对象
     * @param responseAnnotation {@link ApiResponses}注解对象
     */
    protected void parserApiResponse(Operation operation, ApiResponses responseAnnotation) {
        for (ApiResponse apiResponse : responseAnnotation.value()) {
            Map<String, Property> responseHeaders = parseResponseHeaders(apiResponse.responseHeaders());
            Class<?> responseClass = apiResponse.response();
            Response response = new Response().description(apiResponse.message()).headers(responseHeaders);

            if (responseClass.equals(Void.class)) {
                if (operation.getResponses() != null) {
                    Response apiOperationResponse = operation.getResponses().get(String.valueOf(apiResponse.code()));
                    if (apiOperationResponse != null) {
                        response.setResponseSchema(apiOperationResponse.getResponseSchema());
                    }
                }
            } else {
                Map<String, Model> models = ModelConverters.getInstance().read(responseClass);
                for (String key : models.keySet()) {
                    if (RESPONSE_HEADER_CONTAINER_LIST.equals(apiResponse.responseContainer())) {
                        response.setResponseSchema(new ArrayModel().items(new RefProperty().asDefault(key)));
                    } else {
                        response.setResponseSchema(new RefModel().asDefault(key));
                    }
                    swagger.model(key, models.get(key));
                }
                models = ModelConverters.getInstance().readAll(responseClass);
                models.forEach((key, value) -> swagger.model(key, value));

                if (response.getResponseSchema() == null) {
                    Map<String, Response> responses = operation.getResponses();
                    if (responses != null) {
                        Response apiOperationResponse = responses.get(String.valueOf(apiResponse.code()));
                        if (apiOperationResponse != null) {
                            response.setResponseSchema(apiOperationResponse.getResponseSchema());
                        }
                    }
                }
            }

            if (apiResponse.code() == 0) {
                operation.defaultResponse(response);
            } else {
                operation.response(apiResponse.code(), response);
            }
        }
    }

    /**
     * 解析方法上的producer
     *
     * @param parentProduces 父
     * @param apiProduces 子
     * @param operation 返回信息对象
     * @return producers
     */
    protected String[] parserOperationProduces(String[] parentProduces, String[] apiProduces, Operation operation) {
        if (parentProduces != null) {
            Set<String> both = Sets.newLinkedHashSet(Arrays.asList(apiProduces));
            both.addAll(Arrays.asList(parentProduces));
            if (operation.getProduces() != null) {
                both.addAll(operation.getProduces());
            }
            apiProduces = both.toArray(new String[0]);
        }
        return apiProduces;
    }

    /**
     * 解析方法上的consume
     *
     * @param parentConsumes 父
     * @param apiConsumes 子
     * @param operation 返回信息对象
     * @return consumes
     */
    protected String[] parserOperationConsumes(String[] parentConsumes, String[] apiConsumes, Operation operation) {
        if (parentConsumes != null) {
            Set<String> both = Sets.newLinkedHashSet(Arrays.asList(apiConsumes));
            both.addAll(Arrays.asList(parentConsumes));
            if (operation.getConsumes() != null) {
                both.addAll(operation.getConsumes());
            }
            apiConsumes = both.toArray(new String[0]);
        }
        return apiConsumes;
    }

    /**
     * 解析{@link ApiImplicitParams}注解
     *
     * @param method 方法
     * @param operation 返回的参数
     */
    protected void readImplicitParameters(Method method, Operation operation) {
        ApiImplicitParams implicitParams = AnnotationUtils.findAnnotation(method, ApiImplicitParams.class);
        if (implicitParams == null) {
            return;
        }
        for (ApiImplicitParam param : implicitParams.value()) {
            Class<?> cls;
            try {
                cls = Class.forName(param.dataType());
            } catch (ClassNotFoundException e) {
                cls = method.getDeclaringClass();
            }

            Parameter p = readImplicitParam(param, cls);
            if (p != null) {
                operation.addParameter(p);
            }
        }
    }

    protected Parameter readImplicitParam(ApiImplicitParam param, Class<?> apiClass) {
        Parameter parameter;
        if ("path".equalsIgnoreCase(param.paramType())) {
            parameter = new PathParameter();
        } else if ("query".equalsIgnoreCase(param.paramType())) {
            parameter = new QueryParameter();
        } else if ("form".equalsIgnoreCase(param.paramType()) || "formData".equalsIgnoreCase(param.paramType())) {
            parameter = new FormParameter();
        } else if ("body".equalsIgnoreCase(param.paramType())) {
            parameter = new BodyParameter();
        } else if ("header".equalsIgnoreCase(param.paramType())) {
            parameter = new HeaderParameter();
        } else {
            return null;
        }

        return ParameterProcessor.applyAnnotations(swagger, parameter, apiClass,
            Arrays.asList(new Annotation[] {param}));
    }

    void processOperationDecorator(Operation operation, Method method) {
        final Iterator<SwaggerExtension> chain = SwaggerExtensions.chain();
        if (chain.hasNext()) {
            SwaggerExtension extension = chain.next();
            extension.decorateOperation(operation, method, chain);
        }
    }

    /**
     * 根据传入的格式来进行替换，生成操作id
     *
     * @param method 方法
     * @param httpMethod 请求类型
     * @return 操作id
     */
    protected String getOperationId(Method method, String httpMethod) {
        if (this.operationIdFormat == null) {
            this.operationIdFormat = OPERATION_ID_FORMAT_DEFAULT;
        }

        String packageName = method.getDeclaringClass().getPackage().getName();
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();

        StrBuilder sb = new StrBuilder(this.operationIdFormat);
        sb.replaceAll("{{packageName}}", packageName);
        sb.replaceAll("{{className}}", className);
        sb.replaceAll("{{methodName}}", methodName);
        sb.replaceAll("{{httpMethod}}", httpMethod);

        return sb.toString();
    }

    public List<Parameter> extractTypes(Class<?> cls, Set<Type> typesToSkip, List<Annotation> additionalAnnotations) {
        TypeExtracter extractor = new TypeExtracter();
        Collection<TypeWithAnnotations> typesWithAnnotations = extractor.extractTypes(cls);

        List<Parameter> output = new ArrayList<>();
        for (TypeWithAnnotations typeWithAnnotations : typesWithAnnotations) {

            Type type = typeWithAnnotations.getType();
            List<Annotation> annotations = new ArrayList<>(additionalAnnotations);
            annotations.addAll(typeWithAnnotations.getAnnotations());

            /*
             * Skip the type of the bean itself when recursing into its members
             * in order to avoid a cycle (stack overflow), as crazy as that user
             * code would have to be.
             *
             * There are no tests to prove this works because the test bean
             * classes are shared with SwaggerReaderTest and Swagger's own logic
             * doesn't prevent this problem.
             */
            Set<Type> recurseTypesToSkip = new HashSet<Type>(typesToSkip);
            recurseTypesToSkip.add(cls);

            output.addAll(this.getParameters(type, annotations, recurseTypesToSkip));
        }

        return output;
    }

    private class MethodJavadoc {
        private boolean result;
        private String needMethodName;
        private Method declaredMethod;
        private String returnComment;

        public MethodJavadoc(String needMethodName, Method declaredMethod) {
            this.needMethodName = needMethodName;
            this.declaredMethod = declaredMethod;
        }

        boolean is() {return result;}

        public String getReturnComment() {
            return returnComment;
        }

        public MethodJavadoc invoke() {
            if (declaredMethod.getName().equals(needMethodName)) {
                returnComment = getMethodJavaDoc(declaredMethod);
                if (StringUtils.isNotBlank(returnComment)) {
                    result = true;
                    return this;
                }
            } else {
                returnComment = getMethodJavaDocFromSuper(needMethodName, declaredMethod);
                if (StringUtils.isNotBlank(returnComment)) {
                    result = true;
                    return this;
                }
            }
            result = false;
            return this;
        }
    }
}

