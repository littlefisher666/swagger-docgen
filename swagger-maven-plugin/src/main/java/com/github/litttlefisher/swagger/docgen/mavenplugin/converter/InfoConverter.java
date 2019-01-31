package com.github.litttlefisher.swagger.docgen.mavenplugin.converter;

import com.github.litttlefisher.swagger.docgen.mavenplugin.properties.InfoProperty;

import io.swagger.models.Info;

/**
 * @author jinyn22648
 * @version $$Id: InfoConverter.java, v 0.1 2019/1/31 2:05 PM jinyn22648 Exp $$
 */
public class InfoConverter {
    private InfoConverter() {
    }

    public static Info convert(InfoProperty property) {
        if (property == null) {
            return null;
        } else {
            Info info = new Info();
            info.setVersion(property.getVersion());
            info.setVendorExtensions(property.getVendorExtensions());
            info.setTitle(property.getTitle());
            info.setTermsOfService(property.getTermsOfService());
            info.setLicense(LicenseConverter.convert(property.getLicense()));
            info.setDescription(property.getDescription());
            info.setContact(ContactConverter.convert(property.getContact()));
            return info;
        }
    }
}
