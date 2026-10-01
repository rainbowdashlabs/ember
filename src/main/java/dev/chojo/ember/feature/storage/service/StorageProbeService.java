/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.conf.file.elements.StorageBackendSettings;
import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.ProbeResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.Callable;

/**
 * Whether a storage backend answers, asked of one built for the question and closed again after it.
 *
 * <p>A backend that cannot even be built answers as one that is not healthy, with the reason it could not
 * be built, so every caller reads a failure the same way whichever step it came from.
 */
@Singleton
public class StorageProbeService {
    /**
     * What a failed probe tells the client. The probe opens a connection to an address from the request,
     * so a verbatim failure would tell apart a refused connection, a timeout and a protocol error, which
     * is a port scan of whatever the address validator does not cover. The real cause goes to the log,
     * where the operator can still read it.
     *
     * <p>What is said instead names the three things that are actually wrong when this happens and says
     * where the rest of it is, because an operator who has mistyped a key needs to know to go and look
     * rather than to conclude that Ember is broken.
     */
    public static final String PROBE_FAILED =
            "The storage backend would not accept these settings. The address, the credentials or the target "
                    + "may be wrong, and the exact reason is in the instance log: it is kept there because this "
                    + "endpoint opens a connection to an address the request names";

    private static final Logger log = LoggerFactory.getLogger(StorageProbeService.class);

    private final StorageBackendFactory factory;

    @Inject
    public StorageProbeService(StorageBackendFactory factory) {
        this.factory = factory;
    }

    /**
     * Probes the backend a station, or an association, would stand on.
     *
     * @param config the stored or not yet stored configuration
     * @return whether it answered
     */
    public ProbeResult probe(StationStorageBackendConfig config) {
        return probeBuilt(() -> factory.buildForStation(config));
    }

    /**
     * Probes the backend the instance would stand on with these settings.
     *
     * @param settings the settings, which need not be the saved ones
     * @return whether it answered
     */
    public ProbeResult probe(StorageBackendSettings settings) {
        return probeBuilt(() -> factory.buildForInstance(settings));
    }

    /**
     * What a probe said, in the shape it is answered in.
     *
     * @param status the probe's own answer
     * @return the answer
     */
    public static ProbeResult resultOf(HealthStatus status) {
        return new ProbeResult(
                status.healthy(),
                status.error().orElse(null),
                status.checkedAt().toString());
    }

    /**
     * A probe's answer as a station or an association is told it: whether it answered, and for a failure
     * {@link #PROBE_FAILED} in place of the reason, which goes to the log.
     *
     * @param whose  whose storage was probed, as the log names it
     * @param result the probe's own answer
     * @return the answer without the reason of a failure
     */
    public static ProbeResult withoutReason(String whose, ProbeResult result) {
        if (result.error() == null) return result;
        log.warn("Storage backend probe for {} failed: {}", whose, result.error());
        return new ProbeResult(result.healthy(), PROBE_FAILED, result.checkedAt());
    }

    private static ProbeResult probeBuilt(Callable<StorageBackend> build) {
        try (StorageBackend backend = build.call()) {
            return resultOf(backend.probe());
        } catch (Exception e) {
            return new ProbeResult(false, e.getMessage(), Instant.now().toString());
        }
    }
}
