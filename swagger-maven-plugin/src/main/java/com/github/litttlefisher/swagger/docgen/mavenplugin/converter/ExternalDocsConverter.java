package com.github.litttlefisher.swagger.docgen.mavenplugin.converter;

import com.github.litttlefisher.swagger.docgen.mavenplugin.properties.ExternalDocsProperty;

import io.swagger.models.ExternalDocs;

/**
 * @author jinyn22648
 * @version $$Id: ExternalDocsConverter.java, v 0.1 2019/1/31 2:05 PM jinyn22648 Exp $$
 */
public class ExternalDocsConverter {
    private ExternalDocsConverter() {
    }

    public static ExternalDocs convert(ExternalDocsProperty property) {
        if (property == null) {
            return null;
        } else {
            ExternalDocs docs = new ExternalDocs();
            docs.setDescription(property.getDescription());
            docs.setUrl(property.getUrl());
            docs.setVendorExtensions(property.getVendorExtensions());
            return docs;
        }
    }
}
