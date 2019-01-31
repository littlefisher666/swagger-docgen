package com.github.litttlefisher.swagger.docgen.mavenplugin.converter;

import com.github.litttlefisher.swagger.docgen.mavenplugin.properties.LicenseProperty;

import io.swagger.models.License;

/**
 * @author jinyn22648
 * @version $$Id: LicenseConverter.java, v 0.1 2019/1/31 2:11 PM jinyn22648 Exp $$
 */
public class LicenseConverter {

    private LicenseConverter() {
    }

    public static License convert(LicenseProperty property) {
        if (property == null) {
            return null;
        } else {
            License license = new License();
            license.setUrl(property.getUrl());
            license.setName(property.getName());
            license.setVendorExtensions(property.getVendorExtensions());
            return license;
        }
    }
}
