/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import com.yubico.webauthn.data.AttestationConveyancePreference;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class WebAuthnRelyingPartyFactoryTest {

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Api apiWithBaseUrl(String baseUrl) throws Exception {
        var api = new Api();
        setField(api, "baseUrl", baseUrl);
        return api;
    }

    private static TwoFactorSettings settings(String rpId, String rpName, String attestation) throws Exception {
        var settings = new TwoFactorSettings();
        var webauthn = settings.webauthn();
        setField(webauthn, "rpId", rpId);
        setField(webauthn, "rpName", rpName);
        setField(webauthn, "attestation", attestation);
        return settings;
    }

    @Test
    void rpIdDefaultsToBaseUrlHost() throws Exception {
        var rp = WebAuthnRelyingPartyFactory.build(
                settings("", "", "none"),
                apiWithBaseUrl("https://ember.example.org"),
                Mockito.mock(WebAuthnCredentialStore.class));
        assertEquals("ember.example.org", rp.getIdentity().getId());
        assertEquals("Ember", rp.getIdentity().getName());
        assertTrue(rp.getAttestationConveyancePreference().orElseThrow().equals(AttestationConveyancePreference.NONE));
    }

    @Test
    void explicitRpIdAndAttestationOverride() throws Exception {
        var rp = WebAuthnRelyingPartyFactory.build(
                settings("custom.host", "My Instance", "indirect"),
                apiWithBaseUrl("https://api.example.com"),
                Mockito.mock(WebAuthnCredentialStore.class));
        assertEquals("custom.host", rp.getIdentity().getId());
        assertEquals("My Instance", rp.getIdentity().getName());
        assertEquals(
                AttestationConveyancePreference.INDIRECT,
                rp.getAttestationConveyancePreference().orElseThrow());
    }

    @Test
    void directAttestation() throws Exception {
        var rp = WebAuthnRelyingPartyFactory.build(
                settings("", "", "direct"),
                apiWithBaseUrl("https://x.test"),
                Mockito.mock(WebAuthnCredentialStore.class));
        assertEquals(
                AttestationConveyancePreference.DIRECT,
                rp.getAttestationConveyancePreference().orElseThrow());
    }

    @Test
    void unknownAttestationFallsBackToNone() throws Exception {
        var rp = WebAuthnRelyingPartyFactory.build(
                settings("", "", "garbage"),
                apiWithBaseUrl("https://x.test"),
                Mockito.mock(WebAuthnCredentialStore.class));
        assertEquals(
                AttestationConveyancePreference.NONE,
                rp.getAttestationConveyancePreference().orElseThrow());
    }

    @Test
    void nullAttestationFallsBackToNone() throws Exception {
        var rp = WebAuthnRelyingPartyFactory.build(
                settings("", "", null), apiWithBaseUrl("https://x.test"), Mockito.mock(WebAuthnCredentialStore.class));
        assertEquals(
                AttestationConveyancePreference.NONE,
                rp.getAttestationConveyancePreference().orElseThrow());
    }

    @Test
    void rpIdFallsBackToLocalhostWhenBaseUrlBlank() throws Exception {
        var rp = WebAuthnRelyingPartyFactory.build(
                settings("", "", "none"), apiWithBaseUrl(""), Mockito.mock(WebAuthnCredentialStore.class));
        assertEquals("localhost", rp.getIdentity().getId());
    }

    @Test
    void rpIdFallsBackToLocalhostWhenBaseUrlInvalid() throws Exception {
        // Docker service names with underscores are RFC 3986 invalid as hosts. The factory
        // catches IllegalArgumentException from URI parsing and falls back to localhost.
        var rp = WebAuthnRelyingPartyFactory.build(
                settings("", "", "none"),
                apiWithBaseUrl("http://ember_target_backend:8080"),
                Mockito.mock(WebAuthnCredentialStore.class));
        assertEquals("localhost", rp.getIdentity().getId());
    }
}
