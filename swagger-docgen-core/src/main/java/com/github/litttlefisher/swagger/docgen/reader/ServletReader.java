package com.github.litttlefisher.swagger.docgen.reader;

import java.util.Set;

import io.swagger.models.Swagger;
import io.swagger.servlet.Reader;

/**
 * A dedicated {@link ClassSwaggerReader} to scan Servlet classes.
 *
 * @author littlefisher
 */
public class ServletReader extends AbstractReader implements ClassSwaggerReader {

    public ServletReader(Swagger swagger) {
        super(swagger);
    }

    @Override
    public Swagger read(Set<Class<?>> classes) {
        Reader.read(swagger, classes);
        return swagger;
    }

}
