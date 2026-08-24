/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.api.ApiException;
import io.javalin.http.HttpStatus;

/**
 * An address nobody has an account for, named where the caller sent no name to make one with.
 *
 * <p>A refusal of its own rather than a plain bad request, because the screen has something to do about
 * it: show the two name fields and let the same act be finished. The class name reaches the browser as
 * the error's category, which is what the screen keys on rather than on the wording of a message.
 */
public class AccountNameRequiredException extends ApiException {
    public AccountNameRequiredException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
