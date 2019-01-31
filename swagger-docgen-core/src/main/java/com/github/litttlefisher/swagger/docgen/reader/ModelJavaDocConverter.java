package com.github.litttlefisher.swagger.docgen.reader;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.javadoc.ClassDoc;
import com.sun.tools.javadoc.ClassDocImpl;

import io.swagger.converter.ModelConverter;
import io.swagger.converter.ModelConverterContext;
import io.swagger.jackson.ModelResolver;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.properties.Property;

/**
 * @author jinyn22648
 * @version $$Id: ModelJavaDocConverter.java, v 0.1 2019/1/27 10:47 AM jinyn22648 Exp $$
 */
public class ModelJavaDocConverter extends ModelResolver {

    public ModelJavaDocConverter(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    public Model resolve(JavaType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Model model = super.resolve(type, context, chain);
        ClassDoc classJavaDoc = JavaDocReader.getClassJavaDoc(type.getRawClass().getCanonicalName());
        if (classJavaDoc != null) {
            if (classJavaDoc instanceof ClassDocImpl) {
                ClassDocImpl classDocImpl = (ClassDocImpl) classJavaDoc;
                String commentText = classDocImpl.commentText();
                if (StringUtils.isNoneBlank(commentText) && model instanceof ModelImpl) {
                    ModelImpl modelImpl = (ModelImpl) model;
                    modelImpl.setName(commentText);
                }
            }

            Map<String, Property> properties = model.getProperties();
            for (String propertyName : properties.keySet()) {
                Property property = properties.get(propertyName);
                Arrays.stream(classJavaDoc.fields(false)).filter(
                    fieldJavaDoc -> propertyName.equals(fieldJavaDoc.name())).findFirst().ifPresent(
                    fieldDoc -> property.setDescription(fieldDoc.commentText()));
            }
        }
        return model;
    }
}
