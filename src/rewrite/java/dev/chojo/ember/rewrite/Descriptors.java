/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.rewrite;

import org.jspecify.annotations.Nullable;
import org.openrewrite.java.tree.JavaType;

/**
 * Writes a type the way a JVM method descriptor does, so a method of the source tree can be matched
 * against a method a SpotBugs report names by its descriptor.
 *
 * <p>Generic types are erased as the compiler erases them: a parameterised type to its raw type and
 * a type variable to its first bound. A type that is not known to the parser writes as {@code ?}
 * and so never matches.
 */
final class Descriptors {

    private Descriptors() {}

    /**
     * The descriptor of one type.
     *
     * @param type the type, or {@code null} when the parser did not know it
     * @return its erased descriptor, such as {@code Ljava/lang/String;} or {@code I}
     */
    static String of(@Nullable JavaType type) {
        if (type instanceof JavaType.Primitive primitive) {
            return switch (primitive) {
                case Boolean -> "Z";
                case Byte -> "B";
                case Char -> "C";
                case Double -> "D";
                case Float -> "F";
                case Int -> "I";
                case Long -> "J";
                case Short -> "S";
                case Void -> "V";
                case String -> "Ljava/lang/String;";
                default -> "?";
            };
        }
        if (type instanceof JavaType.Array array) {
            return "[" + of(array.getElemType());
        }
        if (type instanceof JavaType.GenericTypeVariable variable) {
            return variable.getBounds().isEmpty()
                    ? "Ljava/lang/Object;"
                    : of(variable.getBounds().getFirst());
        }
        if (type instanceof JavaType.FullyQualified qualified) {
            return "L" + qualified.getFullyQualifiedName().replace('.', '/') + ";";
        }
        return "?";
    }
}
