package com.github.litttlefisher.swagger.docgen.mavenplugin.converter;

import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;

import com.github.litttlefisher.swagger.docgen.document.ApiSource;
import com.github.litttlefisher.swagger.docgen.mavenplugin.properties.ApiSourceProperty;

/**
 * @author jinyn22648
 * @version $$Id: ApiSourceConverter.java, v 0.1 2019/1/31 2:17 PM jinyn22648 Exp $$
 */
public class ApiSourceConverter {

    private ApiSourceConverter() {
    }

    public static ApiSource convert(ApiSourceProperty property) {
        return property == null ? null : ApiSource.builder().apiModelPropertyAccessExclusions(
            property.getApiModelPropertyAccessExclusions()).apiSortComparator(property.getApiSortComparator())
            .attachSwaggerArtifact(property.isAttachSwaggerArtifact()).basePath(property.getBasePath()).descriptionFile(
                property.getDescriptionFile()).documentSourceType(property.getDocumentSourceType()).externalDocs(
                ExternalDocsConverter.convert(property.getExternalDocs())).host(property.getHost()).info(
                InfoConverter.convert(property.getInfo())).javadocEnabled(property.isJavadocEnabled())
            .jsonExampleValues(property.isJsonExampleValues()).locations(property.getLocations()).modelConverters(
                property.getModelConverters()).modelSubstitute(property.getModelSubstitute()).operationIdFormat(
                property.getOperationIdFormat()).outputFormat(property.getOutputFormat()).outputPath(
                property.getOutputPath()).removeBasePathFromEndpoints(property.isRemoveBasePathFromEndpoints()).schemes(
                property.getSchemes()).securityDefinitions(
                CollectionUtils.isEmpty(property.getSecurityDefinitions()) ? null :
                    property.getSecurityDefinitions().stream().map(SecurityDefinitionConverter::convert)
                        .collect(Collectors.toList())).skipInheritingClasses(property.isSkipInheritingClasses())
            .swaggerApiReader(property.getSwaggerApiReader()).swaggerDirectory(property.getSwaggerDirectory())
            .swaggerExtensions(property.getSwaggerExtensions()).swaggerFileName(property.getSwaggerFileName())
            .swaggerInternalFilter(property.getSwaggerInternalFilter()).swaggerSchemaConverter(
                property.getSwaggerSchemaConverter()).templatePath(property.getTemplatePath()).typesToSkip(
                property.getTypesToSkip()).useJAXBAnnotationProcessor(property.isUseJAXBAnnotationProcessor())
            .useJAXBAnnotationProcessorAsPrimary(property.isUseJAXBAnnotationProcessorAsPrimary()).build();
    }
}
