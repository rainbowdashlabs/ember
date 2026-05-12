/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

public interface HashAlgorithm {

    String name();

    PasswordHash hash(String password);

    boolean verify(String password, PasswordHash hash);
}
