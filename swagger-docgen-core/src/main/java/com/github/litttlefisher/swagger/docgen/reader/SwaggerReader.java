package com.github.litttlefisher.swagger.docgen.reader;

import java.util.Set;

import io.swagger.jaxrs.Reader;
import io.swagger.models.Swagger;

/**
 * This API reader is directly using the swagger internal {@link Reader} to scan the classes.
 * This reader is used when the exact output as the runtime generated swagger file is necessary.
 *
 * @author littlefisher
 */
public class SwaggerReader extends AbstractReader implements ClassSwaggerReader {

    public SwaggerReader(Swagger swagger) {
        super(swagger);
    }

    @Override
    public Swagger read(Set<Class<?>> classes) {
        return new Reader(swagger).read(classes);
    }

}
