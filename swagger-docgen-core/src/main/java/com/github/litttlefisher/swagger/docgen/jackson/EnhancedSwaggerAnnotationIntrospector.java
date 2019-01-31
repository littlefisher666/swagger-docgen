package com.github.litttlefisher.swagger.docgen.jackson;

import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;

import io.swagger.annotations.ApiModel;
import io.swagger.jackson.SwaggerAnnotationIntrospector;

/**
 * Extends SwaggerAnnotationIntrospector with {@link #findRootName(AnnotatedClass)} implementation. See
 * https://github.com/swagger-api/swagger-core/issues/2104
 *
 * @author littlefisher
 */
public class EnhancedSwaggerAnnotationIntrospector extends SwaggerAnnotationIntrospector {

    private static final long serialVersionUID = -2127572533951487520L;

    @Override
    public PropertyName findRootName(AnnotatedClass ac) {
        ApiModel model = ac.getAnnotation(ApiModel.class);
        if (model != null) {
            return new PropertyName(model.value());
        } else {
            return super.findRootName(ac);
        }
    }
}
