/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import com.yubico.webauthn.data.AttestationConveyancePreference;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.WebAuthnSettings;
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

    private static WebAuthnSettings settings(String rpId, String rpName, String attestation) throws Exception {
        var settings = new WebAuthnSettings();
        setField(settings, "rpId", rpId);
        setField(settings, "rpName", rpName);
        setField(settings, "attestation", attestation);
        return settings;
    }

    private static RelyingParties build(WebAuthnSettings settings, Api api) {
        return WebAuthnRelyingPartyFactory.build(
                settings,
                api,
                Mockito.mock(WebAuthnCredentialStore.class),
                Mockito.mock(SecondFactorCredentialStore.class));
    }

    @Test
    void rpIdDefaultsToBaseUrlHost() throws Exception {
        var parties = build(settings("", "", "none"), apiWithBaseUrl("https://ember.example.org"));
        assertEquals("ember.example.org", parties.passkey().getIdentity().getId());
        assertEquals("Ember", parties.passkey().getIdentity().getName());
        assertEquals(
                parties.passkey().getAttestationConveyancePreference().orElseThrow(),
                AttestationConveyancePreference.NONE);
        assertFalse(parties.localhostFallback());
    }

    @Test
    void bothViewsShareIdentityAndOrigins() throws Exception {
        var parties = build(settings("custom.host", "My Instance", "none"), apiWithBaseUrl("https://api.example.com"));
        assertEquals(parties.passkey().getIdentity(), parties.secondFactor().getIdentity());
        assertEquals(parties.passkey().getOrigins(), parties.secondFactor().getOrigins());
    }

    @Test
    void explicitRpIdAndAttestationOverride() throws Exception {
        var parties =
                build(settings("custom.host", "My Instance", "indirect"), apiWithBaseUrl("https://api.example.com"));
        assertEquals("custom.host", parties.passkey().getIdentity().getId());
        assertEquals("My Instance", parties.passkey().getIdentity().getName());
        assertEquals(
                AttestationConveyancePreference.INDIRECT,
                parties.passkey().getAttestationConveyancePreference().orElseThrow());
    }

    @Test
    void directAttestation() throws Exception {
        var parties = build(settings("", "", "direct"), apiWithBaseUrl("https://x.test"));
        assertEquals(
                AttestationConveyancePreference.DIRECT,
                parties.passkey().getAttestationConveyancePreference().orElseThrow());
    }

    @Test
    void unknownAttestationFallsBackToNone() throws Exception {
        var parties = build(settings("", "", "garbage"), apiWithBaseUrl("https://x.test"));
        assertEquals(
                AttestationConveyancePreference.NONE,
                parties.passkey().getAttestationConveyancePreference().orElseThrow());
    }

    @Test
    void nullAttestationFallsBackToNone() throws Exception {
        var parties = build(settings("", "", null), apiWithBaseUrl("https://x.test"));
        assertEquals(
                AttestationConveyancePreference.NONE,
                parties.passkey().getAttestationConveyancePreference().orElseThrow());
    }

    @Test
    void rpIdFallsBackToLocalhostWhenBaseUrlBlank() throws Exception {
        var parties = build(settings("", "", "none"), apiWithBaseUrl(""));
        assertEquals("localhost", parties.passkey().getIdentity().getId());
        assertTrue(parties.localhostFallback(), "the fallback must be carried so the passkey mode can be held at OFF");
    }

    @Test
    void rpIdFallsBackToLocalhostWhenBaseUrlInvalid() throws Exception {
        // Docker service names with underscores are RFC 3986 invalid as hosts. The factory
        // catches IllegalArgumentException from URI parsing and falls back to localhost.
        var parties = build(settings("", "", "none"), apiWithBaseUrl("http://ember_target_backend:8080"));
        assertEquals("localhost", parties.passkey().getIdentity().getId());
        assertTrue(parties.localhostFallback());
    }

    @Test
    void explicitRpIdIsNotAFallback() throws Exception {
        var parties = build(settings("real.host", "", "none"), apiWithBaseUrl(""));
        assertEquals("real.host", parties.passkey().getIdentity().getId());
        assertFalse(parties.localhostFallback());
    }

    // -- The settings move from auth.twoFactor.webauthn to auth.webauthn --

    @Test
    void newLocationWinsWhenSet() throws Exception {
        var auth = new Auth();
        setField(auth.webauthn(), "rpId", "new.host");
        setField(auth.twoFactor().webauthn(), "rpId", "old.host");

        var resolved = WebAuthnSettings.resolvedFrom(auth);
        assertEquals("new.host", resolved.rpId());
    }

    @Test
    void legacyLocationIsAdoptedWhileNewOneIsUntouched() throws Exception {
        var auth = new Auth();
        setField(auth.twoFactor().webauthn(), "rpId", "old.host");
        setField(auth.twoFactor().webauthn(), "rpName", "Old Name");
        setField(auth.twoFactor().webauthn(), "attestation", "indirect");
        setField(auth.twoFactor().webauthn(), "timeoutSeconds", 90);

        var resolved = WebAuthnSettings.resolvedFrom(auth);
        assertEquals("old.host", resolved.rpId());
        assertEquals("Old Name", resolved.rpName());
        assertEquals("indirect", resolved.attestation());
        assertEquals(90, resolved.timeoutSeconds());
        // The adoption mutates the new location, so the runtime and the admin screen agree.
        assertEquals("old.host", auth.webauthn().rpId());
    }

    @Test
    void defaultsStayDefaultsWhenNeitherLocationIsSet() {
        var auth = new Auth();
        var resolved = WebAuthnSettings.resolvedFrom(auth);
        assertEquals("", resolved.rpId());
        assertEquals("none", resolved.attestation());
        assertEquals(60, resolved.timeoutSeconds());
    }
}
