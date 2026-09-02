/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.service;

import dev.chojo.ember.feature.equipment.repository.EquipmentNeedRepository;
import dev.chojo.ember.feature.federation.service.LendingService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What has to happen to held stock when an appointment stops happening.
 *
 * <p>Cancelling is the common case: it is the moment held stock must be released and requests sent to
 * a partner withdrawn, because the evening they were for is not going to happen and the partner has
 * no other way of learning that.
 *
 * <p>It lives in the write path rather than in a domain event handler on purpose. Dispatch is
 * synchronous and handler exceptions are swallowed, so a release that failed would fail silently and
 * leave a partner's shelf blocked for a weekend nobody is coming.
 */
@Singleton
public class EquipmentReleaseService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentReleaseService.class);

    private final EquipmentNeedRepository needRepository;
    private final LendingService lendingService;

    @Inject
    public EquipmentReleaseService(EquipmentNeedRepository needRepository, LendingService lendingService) {
        this.needRepository = needRepository;
        this.lendingService = lendingService;
    }

    /**
     * Releases everything an appointment holds and withdraws what it has asked of a partner.
     *
     * @param eventId   the appointment
     * @param stationId the station it belongs to
     */
    public void release(int eventId, int stationId) {
        int withdrawn = lendingService.withdrawForEvent(eventId, stationId);
        int lines = needRepository.deleteByEvent(eventId);
        if (lines > 0 || withdrawn > 0) {
            log.info("Appointment {} released {} equipment lines and withdrew {} requests", eventId, lines, withdrawn);
        }
    }

    /**
     * Withdraws what an appointment has asked of a partner, leaving its lines standing.
     *
     * <p>What a cancellation does: the appointment is still there and may be reinstated, so its lines
     * are worth keeping, but nothing should stay held at a partner over an evening that is off.
     *
     * @param eventId   the appointment
     * @param stationId the station it belongs to
     */
    public void withdrawRequests(int eventId, int stationId) {
        int withdrawn = lendingService.withdrawForEvent(eventId, stationId);
        if (withdrawn > 0) log.info("Appointment {} withdrew {} lending requests", eventId, withdrawn);
    }
}
