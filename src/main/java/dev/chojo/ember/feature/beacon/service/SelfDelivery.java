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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Optional;

/**
 * Hands a delivery to this instance's own beacon without going out to the network for it.
 *
 * <p>An instance may be its own beacon, which is how a single installation keeps what it reports,
 * and until now that still meant a request over HTTP to itself. Inside a container that is where it
 * came apart: the address an instance advertises is the one a browser reaches it at, and from within
 * the container that address is the container, where nothing answers. A picture never arrived, and
 * because a picture that does not arrive stops its report going, neither did the report.
 *
 * <p>A call to oneself is not a network call, so this makes it a method call. What is written down
 * is the same as what the route writes down, under this instance's own identity, which is exactly
 * what the delivery would have carried had it gone out and come back.
 */
@Singleton
public class SelfDelivery {
    private static final Logger log = LoggerFactory.getLogger(SelfDelivery.class);

    private final BeaconSettings config;
    private final Api api;
    private final BeaconIntakeService intake;
    private final DiscoveryKeyService keys;
    private final ProblemReportScreenshotService pictures;

    @Inject
    public SelfDelivery(
            BeaconSettings config,
            Api api,
            BeaconIntakeService intake,
            DiscoveryKeyService keys,
            ProblemReportScreenshotService pictures) {
        this.config = config;
        this.api = api;
        this.intake = intake;
        this.keys = keys;
        this.pictures = pictures;
    }

    /**
     * Whether the beacon this instance reports to is this instance.
     *
     * <p>Compared by host and not by the whole address, the same way a delivery's audience is
     * checked on arrival: the address a browser is given and the address a container can reach
     * differ by port often enough that a port would make this answer no on the very setup it is for.
     */
    public boolean isSelf() {
        if (!config.receiving()) return false;
        String beacon = host(config.url());
        String own = host(api.baseUrl());
        return beacon != null && own != null && beacon.equalsIgnoreCase(own);
    }

    /** Writes a report as though it had arrived, and says whether it did. */
    public boolean takeReport(BeaconPayloads.ReportPayload payload) {
        try {
            intake.storeReport(keys.instanceId(), keys.publicKeyBase64(), payload);
            return true;
        } catch (RuntimeException e) {
            log.warn("This instance would not take its own report: {}", e.getMessage());
            return false;
        }
    }

    /** Writes a fault as though it had arrived, and says whether it did. */
    public boolean takeProblem(BeaconPayloads.ProblemPayload payload) {
        try {
            intake.storeProblem(keys.instanceId(), keys.publicKeyBase64(), payload);
            return true;
        } catch (RuntimeException e) {
            log.warn("This instance would not take its own fault: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Keeps the picture belonging to a report and says what it was numbered.
     *
     * <p>The bytes are already on this instance, so what the library gives back is the file that is
     * already there: it keeps what it is given under the hash of its content, and the same picture
     * twice is the same file.
     */
    public Optional<Integer> takePicture(String encoded) {
        try {
            return pictures.store(encoded, null);
        } catch (RuntimeException e) {
            log.warn("This instance would not keep the picture of its own report: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String host(String url) {
        try {
            return url == null ? null : URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
