/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberLookupService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Creates an {@code FF Musterstadt} station, mirrors every active {@code MANAGER} and
 * {@code TEAM} member of the primary station onto it (reusing the same account so the user can
 * switch stations after login), and federates the two stations bidirectionally with all default
 * capabilities enabled.
 */
@Singleton
public class DemoMirrorStationSeeder implements DemoSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoMirrorStationSeeder.class);
    private final StationRepository stationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberLookupService memberLookupService;
    private final AccountRepository accountRepository;
    private final FederationService federationService;
    private final Demo demoConfig;
    private final Api apiConfig;

    @Inject
    public DemoMirrorStationSeeder(
            StationRepository stationRepository,
            StationMemberRepository stationMemberRepository,
            MemberLookupService memberLookupService,
            AccountRepository accountRepository,
            FederationService federationService,
            Demo demoConfig,
            Api apiConfig) {
        this.stationRepository = stationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberLookupService = memberLookupService;
        this.accountRepository = accountRepository;
        this.federationService = federationService;
        this.demoConfig = demoConfig;
        this.apiConfig = apiConfig;
    }

    @Override
    public int order() {
        return MIRROR_STATION;
    }

    @Override
    public void seed(DemoSeederContext context) {
        int jfStationId = context.stationId();
        var ffStation = stationRepository.create("FF Musterstadt", DemoUids.station("ff-musterstadt"));
        stationRepository.updatePublicSlug(ffStation.id(), "ff-musterstadt");

        var managerRole = stationMemberRepository
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow();
        var loginRole = stationMemberRepository
                .findPermissionByName(StationPermission.LOGIN)
                .orElseThrow();

        int mirrored = 0;
        for (var userType : List.of(StationUserType.MANAGER, StationUserType.TEAM)) {
            for (StationMember jfMember : stationMemberRepository.findByStationAndUserType(jfStationId, userType)) {
                if (jfMember.accountId() == null) continue;
                String email = accountRepository
                        .findById(jfMember.accountId())
                        .map(Account::email)
                        .orElseThrow();
                var ffMember = stationMemberRepository.create(ffStation.id(), jfMember.accountId());
                memberLookupService.setUid(ffMember.id(), DemoUids.member(email, ffStation.id()));
                stationMemberRepository.setUserType(ffMember.id(), userType);
                stationMemberRepository.grantPermission(ffMember.id(), loginRole.id());
                if (userType == StationUserType.MANAGER) {
                    stationMemberRepository.grantPermission(ffMember.id(), managerRole.id());
                    stationRepository.setOwner(ffStation.id(), ffMember.id());
                }
                mirrored++;
            }
        }

        String remoteHost = demoConfig.federationForceHttp() ? "http://localhost:" + apiConfig.port() : null;
        var keyPair = federationService.generateKeyPair();
        stationRepository.updateFederationPrivateKey(ffStation.id(), federationService.encodePrivateKey(keyPair));
        federationService.acceptInvite(
                jfStationId, ffStation.id(), federationService.encodePublicKey(keyPair), remoteHost, remoteHost);

        log.info(
                "Demo: Created FF Musterstadt (id={}) with {} mirrored members, federated with JF Musterstadt",
                ffStation.id(),
                mirrored);
    }
}
