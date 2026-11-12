/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a handler, or a whole route class, that lives in a module's feature but belongs to no module.
 *
 * <p>Every other route of a module's feature belongs to that module, and the architecture test holds
 * it to that, so that the route guard refuses it while the module is off. The reason is what a
 * reviewer reads to decide whether the exception still holds: write what keeps the endpoint useful
 * while the module is off.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ModuleFree {

    /** Why this endpoint belongs to no module. */
    String value();
}
