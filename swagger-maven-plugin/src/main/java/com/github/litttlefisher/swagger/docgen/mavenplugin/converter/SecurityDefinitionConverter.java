package com.github.litttlefisher.swagger.docgen.mavenplugin.converter;

import com.github.litttlefisher.swagger.docgen.document.SecurityDefinition;
import com.github.litttlefisher.swagger.docgen.mavenplugin.properties.SecurityDefinitionProperty;

/**
 * @author jinyn22648
 * @version $$Id: SecurityDefinitionConverter.java, v 0.1 2019/1/31 2:05 PM jinyn22648 Exp $$
 */
public class SecurityDefinitionConverter {
    private SecurityDefinitionConverter() {
    }

    public static SecurityDefinition convert(SecurityDefinitionProperty property) {
        if (property == null) {
            return null;
        } else {
            SecurityDefinition definition = new SecurityDefinition();
            definition.setDescription(property.getDescription());
            definition.setIn(property.getIn());
            definition.setJson(property.getJson());
            definition.setJsonPath(property.getJsonPath());
            definition.setName(property.getName());
            definition.setType(property.getType());
            return definition;
        }
    }
}
