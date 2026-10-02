/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.conf.file.elements.StorageBackendSettings;
import dev.chojo.ember.feature.storage.audit.StorageAuditOutcome;
import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
import dev.chojo.ember.feature.storage.core.ProbeResult;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Whether a storage answers, for every owner, with the reason of a failure kept in the log.
 *
 * <p>The probe opens a connection to an address from the request, so a verbatim failure would tell apart a
 * refused connection, a timeout and a protocol error, which is a port scan of whatever the address check
 * does not cover. Every answer that leaves here has the reason replaced by {@link #PROBE_FAILED}, whoever
 * asked: a station, an association or the instance. A probe of storage that is already saved is written to
 * its owner's history; one of storage only typed in is not, because nothing was decided.
 *
 * <p>A backend that cannot even be built answers as one that is not healthy, so every caller reads a
 * failure the same way whichever step it came from.
 */
@Singleton
public class StorageProbeService {
    /**
     * What a failed probe tells the client. What is said names the three things that are actually wrong when
     * this happens and says where the rest of it is, because an operator who has mistyped a key needs to
     * know to go and look rather than to conclude that Ember is broken.
     */
    public static final String PROBE_FAILED =
            "The storage backend would not accept these settings. The address, the credentials or the target "
                    + "may be wrong, and the exact reason is in the instance log: it is kept there because this "
                    + "endpoint opens a connection to an address the request names";

    private static final Logger log = LoggerFactory.getLogger(StorageProbeService.class);

    private final StorageBackendFactory factory;
    private final StorageBackendAuditService audit;

    @Inject
    public StorageProbeService(StorageBackendFactory factory, StorageBackendAuditService audit) {
        this.factory = factory;
        this.audit = audit;
    }

    /**
     * Whether storage a station or an association has not saved yet would answer.
     *
     * @param owner  whose storage it would be
     * @param config the storage as the form describes it
     * @return the answer, without the reason of a failure
     */
    public ProbeResult probe(Owner owner, StationStorageBackendConfig config) {
        return masked(owner, probeBuilt(() -> factory.buildForStation(config)));
    }

    /**
     * Whether storage the instance has not saved yet would answer.
     *
     * @param settings the storage as the form describes it
     * @return the answer, without the reason of a failure
     */
    public ProbeResult probe(StorageBackendSettings settings) {
        return masked(new Owner.Instance(), probeBuilt(() -> factory.buildForInstance(settings)));
    }

    /**
     * Whether the storage a station or an association saved answers, written to its history.
     *
     * @param actor  who asked
     * @param owner  whose storage it is
     * @param config the saved storage
     * @return the answer, without the reason of a failure
     */
    public ProbeResult probeSaved(Actor actor, Owner owner, StationStorageBackendConfig config) {
        return recorded(actor, owner, probeBuilt(() -> factory.buildForStation(config)));
    }

    /**
     * Whether a backend in use answers, written to its owner's history. The backend stays open: it is the
     * one files are being kept on.
     *
     * @param actor   who asked
     * @param owner   whose storage it is
     * @param running the backend in use
     * @return the answer, without the reason of a failure
     */
    public ProbeResult probeRunning(Actor actor, Owner owner, StorageBackend running) {
        return recorded(actor, owner, resultOf(running.probe()));
    }

    private ProbeResult recorded(Actor actor, Owner owner, ProbeResult raw) {
        ProbeResult result = masked(owner, raw);
        audit.recordProbe(
                actor, owner, result.healthy() ? StorageAuditOutcome.OK : StorageAuditOutcome.FAILED, result.error());
        return result;
    }

    private static ProbeResult masked(Owner owner, ProbeResult result) {
        if (result.error() == null) return result;
        log.warn("Storage backend probe for {} failed: {}", whose(owner), result.error());
        return new ProbeResult(result.healthy(), PROBE_FAILED, result.checkedAt());
    }

    private static String whose(Owner owner) {
        return switch (owner) {
            case Owner.Station station -> "station " + station.stationId();
            case Owner.Association association -> "association " + association.clusterId();
            case Owner.Instance ignored -> "the instance";
        };
    }

    private static ProbeResult resultOf(HealthStatus status) {
        return new ProbeResult(
                status.healthy(),
                status.error().orElse(null),
                status.checkedAt().toString());
    }

    private static ProbeResult probeBuilt(Callable<StorageBackend> build) {
        try (StorageBackend backend = build.call()) {
            return resultOf(backend.probe());
        } catch (Exception e) {
            String reason =
                    Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName());
            return new ProbeResult(false, reason, Instant.now().toString());
        }
    }
}
