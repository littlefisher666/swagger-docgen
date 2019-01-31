package com.github.litttlefisher.swagger.docgen.document;

import com.github.jknack.handlebars.io.TemplateLoader;

import lombok.Data;

/**
 * @author jinyn22648
 * @version $$Id: TemplatePath.java, v 0.1 2019/1/31 11:06 AM jinyn22648 Exp $$
 */
@Data
public class TemplatePath {

    private TemplateLoader loader;
    private String prefix;
    private String name;
    private String suffix;
}
