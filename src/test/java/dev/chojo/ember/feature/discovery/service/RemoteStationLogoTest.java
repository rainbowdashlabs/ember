/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.chojo.ember.feature.discovery.entity.DiscoveryPeer;
import dev.chojo.ember.feature.discovery.entity.DiscoveryStationCard;
import dev.chojo.ember.feature.discovery.entity.PeerSource;
import dev.chojo.ember.feature.discovery.entity.PictureTags;
import dev.chojo.ember.feature.discovery.entity.RemoteLogoCheck;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryIdentity;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryStationsResponse;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.Json;
import dev.chojo.ember.util.TestRemoteUrlValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The copies this instance keeps of the logos of other instances' stations, fetched from a stub standing
 * in for the other instance, through the real discovery client and the real picture pipeline.
 */
class RemoteStationLogoTest extends RepositoryTestBase {
    private static final UUID FIRST = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID SECOND = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private HttpServer server;
    private String base;
    private String key;
    private DiscoveryPeer peer;
    private final List<DiscoveryStationCard> listed = new CopyOnWriteArrayList<>();
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    private final List<String> asked = new CopyOnWriteArrayList<>();
    private final List<String> tagsAskedWith = new CopyOnWriteArrayList<>();
    private RemoteStationLogoService logos;
    private DiscoveryStationFetcher fetcher;

    /** What the stub answers a request for a logo with. */
    private record Answer(int status, String contentType, byte[] body, Map<String, String> headers) {
        static Answer of(String contentType, byte[] body) {
            return new Answer(200, contentType, body, Map.of());
        }

        static Answer status(int status) {
            return new Answer(status, "text/plain", new byte[0], Map.of());
        }
    }

    @BeforeEach
    void startTheOtherInstance() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::answer);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        key = "logo-peer-" + UUID.randomUUID();
        peer = discoveryPeerRepo.upsert(key, base, "logo-instance", PeerSource.MANUAL, null);

        var settings = mock(DiscoverySettingsService.class);
        when(settings.isEnabled()).thenReturn(true);
        var http = new DiscoveryHttpClient(
                mock(DiscoverySigningService.class), TestRemoteUrlValidator.permissiveOutbound());
        var backend = localStorage();
        var images = new ImageVariants(new StorageService(new StorageBackendResolver(backend), backend));
        logos = new RemoteStationLogoService(http, images, discoveryStationCacheRepo, new TaskScheduler());
        fetcher = new DiscoveryStationFetcher(
                http,
                discoveryPeerRepo,
                discoveryStationCacheRepo,
                mock(DiscoveryReputationService.class),
                settings,
                logos);
    }

    @AfterEach
    void stopTheOtherInstance() {
        server.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/api/v1/public/discovery/stations")) {
            byte[] body = Json.MAPPER.writeValueAsBytes(new DiscoveryStationsResponse(
                    new DiscoveryIdentity(base, key, "logo-instance"), new ArrayList<>(listed)));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            return;
        }
        asked.add(exchange.getRequestURI().toString());
        String tag = exchange.getRequestHeaders().getFirst("If-None-Match");
        if (tag != null) tagsAskedWith.add(tag);
        var answer = answers.getOrDefault(path, Answer.status(404));
        exchange.getResponseHeaders().add("Content-Type", answer.contentType());
        answer.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        exchange.sendResponseHeaders(answer.status(), answer.body().length == 0 ? -1 : answer.body().length);
        if (answer.body().length > 0) exchange.getResponseBody().write(answer.body());
        exchange.close();
    }

    private void lists(UUID station, String logoUrl) {
        listed.add(new DiscoveryStationCard(
                station.toString(),
                "Wache " + station,
                null,
                logoUrl,
                "DE",
                null,
                "Nordstadt",
                base + "/public/station/" + station,
                List.of(),
                "<10",
                Instant.now(),
                null,
                null,
                null));
    }

    private static byte[] picture(int width, int height, String format) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private byte[] kept(UUID station) {
        return logos.read(RemoteStationLogoService.fingerprint(key), station, 0)
                .orElseThrow()
                .data();
    }

    private boolean keeps(UUID station) {
        return logos.read(RemoteStationLogoService.fingerprint(key), station, 0).isPresent();
    }

    private RemoteLogoCheck check(UUID station) {
        return discoveryStationCacheRepo.findLogosDue(key, Instant.now()).stream()
                .filter(c -> c.stationUid().equals(station.toString()))
                .findFirst()
                .orElseThrow();
    }

    private void makeDue(UUID station) {
        var check = check(station);
        discoveryStationCacheRepo.recordLogoCheck(
                key,
                station.toString(),
                Instant.now().minus(RemoteStationLogoService.RECHECK_AFTER).minus(Duration.ofMinutes(1)),
                check.stored(),
                check.tags());
    }

    @Test
    void theLogoOfANewCardIsFetchedDrawnAgainAsASquareAndKept() throws IOException {
        byte[] served = picture(600, 300, "jpg");
        answers.put("/logo/first", new Answer(200, "image/jpeg", served, Map.of("ETag", "\"v1\"")));
        lists(FIRST, base + "/logo/first");

        fetcher.refresh(peer);

        byte[] copy = kept(FIRST);
        assertFalse(Arrays.equals(served, copy), "no byte that came in is served");
        var drawn = ImageIO.read(new ByteArrayInputStream(copy));
        assertEquals(256, drawn.getWidth());
        assertEquals(256, drawn.getHeight());
        assertEquals(List.of("/logo/first?size=256"), asked);
        assertEquals(new PictureTags("\"v1\"", null), check(FIRST).tags());
        assertTrue(check(FIRST).stored());
    }

    @Test
    void aKeptLogoIsAskedForAgainOnlyOnceItIsDueAndThenWithItsTag() throws IOException {
        answers.put("/logo/first", new Answer(200, "image/png", picture(64, 64, "png"), Map.of("ETag", "\"v1\"")));
        lists(FIRST, base + "/logo/first");
        fetcher.refresh(peer);
        byte[] before = kept(FIRST);

        fetcher.refresh(peer);
        assertEquals(1, asked.size(), "a logo checked within the interval is not asked for");

        makeDue(FIRST);
        answers.put("/logo/first", Answer.status(304));
        fetcher.refresh(peer);

        assertEquals(2, asked.size());
        assertEquals(List.of("\"v1\""), tagsAskedWith);
        assertArrayEquals(before, kept(FIRST));
        assertTrue(check(FIRST).stored());
    }

    @Test
    void anOversizedADrawingAndSomethingElseEntirelyAreNotTaken() throws IOException {
        byte[] oversized = Arrays.copyOf(picture(8, 8, "png"), RemoteStationLogoService.MAX_LOGO_BYTES + 1);
        byte[] drawing = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        answers.put("/logo/large", Answer.of("image/png", oversized));
        answers.put("/logo/drawing", Answer.of("image/svg+xml", drawing));
        answers.put("/logo/text", Answer.of("image/png", "not a picture".getBytes(StandardCharsets.UTF_8)));
        UUID third = UUID.randomUUID();
        lists(FIRST, base + "/logo/large");
        lists(SECOND, base + "/logo/drawing");
        lists(third, base + "/logo/text");

        fetcher.refresh(peer);

        assertEquals(3, asked.size());
        for (UUID station : List.of(FIRST, SECOND, third)) {
            assertFalse(keeps(station), "nothing is kept for " + station);
            assertFalse(check(station).stored());
        }
    }

    @Test
    void aLogoThatCannotBeFetchedLeavesTheKeptOneInPlace() throws IOException {
        answers.put("/logo/first", new Answer(200, "image/png", picture(64, 64, "png"), Map.of("ETag", "\"v1\"")));
        lists(FIRST, base + "/logo/first");
        fetcher.refresh(peer);
        byte[] before = kept(FIRST);

        for (var failure : List.of(
                Answer.status(500),
                new Answer(302, "text/plain", new byte[0], Map.of("Location", "https://elsewhere.example/logo")),
                Answer.of("image/png", "broken".getBytes(StandardCharsets.UTF_8)))) {
            makeDue(FIRST);
            answers.put("/logo/first", failure);

            assertEquals(1, fetcher.refresh(peer), "the card refresh itself succeeds");
            assertArrayEquals(before, kept(FIRST));
            assertEquals(new PictureTags("\"v1\"", null), check(FIRST).tags());
        }
    }

    @Test
    void aLogoTheOtherInstanceNoLongerHasIsDropped() throws IOException {
        answers.put("/logo/first", Answer.of("image/png", picture(64, 64, "png")));
        lists(FIRST, base + "/logo/first");
        fetcher.refresh(peer);
        assertTrue(keeps(FIRST));

        makeDue(FIRST);
        answers.put("/logo/first", Answer.status(204));
        fetcher.refresh(peer);

        assertFalse(keeps(FIRST));
        assertFalse(check(FIRST).stored());
    }

    @Test
    void aLogoOnAnotherHostIsNeverAskedFor() {
        lists(FIRST, "http://localhost:1/logo/first");

        fetcher.refresh(peer);

        assertTrue(asked.isEmpty());
        assertFalse(check(FIRST).stored());
    }

    @Test
    void aCardTheOtherInstanceDropsTakesItsLogoAlong() throws IOException {
        answers.put("/logo/first", Answer.of("image/png", picture(64, 64, "png")));
        answers.put("/logo/second", Answer.of("image/png", picture(32, 32, "png")));
        lists(FIRST, base + "/logo/first");
        lists(SECOND, base + "/logo/second");
        fetcher.refresh(peer);
        assertTrue(keeps(FIRST));
        assertTrue(keeps(SECOND));

        listed.removeIf(card -> card.stationUid().equals(SECOND.toString()));
        fetcher.refresh(peer);

        assertTrue(keeps(FIRST));
        assertFalse(keeps(SECOND));
    }

    @Test
    void forgettingAnInstanceDropsEveryLogoAndAsksForThemAfresh() throws IOException {
        answers.put("/logo/first", Answer.of("image/png", picture(64, 64, "png")));
        lists(FIRST, base + "/logo/first");
        fetcher.refresh(peer);

        logos.forgetInstance(key);

        assertFalse(keeps(FIRST));
        assertFalse(check(FIRST).stored());
        fetcher.refresh(peer);
        assertTrue(keeps(FIRST));
    }

    @Test
    void aCopyMissingFromStorageIsForgottenAndFetchedAfresh() throws IOException {
        answers.put("/logo/first", new Answer(200, "image/png", picture(64, 64, "png"), Map.of("ETag", "\"v1\"")));
        lists(FIRST, base + "/logo/first");
        fetcher.refresh(peer);
        logos.forget(key, FIRST.toString());
        assertTrue(check(FIRST).stored(), "the row still says a copy is kept");

        assertFalse(keeps(FIRST));

        var forgotten = check(FIRST);
        assertFalse(forgotten.stored());
        assertEquals(PictureTags.NONE, forgotten.tags());
        fetcher.refresh(peer);
        assertTrue(keeps(FIRST));
        assertTrue(tagsAskedWith.isEmpty(), "a copy that went missing is not asked for as unchanged");
    }

    @Test
    void severalDueLogosOfOnePeerAreAllFetched() throws IOException {
        List<UUID> stations = new ArrayList<>();
        for (int i = 0; i < RemoteStationLogoService.PARALLEL_FETCHES * 2 + 1; i++) {
            UUID station = UUID.randomUUID();
            stations.add(station);
            answers.put("/logo/" + station, Answer.of("image/png", picture(16, 16, "png")));
            lists(station, base + "/logo/" + station);
        }

        fetcher.refresh(peer);

        assertEquals(stations.size(), asked.size());
        for (UUID station : stations) {
            assertTrue(keeps(station), "a copy is kept for " + station);
        }
    }

    @Test
    void aCopyIsReadOnlyUnderAFingerprint() {
        assertFalse(logos.read("../../etc", FIRST, 0).isPresent());
        assertNotEquals(RemoteStationLogoService.fingerprint("a"), RemoteStationLogoService.fingerprint("b"));
    }
}
