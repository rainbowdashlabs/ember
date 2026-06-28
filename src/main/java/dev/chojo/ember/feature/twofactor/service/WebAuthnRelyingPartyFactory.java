/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.AttestationConveyancePreference;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Set;

/**
 * Builds the singleton {@link RelyingParty} from the deployed {@link Api#baseUrl()} and the
 * {@link TwoFactorSettings.WebAuthnConfig} overrides. The rpId defaults to the API base host
 * and the only allowed origin is the API base URL itself. When neither an explicit rpId nor
 * a usable host can be derived (e.g. {@code api.baseUrl} contains a hostname that fails
 * RFC 3986 parsing such as a docker service name with underscores), the factory falls back to
 * {@code "localhost"} with a warning rather than refusing to start — the same WebAuthn flow
 * just won't bind to a useful effective domain in that misconfigured case.
 */
public final class WebAuthnRelyingPartyFactory {
    private static final Logger log = LoggerFactory.getLogger(WebAuthnRelyingPartyFactory.class);
    private static final String DEFAULT_RP_ID = "localhost";
    private static final String DEFAULT_RP_NAME = "Ember";

    private WebAuthnRelyingPartyFactory() {}

    public static RelyingParty build(TwoFactorSettings twoFactor, Api api, WebAuthnCredentialStore credentialStore) {
        var config = twoFactor.webauthn();
        String rpId = blankToNull(config.rpId());
        if (rpId == null) rpId = blankToNull(safeHost(api.baseUrl()));
        if (rpId == null) {
            log.warn(
                    "WebAuthn rpId could not be derived from api.baseUrl '{}' and twoFactor.webauthn.rpId is unset — "
                            + "defaulting to '{}'. Set twoFactor.webauthn.rpId explicitly for production.",
                    api.baseUrl(),
                    DEFAULT_RP_ID);
            rpId = DEFAULT_RP_ID;
        }
        String rpName = blankToNull(config.rpName());
        if (rpName == null) rpName = DEFAULT_RP_NAME;

        var identity = RelyingPartyIdentity.builder().id(rpId).name(rpName).build();
        return RelyingParty.builder()
                .identity(identity)
                .credentialRepository(credentialStore)
                .origins(Set.of(api.baseUrl()))
                .attestationConveyancePreference(resolveAttestation(config.attestation()))
                .allowOriginPort(true)
                .allowUntrustedAttestation(true)
                .build();
    }

    /**
     * Parses {@code baseUrl} and returns the host, or {@code null} when the URL is malformed
     * or the host fails RFC 3986 parsing (e.g. contains underscores). Never throws.
     */
    private static String safeHost(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return null;
        try {
            return URI.create(baseUrl).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static AttestationConveyancePreference resolveAttestation(String value) {
        if (value == null) return AttestationConveyancePreference.NONE;
        return switch (value.toLowerCase()) {
            case "indirect" -> AttestationConveyancePreference.INDIRECT;
            case "direct" -> AttestationConveyancePreference.DIRECT;
            default -> AttestationConveyancePreference.NONE;
        };
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
