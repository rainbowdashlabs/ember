/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.documents.repository.SealedVersionRepository;
import dev.chojo.ember.feature.federation.FederationTestTransport;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationSigningService;
import dev.chojo.ember.feature.federation.service.StationKeyStore;
import dev.chojo.ember.feature.federation.transport.PathParams;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.signing.entity.PartnerSealVerdict;
import dev.chojo.ember.feature.signing.entity.PinKind;
import dev.chojo.ember.feature.signing.entity.PinOutcome;
import dev.chojo.ember.feature.signing.entity.PinnedAuthority;
import dev.chojo.ember.feature.signing.entity.RevocationReason;
import dev.chojo.ember.feature.signing.entity.RevokedKey;
import dev.chojo.ember.feature.signing.entity.SealingPartner;
import dev.chojo.ember.feature.signing.entity.SigningAuthorityStatement;
import dev.chojo.ember.feature.signing.entity.StatedAuthority;
import dev.chojo.ember.feature.signing.entity.StatementRefusal;
import dev.chojo.ember.feature.signing.entity.ValidationIndication;
import dev.chojo.ember.feature.signing.entity.ValidationSubIndication;
import dev.chojo.ember.feature.signing.repository.PartnerAuthorityRepository;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.Sha256;
import dev.chojo.ember.util.TestFederationServices;
import dev.chojo.ember.util.TestStationKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CRLException;
import java.security.cert.CertificateEncodingException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pinning a federation partner's signing authorities and checking the documents it seals, between two
 * installations.
 *
 * <p>The organiser's installation is this test's database and services. The partner's installation is
 * kept apart in memory, as {@link PartnerInstallation}: its own authorities, its own station key and its
 * own federation key, so nothing of it is in this database. The only way the organiser reaches it is the
 * stubbed HTTP client, which hands the request's challenge to the partner and returns what it states.
 * Partners on this installation are answered through the real serving function by the local transport.
 */
class PartnerAuthoritiesTest extends RepositoryTestBase {
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String PARTNER_HOST = "https://partner.example";

    private final FederationRepository federationRepo = new FederationRepository();
    private final PartnerAuthorityRepository pins = new PartnerAuthorityRepository();
    private final SigningKeyRepository keys = new SigningKeyRepository();
    private final SigningKeyWrap wrap = new SigningKeyWrap(SECRET);
    private final StationKeyRevocations revocations = new StationKeyRevocations(keys, new RevocationLists(), wrap);
    private final StationSigningKeys signingKeys =
            new StationSigningKeys(keys, new SigningCertificates(), wrap, stationRepo, new Api());
    private final StationKeyStore federationKeys = TestStationKeys.store();
    private final SealVerifier verifier =
            new SealVerifier(keys, revocations, new SealedVersionRepository(), pins, List.of());

    private FederationHttpClient httpClient;
    private FederationTestTransport transport;
    private ExecutorService executor;
    private PartnerAuthorities authorities;
    private PartnerSealValidator validator;
    private Station organiser;
    private PartnerInstallation partner;
    private FederationPartner partnership;

    @BeforeEach
    void pairWithAPartnerOnAnotherInstallation() {
        httpClient = mock(FederationHttpClient.class);
        when(httpClient.canSign(anyInt())).thenReturn(true);
        transport = new FederationTestTransport(httpClient, federationRepo, stationRepo);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        authorities = authoritiesWithin(PartnerAuthorities.BUDGET);
        transport.serve(authorities);
        validator = new PartnerSealValidator(authorities, pins, verifier);
        organiser = stationRepo.create("Organiser " + System.nanoTime());
        partner = new PartnerInstallation();
        partnership = federationRepo.createRemotePartner(
                organiser.id(),
                partner.stationUid,
                federationKeys.ensurePublicKey(organiser.id()),
                partner.federationKey(),
                PARTNER_HOST,
                "Partnerwache",
                FederationContractVersions.current());
    }

    @AfterEach
    void stopAsking() {
        executor.shutdownNow();
    }

    @Test
    void thePartnersAuthorityIsPinnedOnFirstFetchFromItsSignedStatement() throws Exception {
        partnerAnswers(partner::statementTo);

        var outcome = authorities.refresh(partnership);

        assertEquals(PinOutcome.State.TAKEN, outcome.state());
        assertEquals(List.of(partner.currentSha256()), outcome.newlyPinned());
        var pinned = pins.pinnedFor(partnership.id()).getFirst();
        assertEquals(PinKind.FIRST_FETCH, pinned.kind());
        assertTrue(pinned.active());
        assertEquals(new SealingPartner(partner.stationUid, "Partnerwache"), pinned.partner());
        assertTrue(pinned.revocationNextUpdate() != null && !pinned.revocationListDue(Instant.now()));
        assertFalse(authorities.due(partnership.id()), "freshly asked, nothing is due");

        var again = authorities.refresh(partnership);

        assertEquals(List.of(), again.newlyPinned(), "a statement naming the same authority pins nothing new");
        assertEquals(1, pins.pinnedFor(partnership.id()).size());
    }

    @Test
    void aDocumentThePartnerSealedIsAcceptedAsItsSeal() throws Exception {
        partnerAnswers(partner::statementTo);

        var check = validator.validate(partnership, partner.seal());

        assertEquals(PartnerSealVerdict.ACCEPTED, check.verdict());
        assertTrue(check.accepted());
        var seal = check.checked().signatures().getFirst();
        assertEquals(new SealingPartner(partner.stationUid, "Partnerwache"), seal.partner());
        assertFalse(seal.issuedHere(), "a partner's seal is never one issued here");
        assertEquals(ValidationIndication.TOTAL_PASSED, seal.indication());
        assertEquals(1, pins.pinnedFor(partnership.id()).size(), "asked first, since nothing was pinned");
    }

    @Test
    void aSwappedAuthorityIsRefusedOnFirstFetchAndLater() throws Exception {
        var stranger = new PartnerInstallation();
        partnerAnswers(challenge -> stranger.statementAs(partner.stationUid, organiser.uid(), challenge));

        assertEquals(
                StatementRefusal.SIGNATURE, authorities.refresh(partnership).refusal());
        assertEquals(List.of(), pins.pinnedFor(partnership.id()), "nothing pinned from a statement that does not fit");

        partnerAnswers(partner::statementTo);
        authorities.refresh(partnership);
        partnerAnswers(challenge -> partner.withSwappedAuthority(stranger, organiser.uid(), challenge));

        assertEquals(
                StatementRefusal.SIGNATURE, authorities.refresh(partnership).refusal());
        assertEquals(
                List.of(partner.currentSha256()),
                pins.pinnedFor(partnership.id()).stream()
                        .map(PinnedAuthority::sha256)
                        .toList(),
                "the pin is the partner's own authority still");
        var swapped = stranger.sealAs(partner.stationUid);
        assertEquals(
                PartnerSealVerdict.NOT_FROM_PARTNER,
                validator.validate(partnership, swapped).verdict());
    }

    @Test
    void aReIssuedAuthorityIsTakenOnlyFromTheSignedStatement() throws Exception {
        partnerAnswers(partner::statementTo);
        var before = partner.seal();
        authorities.refresh(partnership);

        partner.reissue();
        var after = partner.seal();
        var mitm = new PartnerInstallation();
        partnerAnswers(challenge -> partner.statementSignedBy(mitm, organiser.uid(), challenge));

        assertEquals(
                PartnerSealVerdict.NOT_FROM_PARTNER,
                validator.validate(partnership, after).verdict());
        assertEquals(1, pins.pinnedFor(partnership.id()).size(), "an announcement that does not fit pins nothing");

        partnerAnswers(partner::statementTo);

        assertEquals(
                PartnerSealVerdict.ACCEPTED,
                validator.validate(partnership, after).verdict());
        var pinned = pins.pinnedFor(partnership.id());
        assertEquals(
                List.of(PinKind.FIRST_FETCH, PinKind.ANNOUNCED),
                pinned.stream().map(PinnedAuthority::kind).toList());
        assertEquals(
                List.of(false, true),
                pinned.stream().map(PinnedAuthority::active).toList(),
                "the old one retired");
        assertEquals(
                PartnerSealVerdict.ACCEPTED,
                validator.validate(partnership, before).verdict(),
                "documents sealed under the old authority keep validating");
    }

    @Test
    void aDocumentFromANonPartnerIsRefused() throws Exception {
        partnerAnswers(partner::statementTo);
        var stranger = new PartnerInstallation();

        assertEquals(
                PartnerSealVerdict.NOT_FROM_PARTNER,
                validator.validate(partnership, stranger.seal()).verdict());
        assertEquals(
                PartnerSealVerdict.NOT_FROM_PARTNER,
                validator
                        .validate(partnership, partner.sealAs(UUID.randomUUID()))
                        .verdict(),
                "another station of the partner's installation is not the partner");
        assertEquals(
                PartnerSealVerdict.NO_SEAL,
                validator.validate(partnership, TestSealing.onePagePdf()).verdict());
        assertEquals(
                PartnerSealVerdict.UNREADABLE,
                validator
                        .validate(partnership, "no pdf".getBytes(StandardCharsets.US_ASCII))
                        .verdict());
    }

    @Test
    void anAlteredDocumentOfThePartnerIsRefused() throws Exception {
        partnerAnswers(partner::statementTo);
        var sealed = partner.seal();
        int index = SealedPdfs.indexOf(sealed, SealedPdfs.TITLE.getBytes(StandardCharsets.US_ASCII));
        sealed[index] = 'e';

        assertEquals(
                PartnerSealVerdict.ALTERED,
                validator.validate(partnership, sealed).verdict());
    }

    @Test
    void aRevokedKeyOfThePartnerIsRefusedOnceItsListSaysSo() throws Exception {
        partnerAnswers(partner::statementTo);
        var sealed = partner.seal();
        authorities.refresh(partnership);

        partner.revokeStationKey();
        assertEquals(List.of(), authorities.refresh(partnership).newlyPinned());

        var check = validator.validate(partnership, sealed);
        assertEquals(PartnerSealVerdict.REVOKED, check.verdict());
        assertEquals(
                ValidationSubIndication.REVOKED_NO_POE,
                check.checked().signatures().getFirst().subIndication());
    }

    @Test
    void anOlderRevocationListNeverReplacesANewerOne() throws Exception {
        partnerAnswers(partner::statementTo);
        authorities.refresh(partnership);
        var newer = pins.pinnedFor(partnership.id()).getFirst().revocationList();
        var older = partner.revocationListAt(Instant.now().minus(Duration.ofDays(2)));

        assertFalse(pins.takeRevocationList(
                partnership.id(),
                partner.currentSha256(),
                older,
                Instant.now().minus(Duration.ofDays(2)),
                Instant.now().plus(Duration.ofDays(5))));
        assertArrayEquals(newer, pins.pinnedFor(partnership.id()).getFirst().revocationList());
    }

    @Test
    void aStatementPlayedBackOrMadeForAnotherIsRefused() throws Exception {
        var recorded = new ArrayList<SigningAuthorityStatement>();
        partnerAnswers(challenge -> {
            var statement = partner.statementTo(challenge);
            recorded.add(statement);
            return statement;
        });
        authorities.refresh(partnership);

        partnerAnswers(challenge -> recorded.getFirst());
        assertEquals(
                StatementRefusal.WRONG_CHALLENGE,
                authorities.refresh(partnership).refusal());

        partnerAnswers(challenge -> partner.statementAs(partner.stationUid, UUID.randomUUID(), challenge));
        assertEquals(
                StatementRefusal.WRONG_RECIPIENT,
                authorities.refresh(partnership).refusal());

        partnerAnswers(challenge -> partner.statementAs(UUID.randomUUID(), organiser.uid(), challenge));
        assertEquals(
                StatementRefusal.WRONG_STATION, authorities.refresh(partnership).refusal());

        partnerAnswers(challenge -> partner.statementWith(organiser.uid(), challenge, "yesterday"));
        assertEquals(
                StatementRefusal.NO_MOMENT, authorities.refresh(partnership).refusal());

        partnerAnswers(challenge -> partner.statementNaming(organiser.uid(), challenge, partner.stationCertificate()));
        assertEquals(
                StatementRefusal.NOT_AN_AUTHORITY,
                authorities.refresh(partnership).refusal());

        partnerAnswers(challenge -> partner.statementWithForeignList(organiser.uid(), challenge));
        assertEquals(
                StatementRefusal.NOT_AN_AUTHORITY,
                authorities.refresh(partnership).refusal());

        var keyless = federationRepo
                .createClusterPartner(organiser.id(), UUID.randomUUID(), false)
                .orElseThrow();
        assertEquals(
                StatementRefusal.NO_PARTNER_KEY,
                authorities.take(keyless, recorded.getFirst(), "00").refusal());
    }

    @Test
    void aPartnerThatDoesNotAnswerInTimeLeavesThePinsAsTheyAre() throws Exception {
        partnerAnswers(partner::statementTo);
        authorities.refresh(partnership);
        var released = new CountDownLatch(1);
        partnerAnswers(challenge -> {
            awaitQuietly(released);
            return partner.statementTo(challenge);
        });
        var impatient = authoritiesWithin(Duration.ofMillis(300));

        long started = System.nanoTime();
        var outcome = impatient.refresh(partnership);

        assertEquals(PinOutcome.State.UNANSWERED, outcome.state());
        assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(3)) < 0);
        assertEquals(1, pins.pinnedFor(partnership.id()).size());
        released.countDown();

        doReturn(null)
                .when(httpClient)
                .get(any(), any(FederationRequest.class), any(), anyInt(), eq(SigningAuthorityStatement.class));
        assertEquals(
                PinOutcome.State.UNANSWERED, authorities.refresh(partnership).state());
    }

    @Test
    void theDailyTaskAsksEveryPartnerWithPinsAgain() throws Exception {
        partnerAnswers(partner::statementTo);
        authorities.refresh(partnership);
        partner.reissue();

        authorities.scheduledTasks().stream()
                .filter(task -> task.name().equals("partner-signing-authorities"))
                .map(ScheduledTask::work)
                .findFirst()
                .orElseThrow()
                .run();

        assertEquals(2, pins.pinnedFor(partnership.id()).size(), "the renewed authority is pinned");
    }

    @Test
    void thePublicCheckNamesThePartnerAndStillRefusesStrangers() throws Exception {
        partnerAnswers(partner::statementTo);
        authorities.refresh(partnership);

        var seal = verifier.verify(partner.seal()).signatures().getFirst();

        assertEquals(new SealingPartner(partner.stationUid, "Partnerwache"), seal.partner());
        assertFalse(seal.issuedHere());
        assertEquals(ValidationIndication.TOTAL_PASSED, seal.indication());

        var stranger =
                verifier.verify(new PartnerInstallation().seal()).signatures().getFirst();
        assertNull(stranger.partner());
        assertEquals(ValidationSubIndication.NOT_ISSUED_HERE, stranger.subIndication());
        var otherStation =
                verifier.verify(partner.sealAs(UUID.randomUUID())).signatures().getFirst();
        assertNull(otherStation.partner(), "the partner's authority vouches for the partner station only");
        assertEquals(ValidationSubIndication.NOT_ISSUED_HERE, otherStation.subIndication());
    }

    @Test
    void aPartnerOnThisInstallationStatesItsAuthoritiesThroughTheServingFunction() throws Exception {
        var federation = TestFederationServices.of(federationRepo, stationRepo, federationKeys, new Api());
        var home = stationRepo.create("Home " + System.nanoTime());
        federation.acceptInvite(organiser.id(), home.id(), federationKeys.ensurePublicKey(home.id()), null, null);
        var local = federationRepo
                .findPartnerByStationAndRemoteUid(home.id(), organiser.uid())
                .orElseThrow();
        var sealed = SealedPdfs.sealedWith(signingKeys.forStation(organiser.id()));

        var outcome = authorities.refresh(local);

        assertEquals(PinOutcome.State.TAKEN, outcome.state());
        var installationAuthority = keys.authorityCertificates().getFirst().certificate();
        assertEquals(List.of(Sha256.hex(installationAuthority)), outcome.newlyPinned());
        var check = validator.validate(local, sealed);
        assertEquals(PartnerSealVerdict.ACCEPTED, check.verdict());
        assertEquals(
                organiser.uid(),
                check.checked().signatures().getFirst().partner().stationUid());
        assertTrue(verifier.verify(sealed).signatures().getFirst().issuedHere());
        assertNull(verifier.verify(sealed).signatures().getFirst().partner(), "issued here wins in the public check");
    }

    @Test
    void theServingFunctionRefusesAMalformedChallengeAndAStationWithoutKey() throws Exception {
        var federation = TestFederationServices.of(federationRepo, stationRepo, federationKeys, new Api());
        var home = stationRepo.create("Serving " + System.nanoTime());
        federation.acceptInvite(organiser.id(), home.id(), federationKeys.ensurePublicKey(home.id()), null, null);
        var served = federationRepo
                .findPartnerByStationAndRemoteUid(organiser.id(), home.uid())
                .orElseThrow();
        var asking = new ServingPartner(served, home.uid());

        assertRefused(DocumentRefusal.AUTHORITY_CHALLENGE_MALFORMED, () -> authorities.statementFor(asking, "xyz"));
        assertRefused(DocumentRefusal.AUTHORITY_CHALLENGE_MALFORMED, () -> authorities.statementFor(asking, null));

        var keyless = stationRepo.create("Keyless " + System.nanoTime());
        var cluster = federationRepo
                .createClusterPartner(keyless.id(), home.uid(), false)
                .orElseThrow();
        assertRefused(
                DocumentRefusal.AUTHORITIES_CANNOT_BE_VOUCHED_FOR,
                () -> authorities.statementFor(new ServingPartner(cluster, home.uid()), "0".repeat(64)));
    }

    @Test
    void aClusterPartnerIsCheckedAgainstThisInstallationsAuthorities() throws Exception {
        var member = stationRepo.create("Cluster member " + System.nanoTime());
        var cluster = federationRepo
                .createClusterPartner(organiser.id(), member.uid(), false)
                .orElseThrow();

        var check = validator.validate(cluster, SealedPdfs.sealedWith(signingKeys.forStation(member.id())));

        assertEquals(PartnerSealVerdict.ACCEPTED, check.verdict());
        assertEquals(List.of(), pins.pinnedFor(cluster.id()), "nothing is asked or pinned for a cluster pair");
        assertEquals(
                PartnerSealVerdict.NOT_FROM_PARTNER,
                validator.validate(cluster, partner.seal()).verdict());
    }

    private PartnerAuthorities authoritiesWithin(Duration budget) {
        return new PartnerAuthorities(
                transport.transport(),
                federationRepo,
                stationRepo,
                TestStationKeys.signer(),
                new FederationSigningService(),
                keys,
                revocations,
                pins,
                executor,
                Clock.systemUTC(),
                budget);
    }

    private void partnerAnswers(Function<String, SigningAuthorityStatement> answer) {
        doAnswer(call -> answer.apply(challengeOf(call.getArgument(1))))
                .when(httpClient)
                .get(
                        eq(PARTNER_HOST),
                        any(FederationRequest.class),
                        eq(partner.stationUid),
                        eq(organiser.id()),
                        eq(SigningAuthorityStatement.class));
    }

    private static String challengeOf(FederationRequest request) {
        return PathParams.of(request).query("challenge").orElseThrow();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void assertRefused(DocumentRefusal refusal, Executable call) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, call).refusal());
    }

    /**
     * A partner's installation, held apart from this test's database: its authorities, the station key of
     * the partner station and that station's federation key. It states and seals the way an installation
     * running this code does.
     */
    private final class PartnerInstallation {
        private static final String INSTALLATION = "partner.example";

        private final SigningCertificates certificates = new SigningCertificates();
        private final RevocationLists lists = new RevocationLists();
        private final KeyPair federationKey = rsa();
        private final UUID stationUid = UUID.randomUUID();
        private final List<SigningCertificates.Issued> authorities = new ArrayList<>();
        private final List<RevokedKey> revoked = new ArrayList<>();
        private SigningCertificates.Issued stationKey;
        private long listNumber = 1;

        PartnerInstallation() {
            reissue();
        }

        /** Gives the current authority up and issues a new one with a new station key, as a recovery does. */
        void reissue() {
            authorities.addFirst(certificates.authority(INSTALLATION));
            stationKey = keyFor(stationUid);
        }

        void revokeStationKey() {
            revoked.add(new RevokedKey(
                    SigningCertificates.serialOf(stationKey.certificate()),
                    Instant.now(),
                    RevocationReason.KEY_COMPROMISE));
        }

        String federationKey() {
            return Base64.getEncoder().encodeToString(federationKey.getPublic().getEncoded());
        }

        String currentSha256() {
            return Sha256.hex(der(authorities.getFirst()));
        }

        byte[] stationCertificate() {
            try {
                return stationKey.certificate().getEncoded();
            } catch (CertificateEncodingException e) {
                throw new IllegalStateException(e);
            }
        }

        SigningAuthorityStatement statementTo(String challenge) {
            return statementAs(stationUid, organiser.uid(), challenge);
        }

        SigningAuthorityStatement statementAs(UUID station, UUID recipient, String challenge) {
            return signed(
                    federationKey, station, recipient, challenge, Instant.now().toString(), stated());
        }

        SigningAuthorityStatement statementWith(UUID recipient, String challenge, String issuedAt) {
            return signed(federationKey, stationUid, recipient, challenge, issuedAt, stated());
        }

        SigningAuthorityStatement statementSignedBy(PartnerInstallation other, UUID recipient, String challenge) {
            return signed(
                    other.federationKey,
                    stationUid,
                    recipient,
                    challenge,
                    Instant.now().toString(),
                    stated());
        }

        SigningAuthorityStatement statementNaming(UUID recipient, String challenge, byte[] certificate) {
            var stated = List.of(new StatedAuthority(certificate, true, null));
            return signed(
                    federationKey,
                    stationUid,
                    recipient,
                    challenge,
                    Instant.now().toString(),
                    stated);
        }

        SigningAuthorityStatement statementWithForeignList(UUID recipient, String challenge) {
            var foreign = new PartnerInstallation().revocationListAt(Instant.now());
            var stated = List.of(new StatedAuthority(der(authorities.getFirst()), true, foreign));
            return signed(
                    federationKey,
                    stationUid,
                    recipient,
                    challenge,
                    Instant.now().toString(),
                    stated);
        }

        /** A statement whose authority somebody in between swapped for another's, keeping the signature. */
        SigningAuthorityStatement withSwappedAuthority(PartnerInstallation other, UUID recipient, String challenge) {
            var genuine = statementAs(stationUid, recipient, challenge);
            return new SigningAuthorityStatement(
                    genuine.stationUid(),
                    genuine.recipientUid(),
                    genuine.challenge(),
                    genuine.issuedAt(),
                    other.stated(),
                    genuine.signature());
        }

        byte[] seal() throws Exception {
            return sealWith(stationKey);
        }

        byte[] sealAs(UUID station) throws Exception {
            return sealWith(keyFor(station));
        }

        byte[] revocationListAt(Instant thisUpdate) {
            return listOf(authorities.getFirst(), thisUpdate);
        }

        private SigningCertificates.Issued keyFor(UUID station) {
            return certificates.station(
                    INSTALLATION,
                    "Partnerwache",
                    station,
                    authorities.getFirst(),
                    URI.create("https://" + INSTALLATION + "/api/v1/public/signing/ca/1.crl"));
        }

        private byte[] sealWith(SigningCertificates.Issued key) throws Exception {
            return SealedPdfs.sealer(SealedPdfs.noTimestamps())
                    .sealWithoutTimestamp(
                            SealedPdfs.onePagePdf(),
                            key.privateKey(),
                            List.of(key.certificate(), authorities.getFirst().certificate()))
                    .pdf();
        }

        private List<StatedAuthority> stated() {
            return authorities.stream()
                    .map(authority -> new StatedAuthority(
                            der(authority), authority == authorities.getFirst(), listOf(authority, Instant.now())))
                    .toList();
        }

        private byte[] listOf(SigningCertificates.Issued authority, Instant thisUpdate) {
            try {
                return lists.issue(authority, listNumber++, revoked, thisUpdate).getEncoded();
            } catch (CRLException e) {
                throw new IllegalStateException(e);
            }
        }

        private static SigningAuthorityStatement signed(
                KeyPair key,
                UUID station,
                UUID recipient,
                String challenge,
                String issuedAt,
                List<StatedAuthority> stated) {
            var text = AuthorityStatements.text(station, recipient, challenge, issuedAt, stated);
            return new SigningAuthorityStatement(
                    station,
                    recipient,
                    challenge,
                    issuedAt,
                    stated,
                    new FederationSigningService().signStatement(text, key.getPrivate()));
        }

        private static byte[] der(SigningCertificates.Issued issued) {
            try {
                return issued.certificate().getEncoded();
            } catch (CertificateEncodingException e) {
                throw new IllegalStateException(e);
            }
        }

        private static KeyPair rsa() {
            try {
                var generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                return generator.generateKeyPair();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
