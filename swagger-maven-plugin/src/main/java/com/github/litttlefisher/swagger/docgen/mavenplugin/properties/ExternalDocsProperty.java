package com.github.litttlefisher.swagger.docgen.mavenplugin.properties;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Data;

/**
 * @author jinyn22648
 * @version $$Id: ExternalDocsProperty.java, v 0.1 2019/1/31 2:07 PM jinyn22648 Exp $$
 */
@Data
public class ExternalDocsProperty {
    /**
     * A short description of the target documentation. GFM syntax can be used for rich text representation.
     */
    private String description;

    /**
     * Required. The URL for the target documentation. Value MUST be in the format of a URL.
     */
    private String url;

    private Map<String, Object> vendorExtensions = new LinkedHashMap<String, Object>();
}
