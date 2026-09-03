/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.AttestationConveyancePreference;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.WebAuthnSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Set;

/**
 * Builds the two {@link RelyingParty} views from the deployed {@link Api#baseUrl()} and the
 * {@link WebAuthnSettings} overrides. The rpId defaults to the API base host and the only
 * allowed origin is the API base URL itself. When neither an explicit rpId nor a usable host can
 * be derived (e.g. {@code api.baseUrl} contains a hostname that fails RFC 3986 parsing such as a
 * docker service name with underscores), the factory falls back to {@code "localhost"} with a
 * warning rather than refusing to start - an instance that previously started with a warning
 * must not suddenly fail to boot. The fallback is carried on the result, and the effective
 * passkey mode is held at OFF while it stands: a passkey bound to the wrong effective domain is
 * worse than no passkey.
 *
 * <p>Two decisions are taken here explicitly rather than inherited:
 *
 * <ul>
 *   <li>{@code validateSignatureCounter} stays on for both views. Passkeys commonly report a
 *       counter of zero forever, and the library already exempts authenticators that do; what
 *       the check catches is a counter that goes backwards, which is a cloned credential.
 *   <li>{@code allowOriginSubdomain} stays off. The one legitimate origin is the base URL
 *       itself; a subdomain is not this instance.
 * </ul>
 */
public final class WebAuthnRelyingPartyFactory {
    private static final Logger log = LoggerFactory.getLogger(WebAuthnRelyingPartyFactory.class);
    private static final String DEFAULT_RP_ID = "localhost";
    private static final String DEFAULT_RP_NAME = "Ember";

    private WebAuthnRelyingPartyFactory() {}

    public static RelyingParties build(
            WebAuthnSettings settings,
            Api api,
            WebAuthnCredentialStore fullStore,
            SecondFactorCredentialStore secondFactorStore) {
        boolean localhostFallback = false;
        String rpId = blankToNull(settings.rpId());
        if (rpId == null) rpId = blankToNull(safeHost(api.baseUrl()));
        if (rpId == null) {
            log.warn(
                    "WebAuthn rpId could not be derived from api.baseUrl '{}' and webauthn.rpId is unset - "
                            + "defaulting to '{}' and holding the passkey mode at OFF. "
                            + "Set auth.webauthn.rpId explicitly for production.",
                    api.baseUrl(),
                    DEFAULT_RP_ID);
            rpId = DEFAULT_RP_ID;
            localhostFallback = true;
        }
        String rpName = blankToNull(settings.rpName());
        if (rpName == null) rpName = DEFAULT_RP_NAME;

        var identity = RelyingPartyIdentity.builder().id(rpId).name(rpName).build();
        var attestation = resolveAttestation(settings.attestation());
        return new RelyingParties(
                view(identity, api, attestation, fullStore),
                view(identity, api, attestation, secondFactorStore),
                localhostFallback);
    }

    private static RelyingParty view(
            RelyingPartyIdentity identity,
            Api api,
            AttestationConveyancePreference attestation,
            CredentialRepository store) {
        return RelyingParty.builder()
                .identity(identity)
                .credentialRepository(store)
                .origins(Set.of(api.baseUrl()))
                .attestationConveyancePreference(attestation)
                .allowOriginPort(true)
                .allowOriginSubdomain(false)
                .allowUntrustedAttestation(true)
                .validateSignatureCounter(true)
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
