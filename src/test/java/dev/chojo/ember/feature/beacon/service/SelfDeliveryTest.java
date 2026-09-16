/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.beacon.entity.BeaconPayloads;
import dev.chojo.ember.feature.discovery.service.DiscoveryKeyService;
import dev.chojo.ember.feature.system.service.ProblemReportScreenshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Handing a delivery to this instance's own beacon.
 *
 * <p>An instance may be its own beacon, and until this existed that still meant a request over HTTP
 * to itself. Inside a container that is where it came apart: the address an instance advertises is
 * the one a browser reaches it at, and from within the container nothing answers there. The picture
 * never arrived, and a picture that does not arrive stops its report going, so neither did the
 * report. What is asserted here is that the call is made as a call and not as a request, and that a
 * refusal is answered rather than thrown at whoever was reporting something.
 */
class SelfDeliveryTest {

    private BeaconSettings config;
    private Api api;
    private BeaconIntakeService intake;
    private DiscoveryKeyService keys;
    private ProblemReportScreenshotService pictures;
    private SelfDelivery self;

    @BeforeEach
    void setup() {
        config = mock(BeaconSettings.class);
        api = mock(Api.class);
        intake = mock(BeaconIntakeService.class);
        keys = mock(DiscoveryKeyService.class);
        pictures = mock(ProblemReportScreenshotService.class);
        self = new SelfDelivery(config, api, intake, keys, pictures);

        when(keys.instanceId()).thenReturn("this-instance");
        when(keys.publicKeyBase64()).thenReturn("public-key");
    }

    private static BeaconPayloads.Envelope envelope() {
        return new BeaconPayloads.Envelope(
                BeaconPayloads.PROTOCOL_VERSION,
                Instant.now(),
                UUID.randomUUID().toString(),
                "https://ember.test");
    }

    private static BeaconPayloads.ReportPayload report() {
        return new BeaconPayloads.ReportPayload(
                envelope(),
                "26.18.0",
                null,
                null,
                "Der Knopf tut nichts",
                "/station/dashboard",
                null,
                null,
                null,
                null,
                Instant.now(),
                13);
    }

    private static BeaconPayloads.ProblemPayload problem() {
        return new BeaconPayloads.ProblemPayload(
                envelope(),
                "26.18.0",
                null,
                null,
                "fingerprint",
                "ERROR",
                "logger",
                null,
                "Etwas ging schief",
                "frames",
                1,
                Instant.now(),
                Instant.now());
    }

    /** The whole question this class exists for: is the beacon we report to ourselves. */
    @Test
    void anInstanceReportingToItselfSaysSo() {
        when(config.receiving()).thenReturn(true);
        when(config.url()).thenReturn("https://ember.test");
        when(api.baseUrl()).thenReturn("https://ember.test");

        assertTrue(self.isSelf());
    }

    /**
     * Compared by host and not by the whole address.
     *
     * <p>The address a browser is given and the address a container can reach differ by port often
     * enough that comparing ports would answer no on the very setup this is for.
     */
    @Test
    void thePortIsNotPartOfTheQuestion() {
        when(config.receiving()).thenReturn(true);
        when(config.url()).thenReturn("https://ember.test:8080");
        when(api.baseUrl()).thenReturn("https://ember.test:3000");

        assertTrue(self.isSelf());
    }

    /** Another instance's beacon is another instance, and goes over the network as before. */
    @Test
    void anotherBeaconIsNotThisOne() {
        when(config.receiving()).thenReturn(true);
        when(config.url()).thenReturn("https://somebody-else.test");
        when(api.baseUrl()).thenReturn("https://ember.test");

        assertFalse(self.isSelf());
    }

    /**
     * An instance that keeps nothing is not a beacon, whatever address it reports to.
     *
     * <p>Without this an instance reporting to its own address while receiving nothing would write
     * into a beacon it does not run, and the delivery would be lost where it looked taken.
     */
    @Test
    void anInstanceThatKeepsNothingIsNotItsOwnBeacon() {
        when(config.receiving()).thenReturn(false);
        when(config.url()).thenReturn("https://ember.test");
        when(api.baseUrl()).thenReturn("https://ember.test");

        assertFalse(self.isSelf());
    }

    /** An address that is not one answers no rather than throwing at whoever asked. */
    @Test
    void anAddressThatIsNotOneAnswersNo() {
        when(config.receiving()).thenReturn(true);
        when(config.url()).thenReturn(":::not a url");
        when(api.baseUrl()).thenReturn("https://ember.test");

        assertFalse(self.isSelf());
    }

    /** A report is written as though it had arrived, under this instance's own identity. */
    @Test
    void aReportIsWrittenAsThoughItHadArrived() {
        var payload = report();

        assertTrue(self.takeReport(payload));

        verify(intake).storeReport("this-instance", "public-key", payload);
    }

    /** A fault likewise. */
    @Test
    void aFaultIsWrittenAsThoughItHadArrived() {
        var payload = problem();

        assertTrue(self.takeProblem(payload));

        verify(intake).storeProblem("this-instance", "public-key", payload);
    }

    /**
     * A refusal is an answer, not an exception.
     *
     * <p>The intake turns away anything it does not like, and whoever is reporting a problem is not
     * the person to be told about it: saying no here lets the caller fall back to the way everything
     * went before rather than failing the report.
     */
    @Test
    void aReportTheIntakeWillNotTakeIsAnsweredWithNo() {
        doThrow(new IllegalStateException("no")).when(intake).storeReport(anyString(), anyString(), any());

        assertFalse(self.takeReport(report()));
    }

    /** The same for a fault. */
    @Test
    void aFaultTheIntakeWillNotTakeIsAnsweredWithNo() {
        doThrow(new IllegalStateException("no")).when(intake).storeProblem(anyString(), anyString(), any());

        assertFalse(self.takeProblem(problem()));
    }

    /**
     * The picture is kept and its number handed back, which is what the report then names.
     *
     * <p>Nobody is named as having uploaded it: a picture forwarded to a beacon is of a page of
     * somebody else's installation and belongs to no member here.
     */
    @Test
    void thePictureIsKeptAndNumbered() {
        when(pictures.store(anyString(), isNull())).thenReturn(Optional.of(13));

        assertEquals(Optional.of(13), self.takePicture("data:image/webp;base64,AAAA"));

        verify(pictures).store("data:image/webp;base64,AAAA", null);
    }

    /** A picture that cannot be kept is answered with nothing, which stops the report going. */
    @Test
    void aPictureThatCannotBeKeptIsAnsweredWithNothing() {
        when(pictures.store(anyString(), isNull())).thenThrow(new IllegalStateException("no room"));

        assertTrue(self.takePicture("data:image/webp;base64,AAAA").isEmpty());
    }

    /** Nothing is written anywhere merely by asking whether this instance is its own beacon. */
    @Test
    void askingTheQuestionWritesNothing() {
        when(config.receiving()).thenReturn(true);
        when(config.url()).thenReturn("https://ember.test");
        when(api.baseUrl()).thenReturn("https://ember.test");

        self.isSelf();

        verify(intake, never()).storeReport(anyString(), anyString(), any());
        verify(intake, never()).storeProblem(anyString(), anyString(), any());
        verify(pictures, never()).store(anyString(), any());
    }
}
