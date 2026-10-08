/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.signing.entity.RevocationReason;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.feature.signing.service.PublishedCertificates;
import dev.chojo.ember.feature.signing.service.RevocationLists;
import dev.chojo.ember.feature.signing.service.SigningCertificates;
import dev.chojo.ember.feature.signing.service.SigningKeyWrap;
import dev.chojo.ember.feature.signing.service.StationKeyRevocations;
import dev.chojo.ember.feature.signing.service.StationSigningKeys;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.route.PublicStationRoutes;
import dev.chojo.ember.feature.station.service.PublicStationInfoService;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.testtools.Response;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The public addresses a reader of a sealed document checks its seal against, over HTTP through the
 * real services and the database: the authorities with their certificates and revocation lists, a
 * station's certificates, the refusals for what does not exist, the station's public card naming the
 * authority it seals under, and that no answer carries private key material.
 */
class PublicSigningRoutesTest extends RepositoryTestBase {
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);

    private final SigningKeyRepository repository = new SigningKeyRepository();
    private final SigningKeyWrap wrap = new SigningKeyWrap(SECRET);
    private final StationSigningKeys signingKeys =
            new StationSigningKeys(repository, new SigningCertificates(), wrap, stationRepo, new Api());
    private final StationKeyRevocations revocations =
            new StationKeyRevocations(repository, new RevocationLists(), wrap);
    private final PublishedCertificates published = new PublishedCertificates(repository, revocations, stationRepo);
    private final RouteHarness harness = RouteHarness.serving(
            new PublicSigningRoutes(published),
            new PublicStationRoutes(new PublicStationInfoService(
                    stationRepo,
                    mock(StationLogoService.class),
                    mock(PageService.class),
                    mock(WaitingListService.class),
                    mock(NewsService.class),
                    mock(FormService.class),
                    published)));
    private final List<Integer> stations = new ArrayList<>();

    @BeforeEach
    void startWithoutAuthority() {
        query("DELETE FROM station_signing_key;").single(call()).delete();
        query("DELETE FROM signing_ca;").single(call()).delete();
    }

    @AfterEach
    void removeStations() {
        stations.forEach(stationRepo::delete);
    }

    @Test
    void theAddressInAFreshStationCertificateServesItsAuthoritysRevocationList() throws Exception {
        var key = signingKeys.forStation(station("Distribution point station").id());
        var address = URI.create(distributionPointOf(key.certificate()));

        var answer = fetch(address.getPath());

        assertEquals(200, answer.code());
        assertEquals(PublicSigningRoutes.REVOCATION_LIST_TYPE, answer.header("Content-Type"));
        var list = crlOf(answer.body());
        list.verify(key.authority().getPublicKey());
        assertEquals(key.authority().getSubjectX500Principal(), list.getIssuerX500Principal());
    }

    @Test
    void theServedRevocationListIsTheStoredOneAndNamesARevokedKey() throws Exception {
        var station = station("Revoking station");
        var leaked = signingKeys.forStation(station.id());
        revocations.revoke(station.id(), serialOf(leaked.certificate()), RevocationReason.KEY_COMPROMISE);

        var answer = fetch(crlPath(serialOf(leaked.authority())));

        var served = answer.body();
        var authority =
                repository.findAuthorityBySerial(serialOf(leaked.authority())).orElseThrow();
        assertArrayEquals(
                repository.findRevocationList(authority.id()).orElseThrow().list(), served);
        assertNotNull(crlOf(served).getRevokedCertificate(leaked.certificate().getSerialNumber()));
        assertTrue(answer.header("Content-Disposition").startsWith("attachment"));
        assertTrue(answer.header("Cache-Control").startsWith("public"));
    }

    @Test
    void theAuthorityCertificateIsServedAsStoredWhateverCaseItsSerialIsWrittenIn() throws Exception {
        var key = signingKeys.forStation(station("Authority station").id());
        var serial = serialOf(key.authority());

        var answer = fetch(PREFIX + "/public/signing/ca/" + serial.toUpperCase(Locale.ROOT) + ".crt");

        assertEquals(200, answer.code());
        assertEquals(PublicSigningRoutes.CERTIFICATE_TYPE, answer.header("Content-Type"));
        var served = answer.body();
        assertArrayEquals(repository.authorityCertificate(serial).orElseThrow(), served);
        assertArrayEquals(key.authority().getEncoded(), served);
    }

    @Test
    void theAuthoritiesAreListedWithSubjectFingerprintAndValidity() throws Exception {
        var key = signingKeys.forStation(station("Listing station").id());

        var listed = json(get(PREFIX + "/public/signing/ca"));

        assertEquals(1, listed.size());
        var authority = listed.get(0);
        assertEquals(serialOf(key.authority()), authority.path("serialNumber").asString());
        assertEquals(
                key.authority().getSubjectX500Principal().getName(),
                authority.path("subject").asString());
        assertEquals(
                fingerprintOf(key.authority()),
                authority.path("sha256Fingerprint").asString());
        assertEquals(
                key.authority().getNotAfter().toInstant().toString(),
                authority.path("validUntil").asString());
        assertTrue(authority.path("active").asBoolean());
    }

    @Test
    void aStationListsEveryCertificateItsKeysHadWithTheRevokedOneMarked() throws Exception {
        var station = station("Rotating station");
        var first = signingKeys.forStation(station.id());
        revocations.revoke(station.id(), serialOf(first.certificate()), RevocationReason.SUPERSEDED);
        var second = signingKeys.forStation(station.id());

        var listed = json(get(stationCertificatesPath(station.uid().toString())));

        assertEquals(2, listed.size());
        assertEquals(
                serialOf(second.certificate()),
                listed.get(0).path("serialNumber").asString());
        assertTrue(listed.get(0).path("revokedAt").isNull());
        assertEquals(
                serialOf(first.certificate()),
                listed.get(1).path("serialNumber").asString());
        assertFalse(listed.get(1).path("revokedAt").isNull());
        assertEquals(
                fingerprintOf(first.certificate()),
                listed.get(1).path("sha256Fingerprint").asString());
        assertEquals(
                serialOf(first.authority()),
                listed.get(1).path("authoritySerialNumber").asString());
    }

    @Test
    void aStationCertificateIsServedAsStored() throws Exception {
        var station = station("Certificate station");
        var key = signingKeys.forStation(station.id());

        var answer =
                fetch(stationCertificatesPath(station.uid().toString()) + "/" + serialOf(key.certificate()) + ".crt");

        assertEquals(200, answer.code());
        assertEquals(PublicSigningRoutes.CERTIFICATE_TYPE, answer.header("Content-Type"));
        assertArrayEquals(key.certificate().getEncoded(), answer.body());
    }

    @Test
    void unknownSerialsAreRefusedAsNotFound() {
        signingKeys.forStation(station("Known station").id());

        assertRefused(get(crlPath("1a2b3c")), DocumentRefusal.SIGNING_AUTHORITY_NOT_HERE);
        assertRefused(get(crlPath("not-hex")), DocumentRefusal.SIGNING_AUTHORITY_NOT_HERE);
        assertRefused(get(PREFIX + "/public/signing/ca/1a2b3c.crt"), DocumentRefusal.SIGNING_AUTHORITY_NOT_HERE);
    }

    @Test
    void aCertificateOfAnotherStationIsRefusedAsNotFound() {
        var own = station("Own station");
        signingKeys.forStation(own.id());
        var other = signingKeys.forStation(station("Other station").id());

        assertRefused(
                get(stationCertificatesPath(own.uid().toString()) + "/" + serialOf(other.certificate()) + ".crt"),
                DocumentRefusal.SEAL_CERTIFICATE_NOT_HERE);
    }

    @Test
    void aStationThatNeverSealedIsRefusedAndGetsNoKeyFromBeingAsked() {
        var station = station("Never sealing station");

        assertRefused(get(stationCertificatesPath(station.uid().toString())), DocumentRefusal.SEALING_STATION_NOT_HERE);
        assertRefused(
                get(stationCertificatesPath(station.uid().toString()) + "/1a2b3c.crt"),
                DocumentRefusal.SEALING_STATION_NOT_HERE);
        assertRefused(
                get(stationCertificatesPath(UUID.randomUUID().toString())), DocumentRefusal.SEALING_STATION_NOT_HERE);
        assertTrue(repository.stationCertificates(station.id()).isEmpty());
        assertTrue(repository.authorityCertificates().isEmpty());
    }

    @Test
    void theStationsPublicCardNamesItsAuthorityOnlyOnceItHasAKey() throws Exception {
        var station = station("Public card station");
        stationRepo.updatePublicCalendarEnabled(station.id(), true);

        var before = json(get(PREFIX + "/public/station/" + station.uid() + "/info"));
        assertEquals(0, before.path("sealAuthorities").size());
        assertTrue(repository.authorityCertificates().isEmpty(), "showing the card creates no key");

        var key = signingKeys.forStation(station.id());
        var after = json(get(PREFIX + "/public/station/" + station.uid() + "/info"));

        assertEquals(1, after.path("sealAuthorities").size());
        var authority = after.path("sealAuthorities").get(0);
        assertEquals(serialOf(key.authority()), authority.path("serialNumber").asString());
        assertEquals(
                fingerprintOf(key.authority()),
                authority.path("sha256Fingerprint").asString());
    }

    @Test
    void noAnswerCarriesPrivateKeyMaterial() throws Exception {
        var station = station("Private key station");
        var key = signingKeys.forStation(station.id());
        stationRepo.updatePublicCalendarEnabled(station.id(), true);
        var authority =
                repository.findAuthorityBySerial(serialOf(key.authority())).orElseThrow();
        var stationKey = repository.findActive(station.id()).orElseThrow();
        var secrets = List.of(
                key.privateKey().getEncoded(),
                wrap.open(authority.key()).privateKey().getEncoded(),
                authority.key().wrappedPrivateKey(),
                stationKey.key().wrappedPrivateKey());
        var stationPath = stationCertificatesPath(station.uid().toString());

        var bodies = List.of(
                fetch(PREFIX + "/public/signing/ca").body(),
                fetch(PREFIX + "/public/signing/ca/" + serialOf(key.authority()) + ".crt")
                        .body(),
                fetch(crlPath(serialOf(key.authority()))).body(),
                fetch(stationPath).body(),
                fetch(stationPath + "/" + serialOf(key.certificate()) + ".crt").body(),
                fetch(PREFIX + "/public/station/" + station.uid() + "/info").body());

        for (var body : bodies) {
            var text = new String(body, StandardCharsets.ISO_8859_1);
            assertFalse(text.contains("PRIVATE"), "no PEM private key");
            assertFalse(text.toLowerCase(Locale.ROOT).contains("wrapped"), "no wrapped key field");
            for (var secret : secrets) {
                assertFalse(contains(body, secret), "no private key bytes");
                assertFalse(text.contains(Base64.getEncoder().encodeToString(secret)), "no private key in base64");
            }
        }
    }

    private Station station(String name) {
        var station = stationRepo.create(name + " " + System.nanoTime());
        stations.add(station.id());
        return station;
    }

    private Response get(String path) {
        return harness.request(client -> client.get(path));
    }

    private static String crlPath(String serial) {
        return PREFIX + "/public/signing/ca/" + serial + ".crl";
    }

    private static String stationCertificatesPath(String stationUid) {
        return PREFIX + "/public/station/" + stationUid + "/signing/certificates";
    }

    private static void assertRefused(Response answer, DocumentRefusal refusal) {
        assertEquals(404, answer.code());
        assertEquals(refusal, refusalOf(answer));
    }

    /**
     * Sends a GET past the harness's client, which reads every body as text, so a DER answer arrives
     * byte for byte.
     */
    private Fetched fetch(String path) {
        var fetched = new AtomicReference<Fetched>();
        harness.run((server, client) -> {
            try (var http = HttpClient.newHttpClient()) {
                var response = http.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path))
                                .build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                fetched.set(new Fetched(response.statusCode(), response.body(), response.headers()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
        return fetched.get();
    }

    /**
     * An answer with its body as bytes.
     *
     * @param code    the status code
     * @param body    the body
     * @param headers the headers
     */
    private record Fetched(int code, byte[] body, HttpHeaders headers) {
        String header(String name) {
            return headers.firstValue(name).orElse("");
        }
    }

    private static X509CRL crlOf(byte[] der) throws Exception {
        return (X509CRL) CertificateFactory.getInstance("X.509").generateCRL(new ByteArrayInputStream(der));
    }

    private static String serialOf(X509Certificate certificate) {
        return certificate.getSerialNumber().toString(16);
    }

    private static String fingerprintOf(X509Certificate certificate) throws Exception {
        return HexFormat.ofDelimiter(":")
                .withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    }

    private static String distributionPointOf(X509Certificate certificate) throws IOException {
        var extension = certificate.getExtensionValue(Extension.cRLDistributionPoints.getId());
        var points = CRLDistPoint.getInstance(JcaX509ExtensionUtils.parseExtensionValue(extension));
        var names = GeneralNames.getInstance(
                points.getDistributionPoints()[0].getDistributionPoint().getName());
        return Arrays.stream(names.getNames())
                .filter(name -> name.getTagNo() == GeneralName.uniformResourceIdentifier)
                .map(name -> name.getName().toString())
                .findFirst()
                .orElseThrow();
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int start = 0; start <= haystack.length - needle.length; start++) {
            for (int i = 0; i < needle.length; i++) {
                if (haystack[start + i] != needle[i]) continue outer;
            }
            return true;
        }
        return false;
    }
}
