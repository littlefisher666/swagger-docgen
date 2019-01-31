package com.github.litttlefisher.swagger.docgen.document;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import com.github.litttlefisher.swagger.docgen.constant.SymbolConstant;
import com.github.litttlefisher.swagger.docgen.exception.GenerateException;

import io.swagger.models.Model;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Response;
import io.swagger.models.Swagger;

/**
 * @author littlefisher
 */
public class Utils {

    private static final String CLASSPATH = "classpath:";

    /**
     * 解析templatePath生成为{@link TemplatePath}类
     *
     * @param templatePath 文件路径
     * @return {@link TemplatePath}
     * @throws GenerateException 异常
     */
    public static TemplatePath parseTemplateUrl(String templatePath) throws GenerateException {
        if (templatePath == null) {
            return null;
        }
        TemplatePath tp;
        if (templatePath.startsWith(CLASSPATH)) {
            String resPath = templatePath.substring(CLASSPATH.length());
            tp = extractTemplateObject(resPath);
            tp.setLoader(new ClassPathTemplateLoader(tp.getPrefix(), tp.getSuffix()));
        } else {
            tp = extractTemplateObject(templatePath);
            tp.setLoader(new FileTemplateLoader(tp.getPrefix(), tp.getSuffix()));
        }

        return tp;
    }

    /**
     * 解析路径生成{@link TemplatePath}
     *
     * @param resPath 路径
     * @return {@link TemplatePath}
     * @throws GenerateException 异常
     */
    private static TemplatePath extractTemplateObject(String resPath) throws GenerateException {
        TemplatePath tp = new TemplatePath();
        String prefix = "";
        String suffix = "";
        String name = "";

        // 如果是文件路径格式，则以/区分
        int prefixIndex = resPath.lastIndexOf(File.separator);
        if (prefixIndex != -1) {
            prefix = resPath.substring(0, prefixIndex + 1);
        }

        // 如果是classpath路径格式，则以.区分
        int extIndex = resPath.lastIndexOf(SymbolConstant.PERIOD);
        if (extIndex != -1) {
            suffix = resPath.substring(extIndex);
            if (extIndex < prefix.length()) {
                throw new GenerateException("You have an interesting template path:" + resPath);
            }
            name = resPath.substring(prefix.length(), extIndex);
        }
        tp.setName(name);
        tp.setPrefix(prefix);
        tp.setSuffix(suffix);

        return tp;
    }

    public static void sortSwagger(Swagger swagger) throws GenerateException {
        if (swagger == null || swagger.getPaths() == null) {
            return;
        }

        if (swagger.getPaths() == null) {
            return;
        }
        TreeMap<String, Path> sortedMap = new TreeMap<>(swagger.getPaths());
        swagger.paths(sortedMap);

        for (Path path : swagger.getPaths().values()) {
            String[] methods = {"Get", "Delete", "Post", "Put", "Options", "Patch"};
            for (String m : methods) {
                sortResponses(path, m);
            }
        }

        // reorder definitions
        if (swagger.getDefinitions() != null) {
            TreeMap<String, Model> definitions = new TreeMap<>(swagger.getDefinitions());
            swagger.setDefinitions(definitions);
        }

        // order the tags
        if (swagger.getTags() != null) {
            swagger.getTags().sort(Comparator.comparing(a -> a.toString().toLowerCase()));
        }

    }

    private static void sortResponses(Path path, String method) throws GenerateException {
        try {
            Method m = Path.class.getDeclaredMethod("get" + method);
            Operation op = (Operation) m.invoke(path);
            if (op == null) {
                return;
            }
            Map<String, Response> responses = op.getResponses();
            TreeMap<String, Response> res = new TreeMap<>(responses);
            op.setResponses(res);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new GenerateException(e);
        }
    }
}
