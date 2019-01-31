package com.github.litttlefisher.swagger.docgen.mavenplugin.properties;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;

import lombok.Data;

/**
 * @author jinyn22648
 * @version $$Id: SwaggerToMarkupProperty.java, v 0.1 2019/1/31 3:32 PM jinyn22648 Exp $$
 */
@Data
public class SwaggerToMarkupProperty {

    /**
     * adoc生成文件存放位置
     */
    @Parameter
    private File outputDir;
    /**
     * 该参数参考swagger2markup配置
     */
    @Parameter
    protected Map<String, String> config = new HashMap<>();
}
