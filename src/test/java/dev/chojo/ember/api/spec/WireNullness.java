/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.spec;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.AnnotatedParameter;
import tools.jackson.databind.introspect.BeanPropertyDefinition;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Whether a property of a wire type is marked nullable.
 *
 * <p>A reference is non-null unless its declaration says otherwise with {@link Nullable}. For a record
 * that is the component; for any other type it is the getter, the field or the constructor parameter
 * the property is read or written through.
 */
final class WireNullness {

    private WireNullness() {}

    /**
     * @param owner    the type that declares the property
     * @param property the property as the mapper sees it
     * @return whether the property is marked nullable
     */
    static boolean isNullable(Class<?> owner, BeanPropertyDefinition property) {
        if (owner.isRecord()) {
            return Arrays.stream(owner.getRecordComponents())
                    .filter(component -> component.getName().equals(property.getInternalName()))
                    .anyMatch(WireNullness::isMarked);
        }
        return Stream.of(property.getGetter(), property.getField(), property.getConstructorParameter())
                .anyMatch(WireNullness::isMarked);
    }

    private static boolean isMarked(RecordComponent component) {
        return isMarked(component, component.getAnnotatedType());
    }

    private static boolean isMarked(AnnotatedMember member) {
        if (member == null) return false;
        if (member instanceof AnnotatedParameter parameter) {
            Executable owner = (Executable) parameter.getOwner().getMember();
            int index = parameter.getIndex();
            return isMarked(owner.getParameters()[index], owner.getAnnotatedParameterTypes()[index]);
        }
        if (member.getMember() instanceof Method method) return isMarked(method, method.getAnnotatedReturnType());
        if (member.getMember() instanceof Field field) return isMarked(field, field.getAnnotatedType());
        return false;
    }

    private static boolean isMarked(AnnotatedElement declaration, AnnotatedType type) {
        return declaration.isAnnotationPresent(Nullable.class) || type.isAnnotationPresent(Nullable.class);
    }
}
