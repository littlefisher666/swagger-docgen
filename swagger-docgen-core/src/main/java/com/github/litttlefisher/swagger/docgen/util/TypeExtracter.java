package com.github.litttlefisher.swagger.docgen.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.github.litttlefisher.swagger.docgen.reader.JaxrsReader;

/**
 * @author littlefisher
 */
public class TypeExtracter {

    private static final AccessibleObjectGetter<Field> FIELD_GETTER = Class::getDeclaredFields;

    private static final AccessibleObjectGetter<Method> METHOD_GETTER = Class::getDeclaredMethods;

    private static final AccessibleObjectGetter<Constructor<?>> CONSTRUCTOR_GETTER = Class::getDeclaredConstructors;

    public Collection<TypeWithAnnotations> extractTypes(Class<?> cls) {

        ArrayList<TypeWithAnnotations> typesWithAnnotations = new ArrayList<>();

        typesWithAnnotations.addAll(getPropertyTypes(cls));
        typesWithAnnotations.addAll(getMethodParameterTypes(cls));
        typesWithAnnotations.addAll(getConstructorParameterTypes(cls));

        return typesWithAnnotations;
    }

    private Collection<TypeWithAnnotations> getPropertyTypes(Class<?> clazz) {
        Collection<TypeWithAnnotations> typesWithAnnotations = new ArrayList<>();
        for (Field field : getDeclaredAndInheritedMembers(clazz, FIELD_GETTER)) {
            Type type = field.getGenericType();
            List<Annotation> annotations = Arrays.asList(field.getAnnotations());
            typesWithAnnotations.add(new TypeWithAnnotations(type, annotations));
        }

        return typesWithAnnotations;
    }

    private Collection<TypeWithAnnotations> getMethodParameterTypes(Class<?> clazz) {
        Collection<TypeWithAnnotations> typesWithAnnotations = new ArrayList<>();
        /*
         * For methods we will only examine setters and will only look at the
         * annotations on the parameter, not the method itself.
         */
        for (Method method : getDeclaredAndInheritedMembers(clazz, METHOD_GETTER)) {

            Type[] parameterTypes = method.getGenericParameterTypes();
            // skip methods that don't look like setters
            if (parameterTypes.length != 1 || method.getReturnType() != void.class) {
                continue;
            }
            Type type = parameterTypes[0];
            List<Annotation> annotations = Arrays.asList(JaxrsReader.findParamAnnotations(method)[0]);
            typesWithAnnotations.add(new TypeWithAnnotations(type, annotations));
        }

        return typesWithAnnotations;
    }

    private Collection<TypeWithAnnotations> getConstructorParameterTypes(Class<?> clazz) {
        Collection<TypeWithAnnotations> typesWithAnnotations = new ArrayList<>();
        for (Constructor<?> constructor : getDeclaredAndInheritedMembers(clazz, CONSTRUCTOR_GETTER)) {

            Type[] parameterTypes = constructor.getGenericParameterTypes();
            Annotation[][] parameterAnnotations = constructor.getParameterAnnotations();

            for (int i = 0; i < parameterTypes.length; i++) {
                Type type = parameterTypes[i];
                List<Annotation> annotations = Arrays.asList(parameterAnnotations[i]);
                typesWithAnnotations.add(new TypeWithAnnotations(type, annotations));
            }
        }

        return typesWithAnnotations;
    }

    private <T extends AccessibleObject> List<T> getDeclaredAndInheritedMembers(Class<?> clazz,
        AccessibleObjectGetter<? extends T> getter) {
        List<T> fields = new ArrayList<T>();
        Class<?> inspectedClass = clazz;
        while (inspectedClass != null) {
            fields.addAll(Arrays.asList(getter.get(inspectedClass)));
            inspectedClass = inspectedClass.getSuperclass();
        }
        return fields;
    }

    /**
     * get rid of this and use lambdas instead once Java 8 is supported
     */
    private interface AccessibleObjectGetter<T extends AccessibleObject> {

        T[] get(Class<?> clazz);
    }
}
