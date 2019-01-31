package com.github.litttlefisher.swagger.docgen.document;

import java.util.Set;

import javax.ws.rs.Path;

import com.github.litttlefisher.swagger.docgen.exception.GenerateException;
import com.github.litttlefisher.swagger.docgen.reader.AbstractReader;
import com.github.litttlefisher.swagger.docgen.reader.ClassSwaggerReader;
import com.github.litttlefisher.swagger.docgen.reader.JaxrsReader;
import com.google.common.collect.Sets;

/**
 * 扫描JSR311-api相关注解来生成swagger
 *
 * @author littlefisher
 */
public class JaxrsDocumentSource extends AbstractDocumentSource {

    public JaxrsDocumentSource(ApiSource apiSource, String encoding) {
        super(apiSource);
        if (encoding != null) {
            this.encoding = encoding;
        }
    }

    @Override
    protected Set<Class<?>> getValidClasses() {
        return Sets.union(super.getValidClasses(), apiSource.getValidClasses(Path.class));
    }

    @Override
    protected ClassSwaggerReader resolveApiReader() throws GenerateException {
        String customReaderClassName = apiSource.getSwaggerApiReader();
        if (customReaderClassName == null) {
            JaxrsReader reader = new JaxrsReader(swagger);
            reader.setTypesToSkip(this.typesToSkip);
            reader.setOperationIdFormat(this.apiSource.getOperationIdFormat());
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
