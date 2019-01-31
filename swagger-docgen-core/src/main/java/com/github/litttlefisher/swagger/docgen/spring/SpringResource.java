package com.github.litttlefisher.swagger.docgen.spring;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.github.litttlefisher.swagger.docgen.util.SpringUtils;

import lombok.Data;

/**
 * 解析Controller类构造该Spring相关的配置，一个{@link SpringResource}就是一个对外暴露的接口
 *
 * @author littlefisher
 */
@Data
public class SpringResource {
    /** 该方法对应的controller类路径 */
    private Class<?> controllerClass;
    /** 该方法支持的 */
    private List<Method> methods;
    /**
     * controller上配置的RequestMapping路径
     */
    private List<String> controllerMapping;
    private String resourceName;
    private String resourceKey;
    private String description;

    /**
     * @param clazz Controller class
     * @param resourceName resource Name
     * @param resourceKey key containing the controller package, class controller class name, and controller-level @RequestMapping#value
     * @param description description of the controller
     */
    public SpringResource(Class<?> clazz, String resourceName, String resourceKey, String description) {
        this.controllerClass = clazz;
        this.resourceName = resourceName;
        this.resourceKey = resourceKey;
        this.description = description;
        methods = new ArrayList<>();

        String[] controllerRequestMappingValues = SpringUtils.getControllerRequestMapping(controllerClass);
        // 去除最后一个分隔符
        this.controllerMapping = Arrays.stream(controllerRequestMappingValues).map(
            input -> StringUtils.removeEnd(input, "/")).collect(Collectors.toList());
    }

    public void addMethod(Method m) {
        this.methods.add(m);
    }
}
