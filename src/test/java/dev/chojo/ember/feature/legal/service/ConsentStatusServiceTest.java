/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import dev.chojo.ember.feature.legal.entity.DocumentVersions;
import dev.chojo.ember.feature.legal.entity.GdprConsent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsentStatusServiceTest {
    private static final Instant AT = Instant.parse("2026-09-01T00:00:00Z");

    private ConsentService consents;
    private ConsentStatusService service;

    private static GdprConsent consent(String consentVersion, String privacyVersion, String tosVersion) {
        return new GdprConsent(1, 7, consentVersion, privacyVersion, tosVersion, "0.0.0.0", "DE", "ua", AT);
    }

    @BeforeEach
    void setup() {
        consents = mock(ConsentService.class);
        when(consents.getCurrentVersions()).thenReturn(new DocumentVersions("p2", "t2", "c2"));
        service = new ConsentStatusService(consents);
    }

    @Test
    void anAccountThatNeverConsentedIsNotCurrent() {
        var status = service.status(7);

        assertFalse(status.consented());
        assertFalse(status.current());
        assertEquals("c2", status.currentConsentVersion());
    }

    @Test
    void aConsentToEveryVersionInForceIsCurrent() {
        when(consents.findLatestConsent(7)).thenReturn(Optional.of(consent("c2", "p2", "t2")));

        var status = service.status(7);

        assertTrue(status.consented());
        assertTrue(status.current());
        assertEquals(AT, status.consentedAt());
    }

    @Test
    void aConsentThatNamedNoPolicyIsNotHeldAgainstTheAccountButAChangedOneIs() {
        when(consents.findLatestConsent(7)).thenReturn(Optional.of(consent("c2", null, null)));
        assertTrue(service.status(7).current());

        when(consents.findLatestConsent(7)).thenReturn(Optional.of(consent("c2", "p1", "t2")));
        assertFalse(service.status(7).current());

        when(consents.findLatestConsent(7)).thenReturn(Optional.of(consent("c1", "p2", "t2")));
        assertFalse(service.status(7).current());
    }

    @Test
    void theChangesAreTheDocumentsThatMovedOnSinceTheConsent() {
        when(consents.findLatestConsent(7)).thenReturn(Optional.of(consent("c2", "p1", "t2")));
        when(consents.getPrivacyDiff("p1", "p2")).thenReturn("+ new line");
        when(consents.getPrivacyPolicy("en")).thenReturn(new LegalDocumentService.RenderedDocument("<p/>", "", "p2"));

        var changes = service.changes(7, "en");

        assertTrue(changes.privacyChanged());
        assertFalse(changes.tosChanged());
        assertEquals("+ new line", changes.privacyDiff());
        assertEquals("<p/>", changes.privacyHtml());
        assertNull(changes.tosDiff());
        verify(consents, never()).getTermsOfService(any());
    }

    @Test
    void withoutAConsentNothingCountsAsChanged() {
        var changes = service.changes(7, "de");

        assertFalse(changes.privacyChanged());
        assertFalse(changes.tosChanged());
        assertEquals("t2", changes.currentTosVersion());
    }

    @Test
    void changedTermsComeWithTheirDiffAndText() {
        when(consents.findLatestConsent(7)).thenReturn(Optional.of(consent("c2", null, "t1")));
        when(consents.getTosDiff("t1", "t2")).thenReturn("- old");
        when(consents.getTermsOfService("de")).thenReturn(new LegalDocumentService.RenderedDocument("<t/>", "", "t2"));

        var changes = service.changes(7, "de");

        assertTrue(changes.tosChanged());
        assertEquals("- old", changes.tosDiff());
        assertEquals("<t/>", changes.tosHtml());
    }
}
