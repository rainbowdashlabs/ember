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

import java.net.URI;
import java.util.Set;

/**
 * Builds the singleton {@link RelyingParty} from the deployed {@link Api#baseUrl()} and the
 * {@link TwoFactorSettings.WebAuthnConfig} overrides. The rpId defaults to the API base host
 * and the only allowed origin is the API base URL itself.
 */
public final class WebAuthnRelyingPartyFactory {
    private WebAuthnRelyingPartyFactory() {}

    public static RelyingParty build(TwoFactorSettings twoFactor, Api api, WebAuthnCredentialStore credentialStore) {
        var config = twoFactor.webauthn();
        URI baseUri = URI.create(api.baseUrl());
        String rpId = blankToNull(config.rpId());
        if (rpId == null) rpId = baseUri.getHost();
        String rpName = blankToNull(config.rpName());
        if (rpName == null) rpName = "Ember";

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
