package com.github.litttlefisher.swagger.docgen.jaxrs;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ws.rs.BeanParam;

import org.apache.commons.lang3.reflect.TypeUtils;

import com.github.litttlefisher.swagger.docgen.reader.AbstractReader;
import com.github.litttlefisher.swagger.docgen.reader.JaxrsReader;
import com.google.common.collect.Lists;
import com.sun.jersey.api.core.InjectParam;
import com.sun.jersey.core.header.FormDataContentDisposition;

import io.swagger.jaxrs.ext.AbstractSwaggerExtension;
import io.swagger.jaxrs.ext.SwaggerExtension;
import io.swagger.models.parameters.Parameter;

/**
 * This extension extracts the parameters inside a {@code @BeanParam} by
 * expanding the target bean type's fields/methods/constructor parameters and
 * recursively feeding them back through the {@link JaxrsReader}.
 *
 * @author littlefisher
 */
public class BeanParamInjectParamExtension extends AbstractSwaggerExtension {

    private AbstractReader reader;

    public BeanParamInjectParamExtension(AbstractReader reader) {
        this.reader = reader;
    }

    @Override
    public List<Parameter> extractParameters(List<Annotation> annotations, Type type, Set<Type> typesToSkip,
        Iterator<SwaggerExtension> chain) {
        Class<?> cls = TypeUtils.getRawType(type, type);

        if (shouldIgnoreClass(cls) || typesToSkip.contains(type)) {
            // stop the processing chain
            typesToSkip.add(type);
            return Lists.newArrayList();
        }
        for (Annotation annotation : annotations) {
            if (annotation instanceof BeanParam || annotation instanceof InjectParam) {
                return reader.extractTypes(cls, typesToSkip, Lists.newArrayList());
            }
        }
        return super.extractParameters(annotations, type, typesToSkip, chain);
    }

    @Override
    public boolean shouldIgnoreClass(Class<?> cls) {
        return FormDataContentDisposition.class.equals(cls);
    }
}
