package com.github.litttlefisher.swagger.docgen.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;

import io.swagger.annotations.ApiModel;

/**
 * 自定义一个jackson的module用于解析{@link ApiModel}注解
 *
 * @author littlefisher
 */
public class EnhancedSwaggerModule extends SimpleModule {

    private static final long serialVersionUID = 8532634886214113537L;

    public EnhancedSwaggerModule() {
        super("1.0.0");
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.insertAnnotationIntrospector(new EnhancedSwaggerAnnotationIntrospector());
    }
}
