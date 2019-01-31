package com.github.litttlefisher.swagger.docgen.util;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author littlefisher
 */
public class SpringUtils {
    /**
     * 查找所有Controller对应的path
     *
     * @param controllerClazz controller类
     * @return path值，如果{@link RequestMapping#value()}为空，则返回一个有一个item的String集合
     */
    public static String[] getControllerRequestMapping(Class<?> controllerClazz) {
        String[] controllerRequestMappingValues = {};

        // Determine if we will use class-level requestMapping or dummy string
        RequestMapping classRequestMapping = AnnotationUtils.findAnnotation(controllerClazz, RequestMapping.class);
        if (classRequestMapping != null) {
            controllerRequestMappingValues = classRequestMapping.value();
        }

        if (controllerRequestMappingValues.length == 0) {
            controllerRequestMappingValues = new String[1];
            controllerRequestMappingValues[0] = StringUtils.EMPTY;
        }
        return controllerRequestMappingValues;
    }
}
