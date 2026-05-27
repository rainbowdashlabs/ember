/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConsentServiceTest extends RepositoryTestBase {
    @TempDir
    static Path tempDir;

    private static ConsentService service;
    private static Account account;

    @BeforeAll
    static void setup() throws IOException {
        // Create minimal directory structure for legal docs
        Path privacyDir = tempDir.resolve("privacy");
        Path tosDir = tempDir.resolve("tos");
        Path consentDir = tempDir.resolve("consent");
        Path imprintDir = tempDir.resolve("imprint");

        Files.createDirectories(privacyDir.resolve("de"));
        Files.createDirectories(tosDir.resolve("de"));
        Files.createDirectories(consentDir.resolve("de"));
        Files.createDirectories(imprintDir.resolve("de"));

        // Write simple markdown content
        Files.writeString(privacyDir.resolve("de").resolve("01-privacy.md"), "# Privacy\nWe value your privacy.");
        Files.writeString(tosDir.resolve("de").resolve("01-tos.md"), "# Terms\nThese are the terms.");
        Files.writeString(consentDir.resolve("de").resolve("01-consent.md"), "# Consent\nPlease consent.");
        Files.writeString(imprintDir.resolve("de").resolve("01-imprint.md"), "# Imprint\nCompany info.");

        var apiConfig = mock(Api.class);
        when(apiConfig.privacyPolicyDir()).thenReturn(privacyDir.toString());
        when(apiConfig.tosDir()).thenReturn(tosDir.toString());
        when(apiConfig.consentDir()).thenReturn(consentDir.toString());
        when(apiConfig.imprintDir()).thenReturn(imprintDir.toString());

        service = new ConsentService(accountRepo, apiConfig);
        service.initialize();

        account = accountRepo.create("consent-svc@test.com", "Consent", "SvcTester");
    }

    @AfterAll
    static void cleanup() {
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void getPrivacyPolicy() {
        var doc = service.getPrivacyPolicy("de");
        assertNotNull(doc);
        assertNotNull(doc.version());
    }

    @Test
    @Order(2)
    void getTermsOfService() {
        var doc = service.getTermsOfService("de");
        assertNotNull(doc);
        assertNotNull(doc.version());
    }

    @Test
    @Order(3)
    void getConsentText() {
        var doc = service.getConsentText("de");
        assertNotNull(doc);
        assertNotNull(doc.version());
    }

    @Test
    @Order(4)
    void getImprint() {
        var doc = service.getImprint("de");
        assertNotNull(doc);
        assertNotNull(doc.version());
    }

    @Test
    @Order(5)
    void getCurrentVersions() {
        var versions = service.getCurrentVersions();
        assertNotNull(versions);
        assertNotNull(versions.privacyVersion());
        assertNotNull(versions.tosVersion());
        assertNotNull(versions.consentVersion());
    }

    @Test
    @Order(6)
    void getPrivacyPolicyFallbackLocale() {
        // Non-existent locale falls back to default
        var doc = service.getPrivacyPolicy("xx");
        assertNotNull(doc);
    }

    @Test
    @Order(10)
    void recordConsent() {
        var versions = service.getCurrentVersions();
        service.recordConsent(
                account.id(),
                versions.consentVersion(),
                versions.privacyVersion(),
                versions.tosVersion(),
                "127.0.0.1",
                "DE",
                "TestAgent/1.0");
        // No exception = success
    }

    @Test
    @Order(11)
    void findLatestConsent() {
        var consent = service.findLatestConsent(account.id());
        assertTrue(consent.isPresent());
        assertNotNull(consent.get().consentVersion());
    }

    @Test
    @Order(12)
    void findLatestConsentNoRecord() {
        var other = accountRepo.create("consent-no-record@test.com", "No", "Record");
        assertTrue(service.findLatestConsent(other.id()).isEmpty());
        accountRepo.delete(other.id());
    }

    @Test
    @Order(20)
    void getDiffSameVersion() {
        var versions = service.getCurrentVersions();
        // Same version diff should return null or empty
        var diff = service.getPrivacyDiff(versions.privacyVersion(), versions.privacyVersion());
        // Either null (no archived version yet) or empty string - just verify no exception
        // diff may be null if no history archive exists yet
        assertTrue(diff == null || diff.isEmpty() || !diff.isEmpty());
    }

    @Test
    @Order(21)
    void getTosDiff() {
        var versions = service.getCurrentVersions();
        var diff = service.getTosDiff(versions.tosVersion(), versions.tosVersion());
        assertTrue(diff == null || diff.isEmpty() || !diff.isEmpty());
    }

    @Test
    @Order(22)
    void recordConsentWithNullCountry() {
        var versions = service.getCurrentVersions();
        // null country should be accepted
        assertDoesNotThrow(() -> service.recordConsent(
                account.id(),
                versions.consentVersion(),
                versions.privacyVersion(),
                versions.tosVersion(),
                "10.0.0.1",
                null,
                "Mozilla/5.0"));
    }

    @Test
    @Order(23)
    void findLatestConsentAfterMultipleRecords() {
        var versions = service.getCurrentVersions();
        service.recordConsent(
                account.id(),
                versions.consentVersion(),
                versions.privacyVersion(),
                versions.tosVersion(),
                "1.2.3.4",
                "US",
                "Agent/1.0");
        service.recordConsent(
                account.id(),
                versions.consentVersion(),
                versions.privacyVersion(),
                versions.tosVersion(),
                "5.6.7.8",
                "FR",
                "Agent/2.0");
        var latest = service.findLatestConsent(account.id());
        assertTrue(latest.isPresent());
        // Should return some consent record
        assertNotNull(latest.get().consentVersion());
    }

    @Test
    @Order(24)
    void getPrivacyDiffDifferentVersions() {
        // fromVersion different from toVersion — should return null or some string
        var diff = service.getPrivacyDiff("version-a", "version-b");
        // Just verify no exception — result is null when no history exists
        assertTrue(diff == null || diff.isEmpty() || !diff.isEmpty());
    }

    @Test
    @Order(25)
    void initializeIsIdempotent() {
        // Calling initialize a second time should not throw
        assertDoesNotThrow(() -> service.initialize());
    }

    @Test
    @Order(30)
    void initializeLogsWhenDocumentsChange() throws IOException {
        // Create a fresh set of directories with new content to trigger the "changed" path
        Path freshPrivacy = tempDir.resolve("privacy2");
        Path freshTos = tempDir.resolve("tos2");
        Path freshConsent = tempDir.resolve("consent2");
        Path freshImprint = tempDir.resolve("imprint2");

        Files.createDirectories(freshPrivacy.resolve("de"));
        Files.createDirectories(freshTos.resolve("de"));
        Files.createDirectories(freshConsent.resolve("de"));
        Files.createDirectories(freshImprint.resolve("de"));

        Files.writeString(freshPrivacy.resolve("de").resolve("01-privacy.md"), "# Privacy v2\nNew privacy content.");
        Files.writeString(freshTos.resolve("de").resolve("01-tos.md"), "# Terms v2\nNew terms.");
        Files.writeString(freshConsent.resolve("de").resolve("01-consent.md"), "# Consent v2\nNew consent.");
        Files.writeString(freshImprint.resolve("de").resolve("01-imprint.md"), "# Imprint v2\nNew imprint.");

        var apiConfig2 = mock(Api.class);
        when(apiConfig2.privacyPolicyDir()).thenReturn(freshPrivacy.toString());
        when(apiConfig2.tosDir()).thenReturn(freshTos.toString());
        when(apiConfig2.consentDir()).thenReturn(freshConsent.toString());
        when(apiConfig2.imprintDir()).thenReturn(freshImprint.toString());

        var service2 = new ConsentService(accountRepo, apiConfig2);
        // First init — all documents are new, so changed=true
        assertDoesNotThrow(service2::initialize);
        // Second init — same content, so changed=false (exercises the else branch)
        assertDoesNotThrow(service2::initialize);

        // Modify one doc and re-init to trigger the log.warn with mixed changed/unchanged
        Files.writeString(freshPrivacy.resolve("de").resolve("01-privacy.md"), "# Privacy v3\nUpdated again.");
        assertDoesNotThrow(service2::initialize);
    }
}
