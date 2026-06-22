/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.media.service.ImageService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.page.entity.CellConfig;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemberListResolverTest extends RepositoryTestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static Station station;
    private static Account account1;
    private static Account account2;
    private static StationMember member1;
    private static StationMember member2;
    private static int groupId;
    private static int tagId;
    private static UUID uid1;
    private static UUID uid2;
    private static ImageService imageService;

    @BeforeAll
    static void setupClass() {
        station = stationRepo.create("MemberListStation");
        account1 = accountRepo.create("mlr-1@test.com", "Alice", "Alpha");
        account2 = accountRepo.create("mlr-2@test.com", "Bob", "Bravo");
        member1 = stationMemberRepo.create(station.id(), account1.id());
        member2 = stationMemberRepo.create(station.id(), account2.id());
        uid1 = stationMemberRepo.resolveUid(member1.id());
        uid2 = stationMemberRepo.resolveUid(member2.id());

        var group = memberGroupRepo.create(station.id(), "MLR Group");
        memberGroupRepo.addMember(group.id(), member1.id());
        memberGroupRepo.addMember(group.id(), member2.id());
        groupId = group.id();

        var tag = userTagRepo.create(station.id(), "MLR Tag");
        userTagRepo.addMember(tag.id(), member1.id());
        tagId = tag.id();

        imageService = mock(ImageService.class);
        when(imageService.read(ArgumentMatchers.any(), ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
                .thenReturn(Optional.empty());
    }

    @AfterAll
    static void cleanupClass() {
        stationRepo.delete(station.id());
        accountRepo.delete(account1.id());
        accountRepo.delete(account2.id());
    }

    private static JsonNode json(String src) {
        try {
            return MAPPER.readTree(src);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void resolveGroupKindReturnsMembers() {
        var src = json("{\"kind\":\"group\",\"groupId\":" + groupId + "}");
        var result = MemberListResolver.resolve(
                stationMemberRepo,
                imageService,
                station.id(),
                src,
                CellConfig.MemberListSortBy.NAME,
                Map.of(),
                List.of());
        assertEquals(2, result.size());
    }

    @Test
    void resolveGroupKindMissingGroupId() {
        var src = json("{\"kind\":\"group\"}");
        var result = MemberListResolver.resolve(
                stationMemberRepo,
                imageService,
                station.id(),
                src,
                CellConfig.MemberListSortBy.ORDER,
                Map.of(),
                List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void resolveTagKind() {
        var src = json("{\"kind\":\"tag\",\"tagId\":" + tagId + "}");
        var result = MemberListResolver.resolve(
                stationMemberRepo,
                imageService,
                station.id(),
                src,
                CellConfig.MemberListSortBy.ROLE,
                Map.of(),
                List.of());
        assertEquals(1, result.size());

        var missing = json("{\"kind\":\"tag\"}");
        assertTrue(MemberListResolver.resolve(stationMemberRepo, imageService, station.id(), missing, null, null, null)
                .isEmpty());
    }

    @Test
    void resolveManualKind() {
        var src = json("{\"kind\":\"manual\",\"memberUids\":[\"" + uid1 + "\",\"" + uid2 + "\",\"not-a-uuid\"]}");
        var result = MemberListResolver.resolve(
                stationMemberRepo,
                imageService,
                station.id(),
                src,
                CellConfig.MemberListSortBy.JOIN_DATE,
                Map.of(uid1.toString(), "Desc1"),
                List.of());
        assertEquals(2, result.size());
    }

    @Test
    void resolveManualWithoutMemberUidsArray() {
        var src = json("{\"kind\":\"manual\"}");
        var result = MemberListResolver.resolve(
                stationMemberRepo, imageService, station.id(), src, null, Map.of(), List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void resolveUnknownKindAndNonObjectSource() {
        var unknown = json("{\"kind\":\"weird\"}");
        assertTrue(MemberListResolver.resolve(stationMemberRepo, imageService, station.id(), unknown, null, null, null)
                .isEmpty());
        var arr = json("[]");
        assertTrue(MemberListResolver.resolve(stationMemberRepo, imageService, station.id(), arr, null, null, null)
                .isEmpty());
        assertTrue(MemberListResolver.resolve(stationMemberRepo, imageService, station.id(), null, null, null, null)
                .isEmpty());
    }

    @Test
    void resolveOrderBranchUsesMemberOrder() {
        var src = json("{\"kind\":\"manual\",\"memberUids\":[\"" + uid1 + "\",\"" + uid2 + "\"]}");
        var ordered = MemberListResolver.resolve(
                stationMemberRepo,
                imageService,
                station.id(),
                src,
                CellConfig.MemberListSortBy.ORDER,
                Map.of(),
                List.of(uid2.toString(), uid1.toString()));
        assertEquals(uid2.toString(), ordered.getFirst().memberUid());

        var noOrder = MemberListResolver.resolve(
                stationMemberRepo,
                imageService,
                station.id(),
                src,
                CellConfig.MemberListSortBy.ORDER,
                Map.of(),
                List.of());
        assertEquals(2, noOrder.size());
    }

    @Test
    void avatarDataUrlIsBuiltFromImageService() {
        var imageSvc = mock(ImageService.class);
        when(imageSvc.read(ArgumentMatchers.any(), ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
                .thenReturn(Optional.of(new ImageService.ImageData(new byte[] {1, 2}, "image/png")));

        var src = json("{\"kind\":\"manual\",\"memberUids\":[\"" + uid1 + "\"]}");
        var result =
                MemberListResolver.resolve(stationMemberRepo, imageSvc, station.id(), src, null, Map.of(), List.of());
        assertEquals(1, result.size());
        assertNotNull(result.getFirst().avatarUrl());
        assertTrue(result.getFirst().avatarUrl().startsWith("data:image/png;base64,"));
    }
}
