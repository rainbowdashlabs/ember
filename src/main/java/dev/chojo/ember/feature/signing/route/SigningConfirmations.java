/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.route;

import dev.chojo.ember.api.RateLimits;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.account.service.AuthRateLimiter;
import dev.chojo.ember.feature.signing.entity.SignatureImageSource;
import dev.chojo.ember.feature.signing.entity.SigningPicture;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import io.javalin.http.Context;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * What every route that confirms a signing act shares: reading a confirmation that may carry signature
 * pictures, decoding a picture, and limiting the attempts by proof with the buckets every other step-up of
 * the account uses, so signing opens no way around them.
 */
final class SigningConfirmations {
    private SigningConfirmations() {}

    /**
     * The confirmation as sent, read up to a limit of its own. The server reads a body only up to a
     * megabyte, which a photographed signature in Base64 easily passes, so these routes read theirs up to
     * the pictures they take.
     *
     * @param ctx      the request
     * @param maxBytes the largest body taken
     * @param type     what the body is read into
     * @param <T>      the body's type
     * @return the body
     */
    static <T> T body(Context ctx, int maxBytes, Class<T> type) {
        byte[] raw;
        try (InputStream content = ctx.bodyInputStream()) {
            raw = content.readNBytes(maxBytes + 1);
        } catch (IOException e) {
            throw new UncheckedIOException("The signing confirmation could not be read", e);
        }
        if (raw.length > maxBytes) throw DocumentRefusal.SIGNATURE_IMAGE_TOO_LARGE.raise();
        return ctx.jsonMapper().fromJsonString(new String(raw, StandardCharsets.UTF_8), type);
    }

    /**
     * @param encoded the picture made for the act in Base64, or null to sign with the saved one
     * @param source  how it was made, or null
     * @param keep    whether it replaces the saved picture, or null for no
     * @return the picture to sign with
     */
    static SigningPicture picture(
            @Nullable String encoded, @Nullable SignatureImageSource source, @Nullable Boolean keep) {
        if (encoded == null || encoded.isBlank()) return SigningPicture.SAVED;
        try {
            byte[] made = Base64.getDecoder().decode(encoded.strip());
            return new SigningPicture(made, source, Boolean.TRUE.equals(keep));
        } catch (IllegalArgumentException e) {
            throw DocumentRefusal.SIGNATURE_IMAGE_NOT_A_PICTURE.raise();
        }
    }

    /**
     * Refuses an attempt beyond what the account's step-up buckets allow: the password by the password
     * bucket, everything else by the second-factor bucket.
     *
     * @param ctx         the request
     * @param rateLimiter the limiter holding the buckets
     * @param accountId   the account confirming
     * @param proof       the proof it confirms with
     */
    static void throttle(Context ctx, AuthRateLimiter rateLimiter, int accountId, StepUpProof proof) {
        if (proof == StepUpProof.PASSWORD) {
            RateLimits.enforce(
                    DocumentRefusal.SIGNING_PASSWORD_TOO_OFTEN, rateLimiter.tryPasswordStepUp(ctx.ip(), accountId));
            return;
        }
        RateLimits.enforce(
                DocumentRefusal.SIGNING_CONFIRMATION_TOO_OFTEN, rateLimiter.tryTwoFactor(ctx.ip(), accountId));
    }
}
