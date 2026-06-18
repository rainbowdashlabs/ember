/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.entity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Proof that an anonymous public submitter accepted the current privacy policy, terms of
 * service, and consent text. Captured at the moment of submission and persisted inline on
 * the affected row (e.g. {@code form_response}, {@code waiting_list_entry}) as a single
 * JSONB blob. Surface lives unchanged across an intermediate verification step (e.g. a
 * waitlist e-mail confirmation) so the {@code consentedAt} timestamp always reflects the
 * moment the submitter ticked the checkbox, not the moment the row was finally written.
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConsentProof(
        String consentVersion,
        String privacyVersion,
        String tosVersion,
        String ipAddress,
        String country,
        String userAgent,
        Instant consentedAt) {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .changeDefaultVisibility(v -> v.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE))
            .build();

    /**
     * Parses the JSONB-encoded form of a {@code ConsentProof}. Returns {@code null} if the
     * input is {@code null} / blank or cannot be parsed; the surrounding row has the proof
     * column nullable so a malformed value never blocks reading the row.
     *
     * @param json the JSON payload as stored in the {@code consent_proof} column
     * @return the parsed proof, or {@code null} when none could be recovered
     */
    public static ConsentProof parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, ConsentProof.class);
        } catch (Exception e) {
            getLogger(ConsentProof.class).warn("Failed to parse ConsentProof JSON: {}", json, e);
            return null;
        }
    }

    /**
     * Serialises this proof to a JSON string ready to be bound to the {@code consent_proof}
     * JSONB column.
     *
     * @return JSON-encoded representation
     */
    public String toJson() {
        return MAPPER.writeValueAsString(this);
    }
}
