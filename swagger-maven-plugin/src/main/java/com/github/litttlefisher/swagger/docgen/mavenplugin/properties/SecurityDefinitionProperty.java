package com.github.litttlefisher.swagger.docgen.mavenplugin.properties;

import lombok.Data;

/**
 * @author jinyn22648
 * @version $$Id: SecurityDefinitionProperty.java, v 0.1 2019/1/31 2:06 PM jinyn22648 Exp $$
 */
@Data
public class SecurityDefinitionProperty {
    private String name;
    private String type;
    private String in;
    private String description;
    private String json;
    private String jsonPath;
}
