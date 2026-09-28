/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.ExpiryReminderRepository;
import dev.chojo.ember.feature.notifications.entity.ExpiryReminderKind;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.node.StringNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The expiry reminder sweep against a real station: who is reminded, of what, and that nothing goes
 * out twice.
 */
class ExpiryReminderServiceTest extends RepositoryTestBase {
    private static final Instant MARCH_FIRST = Instant.parse("2026-03-01T10:00:00Z");

    private final List<Integer> accounts = new ArrayList<>();
    private NotificationService notifications;
    private StationReadOnlyGuard readOnlyGuard;
    private ExpiryReminderRepository ledger;
    private ExpiryReminderService service;
    private Station station;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationService.class);
        readOnlyGuard = mock(StationReadOnlyGuard.class);
        when(readOnlyGuard.isWritable(anyInt())).thenReturn(true);
        ledger = new ExpiryReminderRepository();
        service = new ExpiryReminderService(
                profileFieldRepo,
                clusterProfileFieldRepo,
                ledger,
                profileFieldService,
                stationMemberRepo,
                stationRepo,
                readOnlyGuard,
                memberPermissionResolver,
                memberNameResolver,
                notifications,
                () -> clusterService);
        station = stationRepo.create("Expiry Sweep " + System.nanoTime());
    }

    @AfterEach
    void tearDown() {
        stationRepo.delete(station.id());
        accounts.forEach(accountRepo::delete);
    }

    private StationMember member(String first, StationUserType type, StationPermission... granted) {
        return memberAt(station, first, type, granted);
    }

    private StationMember memberAt(Station at, String first, StationUserType type, StationPermission... granted) {
        var account = accountRepo.create(first.toLowerCase() + System.nanoTime() + "@expiry.test", first, "Test");
        accounts.add(account.id());
        var member = stationMemberRepo.create(at.id(), account.id());
        stationMemberRepo.setUserType(member.id(), type);
        for (var permission : granted) {
            stationMemberRepo.grantPermission(
                    member.id(),
                    stationMemberRepo
                            .findPermissionByName(permission)
                            .orElseThrow()
                            .id());
        }
        return stationMemberRepo.findById(member.id()).orElseThrow();
    }

    private ProfileField expiryField(String name, String config) {
        var field = profileFieldRepo.create(
                station.id(), name, ProfileFieldType.EXPIRY_DATE, ProfileFieldConfig.parse(config), false, true, null);
        profileFieldRepo.assignToRole(field.id(), ProfileFieldScope.MEMBER, 0, null, null, null);
        return field;
    }

    private void answer(StationMember member, ProfileField field, String day) {
        profileFieldRepo.setValue(member.id(), field.id(), StringNode.valueOf(day));
    }

    /** Every notification sent, as who got it and what it said. */
    private record Sent(Collection<Integer> to, NotificationData data) {
        NotificationParams.ExpiryReminder params() {
            return (NotificationParams.ExpiryReminder) data.params();
        }
    }

    private static Sent leadingTo(List<Sent> sent, String route) {
        return sent.stream()
                .filter(one -> one.data().link().route().equals(route))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private List<Sent> sent() {
        var to = ArgumentCaptor.forClass(Collection.class);
        var data = ArgumentCaptor.forClass(NotificationData.class);
        verify(notifications, org.mockito.Mockito.atLeast(0))
                .notifyMembers(to.capture(), eq(NotificationType.EXPIRY_REMINDER), data.capture());
        var all = new ArrayList<Sent>();
        for (int i = 0; i < to.getAllValues().size(); i++) {
            all.add(new Sent(to.getAllValues().get(i), data.getAllValues().get(i)));
        }
        return all;
    }

    @Test
    void theMemberTheirGuardianAndManagementAreEachRemindedOnce() {
        var field = expiryField("Erste Hilfe gültig bis", "{\"remindManagement\":true}");
        var anna = member("Anna", StationUserType.MEMBER, StationPermission.LOGIN);
        var ben = member("Ben", StationUserType.MEMBER);
        var gerda = member("Gerda", StationUserType.GUARDIAN);
        var manager = member("Maria", StationUserType.TEAM, StationPermission.MEMBER_MANAGER);
        stationMemberRepo.addManager(gerda.id(), ben.id());
        answer(anna, field, "2026-03-31");
        answer(ben, field, "2026-03-08");

        service.sweep(MARCH_FIRST);

        var sent = sent();
        assertEquals(3, sent.size());

        var own = leadingTo(sent, "profile");
        assertEquals(List.of(anna.id()), List.copyOf(own.to()));
        assertEquals(ExpiryReminderKind.EXPIRES_IN, own.params().kind());
        assertEquals(30, own.params().days());
        assertEquals(LocalDate.of(2026, 3, 31), own.params().expiresOn());
        assertEquals(NotificationLinks.ownProfile(), own.data().link());

        var guardians = leadingTo(sent, "profile-managed");
        assertEquals(List.of(gerda.id()), List.copyOf(guardians.to()), "Ben cannot sign in, so only Gerda hears");
        assertEquals(7, guardians.params().days());
        assertTrue(guardians.params().memberName().startsWith("Ben"));
        assertEquals(
                NotificationLinks.managedProfile(ben.id()), guardians.data().link());

        var management = leadingTo(sent, "members-list");
        assertEquals(List.of(manager.id()), List.copyOf(management.to()));
        assertEquals(ExpiryReminderKind.MEMBERS_DUE, management.params().kind());
        assertEquals(2, management.params().count());
        assertTrue(management.params().members().contains("Anna"));
        assertEquals(NotificationLinks.runningOut(field.id()), management.data().link());

        assertEquals(
                List.of(LocalDate.of(2026, 2, 6), LocalDate.of(2026, 3, 1)),
                ledger.findSent(FieldOrigin.STATION, field.id()).stream()
                        .filter(reminder -> reminder.memberId() == ben.id())
                        .map(reminder -> reminder.reminderDate())
                        .sorted()
                        .toList(),
                "the missed reminder is recorded beside the one that went out");

        service.sweep(MARCH_FIRST.plusSeconds(1800));
        assertEquals(3, sent().size(), "the next sweep owes nothing");
    }

    @Test
    void formerMembersAndMembersNoLongerAskedAreLeftOut() {
        var field = expiryField("Erste Hilfe gültig bis", "{\"remindManagement\":true}");
        member("Maria", StationUserType.TEAM, StationPermission.MEMBER_MANAGER);
        var former = member("Fritz", StationUserType.MEMBER, StationPermission.LOGIN);
        stationMemberRepo.setFormer(former.id(), true);
        var team = member("Tina", StationUserType.TEAM);
        answer(former, field, "2026-03-31");
        answer(team, field, "2026-03-31");

        service.sweep(MARCH_FIRST);

        verify(notifications, never()).notifyMembers(any(), any(), any());
        assertTrue(
                ledger.findSent(FieldOrigin.STATION, field.id()).isEmpty(),
                "their reminders stay owed, should they come back");
    }

    @Test
    void aMemberWithoutALoginIsRecordedButNotNotified() {
        var field = expiryField("Erste Hilfe gültig bis", "{}");
        var ben = member("Ben", StationUserType.MEMBER);
        answer(ben, field, "2026-03-31");

        service.sweep(MARCH_FIRST);

        verify(notifications, never()).notifyMembers(any(), any(), any());
        assertEquals(1, ledger.findSent(FieldOrigin.STATION, field.id()).size());
    }

    @Test
    void anExpiredDateRepeatsOnItsIntervalUntilRenewed() {
        var field = expiryField("Atemschutz gültig bis", "{\"reminderDays\":[],\"repeatEveryDays\":7}");
        var anna = member("Anna", StationUserType.MEMBER, StationPermission.LOGIN);
        answer(anna, field, "2026-02-20");

        service.sweep(MARCH_FIRST);
        service.sweep(MARCH_FIRST.plusSeconds(4 * 86400));
        service.sweep(MARCH_FIRST.plusSeconds(7 * 86400));

        var sent = sent();
        assertEquals(2, sent.size());
        assertEquals(ExpiryReminderKind.EXPIRED, sent.getFirst().params().kind());
        assertEquals(9, sent.getFirst().params().days());
        assertEquals(16, sent.get(1).params().days());

        answer(anna, field, "2028-02-20");
        service.sweep(MARCH_FIRST.plusSeconds(14 * 86400));
        assertEquals(2, sent().size(), "a renewed date is far from its first reminder");
    }

    @Test
    void theLastValidDayIsCalledToday() {
        var field = expiryField("Erste Hilfe gültig bis", "{\"reminderDays\":[0]}");
        var anna = member("Anna", StationUserType.MEMBER, StationPermission.LOGIN);
        answer(anna, field, "2026-03-01");

        service.sweep(MARCH_FIRST);

        assertEquals(
                ExpiryReminderKind.EXPIRES_TODAY, sent().getFirst().params().kind());
    }

    @Test
    void managementNamesTheFirstFewAndCountsTheRest() {
        var field = expiryField("Erste Hilfe gültig bis", "{\"remindMember\":false,\"remindManagement\":true}");
        member("Maria", StationUserType.TEAM, StationPermission.MEMBER_MANAGER);
        for (String first : List.of("Anna", "Ben", "Carla", "Dora")) {
            answer(member(first, StationUserType.MEMBER), field, "2026-03-31");
        }

        service.sweep(MARCH_FIRST);

        var sent = sent();
        assertEquals(1, sent.size(), "the members themselves are not reminded");
        assertEquals(4, sent.getFirst().params().count());
        assertTrue(sent.getFirst().params().members().endsWith(", …"));
    }

    @Test
    void aFieldRemindingNobodyAndAReadOnlyStationSendNothing() {
        var silent = expiryField("Erste Hilfe gültig bis", "{\"remindMember\":false}");
        var anna = member("Anna", StationUserType.MEMBER, StationPermission.LOGIN);
        answer(anna, silent, "2026-03-31");

        service.sweep(MARCH_FIRST);
        assertTrue(ledger.findSent(FieldOrigin.STATION, silent.id()).isEmpty());

        var loud = expiryField("Führerschein gültig bis", "{}");
        answer(anna, loud, "2026-03-31");
        when(readOnlyGuard.isWritable(station.id())).thenReturn(false);

        service.sweep(MARCH_FIRST);

        verify(notifications, never()).notifyMembers(any(), any(), any());
        assertTrue(ledger.findSent(FieldOrigin.STATION, loud.id()).isEmpty());
    }

    /**
     * An association's expiry date reminds the member at the station as a station's does, and the
     * association's own member management instead of the station's.
     */
    @Test
    @SuppressWarnings("unchecked")
    void anAssociationsExpiryDateRemindsTheMemberAndTheAssociation() {
        var cluster = clusterService.create("Expiry Association " + System.nanoTime(), null);
        var clusterStation =
                clusterService.createStation(cluster.id(), "Expiry Association Station " + System.nanoTime());
        try {
            var field = clusterProfileFieldRepo.create(
                    cluster.id(),
                    "Maschinist gültig bis",
                    ProfileFieldType.EXPIRY_DATE,
                    ProfileFieldConfig.parse("{\"remindManagement\":true}"),
                    false,
                    true,
                    null,
                    false,
                    false,
                    null);
            clusterProfileFieldRepo.assignToRole(field.id(), ProfileFieldScope.MEMBER, 0, null, null, null);
            var anna = memberAt(clusterStation, "Anna", StationUserType.MEMBER, StationPermission.LOGIN);
            memberAt(clusterStation, "Maria", StationUserType.TEAM, StationPermission.MEMBER_MANAGER);
            var office = accountRepo.create("office" + System.nanoTime() + "@expiry.test", "Olga", "Office");
            accounts.add(office.id());
            var officeMember = clusterService.addMember(cluster.id(), office.id(), ClusterUserType.CLUSTER_USER);
            clusterService.grant(officeMember.id(), ClusterPermission.CLUSTER_MEMBER_MANAGER);
            clusterProfileFieldRepo.setValue(anna.id(), field.id(), StringNode.valueOf("2026-03-31"));

            service.sweep(MARCH_FIRST);

            var own = leadingTo(sent(), "profile");
            assertEquals(List.of(anna.id()), List.copyOf(own.to()));
            assertEquals("Maschinist gültig bis", own.params().fieldName());
            assertTrue(
                    sent().stream().noneMatch(one -> one.data().link().route().equals("members-list")),
                    "the station's member management is not told about the association's question");

            var to = ArgumentCaptor.forClass(Collection.class);
            var data = ArgumentCaptor.forClass(NotificationData.class);
            verify(notifications)
                    .notifyClusterMembersIfAbsent(
                            to.capture(), eq(NotificationType.EXPIRY_REMINDER), data.capture(), eq(null));
            assertEquals(List.of(officeMember.id()), List.copyOf(to.getValue()));
            assertEquals(NotificationLinks.clusterMembers(), data.getValue().link());
            assertEquals(1, ((NotificationParams.ExpiryReminder) data.getValue().params()).count());

            assertEquals(1, ledger.findSent(FieldOrigin.CLUSTER, field.id()).size());
            assertTrue(ledger.findSent(FieldOrigin.STATION, field.id()).isEmpty());
        } finally {
            stationRepo.delete(clusterStation.id());
            clusterService.delete(cluster.id());
        }
    }
}
