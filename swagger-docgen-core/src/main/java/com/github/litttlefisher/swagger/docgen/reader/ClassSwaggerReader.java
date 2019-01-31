package com.github.litttlefisher.swagger.docgen.reader;

import java.util.Set;

import com.github.litttlefisher.swagger.docgen.exception.GenerateException;

import io.swagger.models.Swagger;

/**
 * swagger解析器
 *
 * @author littlefisher
 */
public interface ClassSwaggerReader {
    /**
     * 解析class生成swagger配置
     *
     * @param classes 要解析的class类
     * @return {@link Swagger}
     * @throws GenerateException 异常
     */
    Swagger read(Set<Class<?>> classes) throws GenerateException;
}
