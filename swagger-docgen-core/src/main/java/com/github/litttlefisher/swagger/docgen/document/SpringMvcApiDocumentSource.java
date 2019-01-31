package com.github.litttlefisher.swagger.docgen.document;

import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;

import com.github.litttlefisher.swagger.docgen.exception.GenerateException;
import com.github.litttlefisher.swagger.docgen.reader.AbstractReader;
import com.github.litttlefisher.swagger.docgen.reader.ClassSwaggerReader;
import com.github.litttlefisher.swagger.docgen.reader.SpringMvcApiReader;

/**
 * 扫描Spring Controller相关注解来生成swagger
 *
 * @author littlefisher
 */
public class SpringMvcApiDocumentSource extends AbstractDocumentSource {

    public SpringMvcApiDocumentSource(ApiSource apiSource, String encoding) {
        super(apiSource);
        if (encoding != null) {
            this.encoding = encoding;
        }
    }

    @Override
    protected Set<Class<?>> getValidClasses() {
        Set<Class<?>> result = super.getValidClasses();
        // 解析Controller注解
        result.addAll(apiSource.getValidClasses(Controller.class));
        result.addAll(apiSource.getValidClasses(RestController.class));
        result.addAll(apiSource.getValidClasses(ControllerAdvice.class));
        return result;
    }

    @Override
    protected ClassSwaggerReader resolveApiReader() throws GenerateException {
        String customReaderClassName = apiSource.getSwaggerApiReader();
        if (customReaderClassName == null) {
            SpringMvcApiReader reader = new SpringMvcApiReader(swagger);
            reader.setTypesToSkip(this.typesToSkip);
            reader.setOperationIdFormat(this.apiSource.getOperationIdFormat());
            reader.setJavadocEnabled(apiSource.isJavadocEnabled());
            return reader;
        } else {
            ClassSwaggerReader customApiReader = getCustomApiReader(customReaderClassName);
            if (customApiReader instanceof AbstractReader) {
                ((AbstractReader) customApiReader).setOperationIdFormat(this.apiSource.getOperationIdFormat());
            }
            return customApiReader;
        }
    }

}
