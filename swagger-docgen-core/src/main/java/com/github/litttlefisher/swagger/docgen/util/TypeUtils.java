package com.github.litttlefisher.swagger.docgen.util;

import java.lang.reflect.Type;
import java.util.List;

import com.google.common.collect.Lists;

import io.swagger.converter.ModelConverters;
import io.swagger.models.properties.Property;

/**
 * @author littlefisher
 */
public class TypeUtils {

    /** 基础数据类型 */
    private static final List<String> PRIMITIVE = Lists.newArrayList("integer", "string", "number", "boolean", "array",
        "file");

    /**
     * 是否是基础数据类型
     *
     * @param cls 要校验的类
     * @return true-是，false-不是
     */
    public static boolean isPrimitive(Type cls) {

        Property property = ModelConverters.getInstance().readAsProperty(cls);
        return PRIMITIVE.stream().anyMatch(input -> input.equals(property.getType()));
    }
}
