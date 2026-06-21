/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import com.yubico.webauthn.data.AttestationConveyancePreference;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import dev.chojo.ember.feature.twofactor.service.WebAuthnCredentialStore;
import dev.chojo.ember.feature.twofactor.service.WebAuthnRelyingPartyFactory;
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
}
