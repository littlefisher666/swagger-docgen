package com.github.litttlefisher.swagger.docgen.mavenplugin.properties;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Data;

/**
 * @author jinyn22648
 * @version $$Id: LicenseProperty.java, v 0.1 2019/1/31 2:08 PM jinyn22648 Exp $$
 */
@Data
public class LicenseProperty {

    private Map<String, Object> vendorExtensions = new LinkedHashMap<String, Object>();
    /** 名称 */
    private String name;
    /** url */
    private String url;
}
