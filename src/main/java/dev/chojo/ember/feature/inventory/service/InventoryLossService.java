/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Gear reported missing: who may say a piece is lost, what they have to write about it, and the
 * station's setting that decides the latter.
 *
 * <p>Whoever looks after the station's gear reaches all of it. Everybody else reaches what they
 * hold, and what the people in their care hold, as {@link GuardianPolicy} decides; nothing is
 * granted or configured for that. A station may ask those members for a note with every loss.
 */
@Singleton
public class InventoryLossService {
    private final InventoryService inventory;
    private final SelfCheckService selfChecks;
    private final GuardianPolicy guardians;
    private final StationRepository stations;

    @Inject
    public InventoryLossService(
            InventoryService inventory,
            SelfCheckService selfChecks,
            GuardianPolicy guardians,
            StationRepository stations) {
        this.inventory = inventory;
        this.selfChecks = selfChecks;
        this.guardians = guardians;
        this.stations = stations;
    }

    /**
     * Marks a piece lost, and records the loss on the self-check it was reported during.
     *
     * @param session     who reports it
     * @param itemId      the piece, already confirmed to be the station's
     * @param note        what happened, or {@code null}
     * @param selfCheckId the self-check the loss came up in, or {@code null}
     * @return the piece as it now stands
     */
    public InventoryItem markLost(
            StationSession session, int itemId, @Nullable String note, @Nullable Integer selfCheckId) {
        var item = inventory.findItemById(itemId).orElseThrow(Refusal.ITEM_NOT_HERE_ON_LOSS::raise);
        String trimmed = note == null || note.isBlank() ? null : note.trim();
        if (!session.hasPermission(StationPermission.INVENTORY_EDIT)) {
            requireHolds(session, item);
            if (trimmed == null && lossNoteRequired(session.stationId())) {
                throw Refusal.LOSS_NEEDS_A_NOTE.raise();
            }
        }
        Integer noteBy = trimmed == null ? null : session.member().id();
        var lost = inventory.markLost(itemId, trimmed, noteBy).orElseThrow(Refusal.ITEM_NOT_MARKED_LOST::raise);
        if (selfCheckId != null) {
            selfChecks.recordLoss(
                    selfCheckId,
                    session.stationId(),
                    session.member().id(),
                    session.hasPermission(StationPermission.MEMBER_GUARDIAN),
                    itemId);
        }
        return lost;
    }

    /**
     * Whether a member marking their own gear lost has to say what happened.
     *
     * @param stationId the station
     * @return its setting, {@code false} where the station is not found
     */
    public boolean lossNoteRequired(int stationId) {
        return stations.findById(stationId).map(Station::lossNoteRequired).orElse(false);
    }

    /**
     * Changes whether a member marking their own gear lost has to say what happened.
     *
     * @param stationId the station
     * @param required  the new setting
     * @return the setting as it now stands
     */
    public boolean requireLossNote(int stationId, boolean required) {
        stations.updateLossNoteRequired(stationId, required);
        return lossNoteRequired(stationId);
    }

    private void requireHolds(StationSession session, InventoryItem item) {
        Integer holder = item.assignedTo();
        if (holder == null) {
            throw Refusal.LOSS_NOT_YOURS_TO_REPORT.raise();
        }
        if (!guardians.mayActFor(session.user(), holder)) {
            throw Refusal.LOSS_NOT_YOURS_TO_REPORT_FOR_THEM.raise();
        }
    }
}
