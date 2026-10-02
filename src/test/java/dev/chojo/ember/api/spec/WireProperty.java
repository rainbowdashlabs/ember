/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.spec;

import tools.jackson.databind.JavaType;

/**
 * One property of a wire type, with what decides whether it is always there and whether it can be null.
 *
 * @param name        the name the mapper writes
 * @param type        the declared type
 * @param nullable    whether the property is marked nullable
 * @param omitsNull   whether the mapper leaves the property out when it is null
 */
record WireProperty(String name, JavaType type, boolean nullable, boolean omitsNull) {

    /**
     * A property is required in a response unless it can be null and is then left out. A request may
     * leave out anything.
     *
     * @param direction whether the property is written or read
     * @return whether every value of the type carries it
     */
    boolean required(WireSchemas.Direction direction) {
        if (direction == WireSchemas.Direction.REQUEST) return false;
        return type.isPrimitive() || !(nullable && omitsNull);
    }

    /**
     * A nullable property carries null unless the mapper leaves it out instead.
     *
     * @param direction whether the property is written or read
     * @return whether null is one of its values
     */
    boolean carriesNull(WireSchemas.Direction direction) {
        if (!nullable || type.isPrimitive()) return false;
        return direction == WireSchemas.Direction.REQUEST || !omitsNull;
    }
}
