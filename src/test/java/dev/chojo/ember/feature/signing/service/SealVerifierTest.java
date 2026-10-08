/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.TestUploads;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.documents.repository.SealedVersionRepository;
import dev.chojo.ember.feature.signing.entity.PadesLevel;
import dev.chojo.ember.feature.signing.entity.RevocationStatus;
import dev.chojo.ember.feature.signing.entity.ValidationIndication;
import dev.chojo.ember.feature.signing.entity.ValidationSubIndication;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SubIndication;
import io.javalin.http.UploadedFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the route tests do not reach: the names the validator's results are mapped by, uploads that never
 * arrive whole, and an authority whose key no longer opens.
 */
class SealVerifierTest extends RepositoryTestBase {
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);

    private final SigningKeyRepository repository = new SigningKeyRepository();
    private final SigningKeyWrap wrap = new SigningKeyWrap(SECRET);
    private final StationSigningKeys signingKeys = new StationSigningKeys(
            repository, new SigningCertificates(), wrap, stationRepo, "https://ember.example.org");
    private final SealVerifier verifier = new SealVerifier(
            repository,
            new StationKeyRevocations(repository, new RevocationLists(), wrap),
            new SealedVersionRepository(),
            List.of());

    @BeforeEach
    void startWithoutAuthority() {
        query("DELETE FROM station_signing_key;").single(call()).delete();
        query("DELETE FROM signing_ca;").single(call()).delete();
    }

    @Test
    void everyResultTheValidatorGivesHasAName() {
        assertEquals(names(Indication.values()), names(ValidationIndication.values()));
        var subIndications = names(SubIndication.values());
        subIndications.add(ValidationSubIndication.NOT_ISSUED_HERE.name());
        assertEquals(subIndications, names(ValidationSubIndication.values()));
        var baselines = Arrays.stream(SignatureLevel.values())
                .map(Enum::name)
                .filter(name -> name.startsWith("PAdES_BASELINE_"))
                .map(name -> name.substring("PAdES_".length()))
                .collect(Collectors.toSet());
        baselines.add(PadesLevel.NOT_BASELINE.name());
        assertEquals(baselines, names(PadesLevel.values()));
    }

    @Test
    void anUploadThatIsMissingTooLargeOrBrokenOffIsRefused() {
        assertRefused(DocumentRefusal.SEAL_CHECK_NO_FILE, () -> verifier.verify((UploadedFile) null));
        assertRefused(
                DocumentRefusal.SEAL_CHECK_TOO_LARGE,
                () -> verifier.verify(TestUploads.unreadable("big.pdf", SealVerifier.MAX_BYTES + 1L)));
        assertRefused(
                DocumentRefusal.SEAL_CHECK_NOT_RECEIVED,
                () -> verifier.verify(TestUploads.unreadable("broken.pdf", 100)));
        assertRefused(
                DocumentRefusal.SEAL_CHECK_TOO_LARGE, () -> verifier.verify(new byte[SealVerifier.MAX_BYTES + 1]));
    }

    @Test
    void aSealIsStillCheckedWhenItsAuthoritysListCannotBeSigned() throws Exception {
        var station = stationRepo.create("Lost key file station " + System.nanoTime());
        var key = signingKeys.forStation(station.id());
        var sealed = SealedPdfs.sealedWith(key);
        var otherSecret = new SigningKeyWrap(Base64.getEncoder().encodeToString(new byte[] {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
            30, 31, 32
        }));
        var withoutList = new SealVerifier(
                repository,
                new StationKeyRevocations(repository, new RevocationLists(), otherSecret),
                new SealedVersionRepository(),
                List.of());

        var check = withoutList.verify(sealed).signatures().getFirst();

        assertTrue(check.issuedHere());
        assertTrue(check.intact());
        assertEquals(RevocationStatus.UNKNOWN, check.revocation().status());
        assertEquals(ValidationIndication.INDETERMINATE, check.indication());
        stationRepo.delete(station.id());
    }

    private static void assertRefused(DocumentRefusal refusal, Executable call) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, call).refusal());
    }

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toCollection(HashSet::new));
    }
}
