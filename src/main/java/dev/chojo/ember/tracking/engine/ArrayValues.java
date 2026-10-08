/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import de.chojo.sadu.core.types.SqlType;
import de.chojo.sadu.queries.api.call.Call;
import org.jspecify.annotations.Nullable;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Array columns as the generic engine carries them: read out of the database as a plain list of their
 * elements, and bound back as an SQL array of the column's element type.
 *
 * <p>The tracking file names an array type the way PostgreSQL does, with a leading underscore in front of
 * the element type ({@code _text}, {@code _int4}, {@code _time}), so the element type is read off the
 * column type and no array type needs a case of its own.
 *
 * <p>Numbers, booleans and text elements travel as they are. Every other element, such as a time, a date
 * or a uuid, travels as the text the database writes for it, which is also the text it reads it back
 * from.
 */
public final class ArrayValues {

    private ArrayValues() {}

    /**
     * @param type a column type as the tracking file names it
     * @return whether the type is an array type
     */
    public static boolean isArray(@Nullable String type) {
        return type != null && type.startsWith("_");
    }

    /**
     * Reads an array column as the list of its elements.
     *
     * @param array the column's value
     * @return its elements in order, or null where the column is null
     * @throws SQLException when the driver cannot read the array
     */
    public static @Nullable List<@Nullable Object> read(@Nullable Array array) throws SQLException {
        if (array == null) return null;
        try {
            Object[] elements = (Object[]) array.getArray();
            List<@Nullable Object> list = new ArrayList<>(elements.length);
            for (Object element : elements) list.add(wireElement(element));
            return list;
        } finally {
            array.free();
        }
    }

    /**
     * Binds a transferred array value as an SQL array of the column's element type.
     *
     * @param call  the call to bind on
     * @param token the parameter name
     * @param value the elements, as a list or a Java array
     * @param type  the column type as the tracking file names it, such as {@code _int4}
     * @return the call with the value bound
     * @throws IllegalArgumentException when the value is not a list of elements
     */
    public static Call bind(Call call, String token, Object value, String type) {
        return call.bind(token, elements(value), SqlType.ofName(type.substring(1)));
    }

    /**
     * @param value a transferred array value
     * @return its elements
     * @throws IllegalArgumentException when the value is not a list of elements
     */
    static List<@Nullable Object> elements(Object value) {
        if (value instanceof Collection<?> collection) return new ArrayList<>(collection);
        if (value instanceof Object[] array) return Arrays.asList(array);
        throw new IllegalArgumentException("An array column carries a list of elements, not "
                + value.getClass().getSimpleName());
    }

    private static @Nullable Object wireElement(@Nullable Object element) {
        if (element == null || element instanceof Number || element instanceof Boolean || element instanceof String) {
            return element;
        }
        return element.toString();
    }
}
