package com.github.litttlefisher.swagger.docgen.reader;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.core.annotation.AnnotationUtils;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.converter.ModelConverter;
import io.swagger.converter.ModelConverterContext;
import io.swagger.jackson.ModelResolver;
import io.swagger.models.Model;
import io.swagger.models.properties.Property;
import lombok.extern.slf4j.Slf4j;

/**
 * @author littlefisher
 */
@Slf4j
public class ModelModifier extends ModelResolver {

    /** 替代类 */
    private Map<JavaType, JavaType> modelSubstitutes = new HashMap<>();
    private List<String> apiModelPropertyAccessExclusions = new ArrayList<>();

    public ModelModifier(ObjectMapper mapper) {
        super(mapper);
    }

    public void addModelSubstitute(String fromClass, String toClass) {
        JavaType type = null;
        JavaType toType = null;
        try {
            type = _mapper.constructType(Class.forName(fromClass));
        } catch (ClassNotFoundException e) {
            log.warn(String
                .format("Problem with loading class: %s. Mapping from: %s to: %s will be ignored.", fromClass,
                    fromClass, toClass));
        }
        try {
            toType = _mapper.constructType(Class.forName(toClass));
        } catch (ClassNotFoundException e) {
            log.warn(String
                .format("Problem with loading class: %s. Mapping from: %s to: %s will be ignored.", toClass, fromClass,
                    toClass));
        }
        if (type != null && toType != null) {
            modelSubstitutes.put(type, toType);
        }
    }

    public List<String> getApiModelPropertyAccessExclusions() {
        return apiModelPropertyAccessExclusions;
    }

    public void setApiModelPropertyAccessExclusions(List<String> apiModelPropertyAccessExclusions) {
        this.apiModelPropertyAccessExclusions = apiModelPropertyAccessExclusions;
    }

    @Override
    public Property resolveProperty(Type type, ModelConverterContext context, Annotation[] annotations,
        Iterator<ModelConverter> chain) {
        // for method parameter types we get here Type but we need JavaType
        JavaType javaType = toJavaType(type);
        if (modelSubstitutes.containsKey(javaType)) {
            return super.resolveProperty(modelSubstitutes.get(javaType), context, annotations, chain);
        } else if (chain.hasNext()) {
            return chain.next().resolveProperty(type, context, annotations, chain);
        } else {
            return super.resolveProperty(type, context, annotations, chain);
        }

    }

    @Override
    public Model resolve(Type type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        // for method parameter types we get here Type but we need JavaType
        JavaType javaType = toJavaType(type);
        if (modelSubstitutes.containsKey(javaType)) {
            return super.resolve(modelSubstitutes.get(javaType), context, chain);
        } else {
            return super.resolve(type, context, chain);
        }
    }

    @Override
    public Model resolve(JavaType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Model model = super.resolve(type, context, chain);

        // 如果没有@ApiModelProperty需要排除的配置，则直接返回
        if (CollectionUtils.isEmpty(apiModelPropertyAccessExclusions)) {
            return model;
        }

        Class<?> cls = type.getRawClass();

        for (Method method : cls.getDeclaredMethods()) {
            ApiModelProperty apiModelPropertyAnnotation = AnnotationUtils.findAnnotation(method,
                ApiModelProperty.class);

            processProperty(apiModelPropertyAnnotation, model);
        }

        for (Field field : FieldUtils.getAllFields(cls)) {
            ApiModelProperty apiModelPropertyAnnotation = AnnotationUtils.getAnnotation(field, ApiModelProperty.class);

            processProperty(apiModelPropertyAnnotation, model);
        }

        return model;
    }

    /**
     * Remove property from {@link Model} for provided {@link ApiModelProperty}.
     *
     * @param apiModelPropertyAnnotation annotation
     * @param model model with properties
     */
    private void processProperty(ApiModelProperty apiModelPropertyAnnotation, Model model) {
        if (apiModelPropertyAnnotation == null) {
            return;
        }

        String apiModelPropertyAccess = apiModelPropertyAnnotation.access();
        String apiModelPropertyName = apiModelPropertyAnnotation.name();

        // 如果@ApiModelProperty未同时配置#name和#access，则过滤
        if (apiModelPropertyAccess.isEmpty() || apiModelPropertyName.isEmpty()) {
            return;
        }

        // Check to see if the value of @ApiModelProperty#access is one to exclude.
        // If so, remove it from the previously-calculated model.
        if (apiModelPropertyAccessExclusions.contains(apiModelPropertyAccess)) {
            model.getProperties().remove(apiModelPropertyName);
        }
    }

    /**
     * Converts {@link Type} to {@link JavaType}.
     *
     * @param type object to convert
     * @return object converted to {@link JavaType}
     */
    private JavaType toJavaType(Type type) {
        JavaType typeToFind;
        if (type instanceof JavaType) {
            typeToFind = (JavaType) type;
        } else {
            typeToFind = _mapper.constructType(type);
        }
        return typeToFind;
    }
}
