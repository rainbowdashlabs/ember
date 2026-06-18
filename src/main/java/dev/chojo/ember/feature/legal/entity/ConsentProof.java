/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.entity;

import java.time.Instant;

/**
 * Proof that an anonymous public submitter accepted the current privacy policy, terms of
 * service, and consent text. Captured at the moment of submission and persisted inline on
 * the affected row (e.g. {@code form_response}, {@code waiting_list_entry}). Surface lives
 * unchanged across an intermediate verification step (e.g. a waitlist e-mail confirmation)
 * so the {@code consentedAt} timestamp always reflects the moment the submitter ticked the
 * checkbox, not the moment the row was finally written.
 *
 * <p>The three version hashes are validated against the current document versions before
 * the submission is accepted; the IP, country, and user-agent fields are derived from the
 * request context using the same helpers as {@code gdpr_consent} for member accounts.
 *
 * @param consentVersion the version hash of the consent text the submitter accepted
 * @param privacyVersion the version hash of the privacy policy the submitter accepted
 * @param tosVersion     the version hash of the terms of service the submitter accepted
 * @param ipAddress      the client IP resolved via {@code ClientIp.resolve}
 * @param country        the country derived from the {@code CF-IPCountry} header, may be {@code null}
 * @param userAgent      the {@code User-Agent} header from the submission, may be {@code null}
 * @param consentedAt    the timestamp at which the submitter ticked the consent checkbox
 */
public record ConsentProof(
        String consentVersion,
        String privacyVersion,
        String tosVersion,
        String ipAddress,
        String country,
        String userAgent,
        Instant consentedAt) {}
