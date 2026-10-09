/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AccountInviteService;
import dev.chojo.ember.feature.accountlink.service.AssociationLinkService;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterMember;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.cluster.service.ClusterMemberManagementService.MemberPage;
import dev.chojo.ember.feature.cluster.service.ClusterMemberSearchService.Search;
import dev.chojo.ember.feature.members.entity.TagVisibility;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository.ClusterMemberRow;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the cluster routes ask of their services: appointing the first administrator, searching the
 * people of every station and naming a cluster member by their account.
 */
class ClusterRouteServicesTest {
    private static final UUID CLUSTER_UID = UUID.fromString("00000000-0000-0000-0001-000000000005");
    private static final UUID ACCOUNT_UID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void anAdministratorIsAppointedOnlyForAClusterAndAnAccountThatExist() {
        var clusters = mock(ClusterService.class);
        var accounts = mock(AccountRepository.class);
        var cluster = mock(Cluster.class);
        when(cluster.id()).thenReturn(5);
        when(clusters.findByUid(CLUSTER_UID)).thenReturn(Optional.of(cluster));
        when(accounts.findByUid(ACCOUNT_UID)).thenReturn(Optional.of(TestSessions.account()));
        var service = new ClusterAppointmentService(clusters, accounts);

        service.appointAdministrator(CLUSTER_UID, ACCOUNT_UID);

        verify(clusters).addMember(5, TestSessions.ACCOUNT_ID, ClusterUserType.CLUSTER_ADMIN);
        assertEquals(
                ClusterRefusal.CLUSTER_NOT_HERE_ON_APPOINTMENT,
                assertThrows(RefusalResponse.class, () -> service.appointAdministrator(UUID.randomUUID(), ACCOUNT_UID))
                        .refusal());
        assertEquals(
                ClusterRefusal.ACCOUNT_NOT_HERE_ON_CLUSTER_APPOINTMENT,
                assertThrows(RefusalResponse.class, () -> service.appointAdministrator(CLUSTER_UID, UUID.randomUUID()))
                        .refusal());
    }

    @Test
    void aSearchRowCarriesTheIdentityItIsDrawnFrom() {
        var management = mock(ClusterMemberManagementService.class);
        var groups = mock(MemberGroupRepository.class);
        var tags = mock(UserTagRepository.class);
        var station = UUID.randomUUID();
        var tagged = new ClusterMemberRow(
                1,
                UUID.randomUUID(),
                3,
                station,
                "Nord",
                false,
                StationUserType.MEMBER,
                LocalDate.EPOCH,
                "Mara",
                "m@test",
                false,
                "Nord");
        var plain = new ClusterMemberRow(
                2, UUID.randomUUID(), 3, station, "Nord", true, StationUserType.TEAM, null, "Tom", null, true, "Nord");
        when(management.search(5, "ma", 3, StationUserType.MEMBER, true, 0, 50))
                .thenReturn(new MemberPage(List.of(tagged, plain), 7, 0, 50));
        when(groups.findNameColors(List.of(1, 2))).thenReturn(Map.of(1, "#f00"));
        when(tags.findDisplayTags(List.of(1, 2)))
                .thenReturn(Map.of(1, new UserTag(4, 3, "Leitung", "#0f0", TagVisibility.BADGE, 0)));

        var page = new ClusterMemberSearchService(management, groups, tags)
                .search(5, new Search("ma", 3, StationUserType.MEMBER, true, 0, 50));

        assertEquals(7, page.total());
        assertEquals("#f00", page.members().getFirst().identity().nameColor());
        assertEquals(
                "Leitung", page.members().getFirst().identity().displayTag().name());
        assertNull(page.members().get(1).identity().displayTag());
        assertEquals("TEAM", page.members().get(1).userType());
    }

    @Test
    void aClusterMemberIsNamedByTheAccountBehindThemOrByNothingWhereItIsGone() {
        var accounts = mock(AccountRepository.class);
        when(accounts.findById(TestSessions.ACCOUNT_ID)).thenReturn(Optional.of(TestSessions.account()));
        when(accounts.findById(99)).thenReturn(Optional.empty());
        var service = new ClusterMemberService(
                mock(ClusterRepository.class),
                mock(ClusterService.class),
                accounts,
                mock(AccountInviteService.class),
                mock(AssociationLinkService.class),
                mock(DomainEventBus.class));

        var named = service.describe(new ClusterMember(1, 5, TestSessions.ACCOUNT_ID, ClusterUserType.CLUSTER_ADMIN));
        var gone = service.describe(new ClusterMember(2, 5, 99, ClusterUserType.CLUSTER_ADMIN));

        assertEquals(ACCOUNT_UID.toString(), named.accountUid());
        assertEquals("mara@test.com", named.email());
        assertEquals("CLUSTER_ADMIN", named.userType());
        assertNull(gone.name());
        assertNull(gone.accountUid());
        verify(accounts, never()).findByUid(any());
    }
}
