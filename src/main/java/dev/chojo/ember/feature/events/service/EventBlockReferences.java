/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.content.entity.BlockAudience;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.service.BlockReferences;
import dev.chojo.ember.feature.events.repository.EventRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Refuses an event block naming an appointment not every reader of its content may see: on a page
 * anything not on the station's public calendar, in a news or wiki article anything kept to part of
 * the station. An appointment of another station is never the station's to name.
 */
@Singleton
public class EventBlockReferences implements BlockReferences {
    private final EventRepository repository;

    @Inject
    public EventBlockReferences(EventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void requireReachable(@Nullable Integer stationId, BlockAudience audience, CellConfig config) {
        if (!(config instanceof CellConfig.FeaturedEventConfig featured)) return;
        String eventUid = featured.eventUid();
        if (eventUid == null) return;
        boolean reachable = stationId != null
                && repository
                        .findOpenByUid(stationId, audience, UUID.fromString(eventUid))
                        .isPresent();
        if (reachable) return;
        throw (audience == BlockAudience.PUBLIC
                        ? Refusal.EVENT_BLOCK_APPOINTMENT_NOT_PUBLIC
                        : Refusal.EVENT_BLOCK_APPOINTMENT_NOT_FOR_EVERY_MEMBER)
                .raise();
    }
}
