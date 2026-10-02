/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.StationRefusal;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The first station of a fresh instance, founded by the administrator who installed it.
 *
 * <p>A new instance starts with its administrator and no station at all, so that the first station
 * carries a name somebody chose rather than a placeholder. Until one exists, an administrator signing
 * in is led here; the administrator who founds it becomes its manager and owner and goes on into its
 * setup. Once the instance has any station, further ones are made the ordinary way.
 */
@Singleton
public class FirstStationService {
    private static final Logger log = LoggerFactory.getLogger(FirstStationService.class);

    private final StationService stationService;
    private final StationRepository stationRepository;
    private final StationMemberRepository memberRepository;

    @Inject
    public FirstStationService(
            StationService stationService,
            StationRepository stationRepository,
            StationMemberRepository memberRepository) {
        this.stationService = stationService;
        this.stationRepository = stationRepository;
        this.memberRepository = memberRepository;
    }

    /**
     * Whether the instance still waits for its first station.
     *
     * @return {@code true} while the instance has no station of its own, an association's shell aside
     */
    public boolean isNeeded() {
        return stationRepository.findAllRegular().isEmpty();
    }

    /**
     * Founds the instance's first station and makes the founding account its manager and owner.
     *
     * @param name      what the station is called
     * @param accountId the administrator founding it
     * @return the new station
     */
    public Station found(String name, int accountId) {
        if (name == null || name.isBlank()) throw StationRefusal.FIRST_STATION_NEEDS_A_NAME.raise();
        if (!isNeeded()) throw StationRefusal.FIRST_STATION_ALREADY_FOUNDED.raise();

        var station = stationService.create(name.trim());
        var member = memberRepository.create(station.id(), accountId);
        memberRepository.setUserType(member.id(), StationUserType.MANAGER);
        memberRepository
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .ifPresent(permission -> memberRepository.grantPermission(member.id(), permission.id()));
        stationRepository.setOwner(station.id(), member.id());
        log.info("Account {} founded the instance's first station {}", accountId, station.id());
        return station;
    }
}
