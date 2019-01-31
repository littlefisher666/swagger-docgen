package com.github.litttlefisher.swagger.docgen.mavenplugin.properties;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Data;

/**
 * @author jinyn22648
 * @version $$Id: InfoProperty.java, v 0.1 2019/1/31 2:06 PM jinyn22648 Exp $$
 */
@Data
public class InfoProperty {
    /** 描述 */
    private String description;
    /** 版本 */
    private String version;
    /** 标题 */
    private String title;
    /** 组织 */
    private String termsOfService;
    /** 联系人 */
    private ContactProperty contact;
    /** license */
    private LicenseProperty license;
    private Map<String, Object> vendorExtensions = new LinkedHashMap<String, Object>();
}
