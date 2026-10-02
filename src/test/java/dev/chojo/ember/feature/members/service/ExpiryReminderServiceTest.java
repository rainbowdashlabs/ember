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
import dev.chojo.ember.feature.cluster.entity.ClusterMember;
import dev.chojo.ember.feature.cluster.entity.ClusterProfileField;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.ExpiryReminderRepository;
import dev.chojo.ember.feature.notifications.entity.Audience;
import dev.chojo.ember.feature.notifications.entity.ClusterAudience;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.ExpiryReminderKind;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.question.FieldType;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The expiry reminder sweep against a real station: who is reminded, of what, and that nothing goes
 * out twice.
 */
class ExpiryReminderServiceTest extends RepositoryTestBase {
    private static final Instant MARCH_FIRST = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant MARCH_TWENTY_FOURTH = Instant.parse("2026-03-24T10:00:00Z");
    private static final Instant FEBRUARY_LAST_NOON = Instant.parse("2026-02-28T12:00:00Z");

    private final List<Integer> accounts = new ArrayList<>();
    private Notifier notifications;
    private StationReadOnlyGuard readOnlyGuard;
    private ExpiryReminderRepository ledger;
    private ExpiryReminderService service;
    private Station station;

    @BeforeEach
    void setUp() {
        notifications = mock(Notifier.class);
        readOnlyGuard = mock(StationReadOnlyGuard.class);
        when(readOnlyGuard.isWritable(anyInt())).thenReturn(true);
        ledger = new ExpiryReminderRepository();
        service = new ExpiryReminderService(
                profileFieldCore,
                ledger,
                profileFieldService,
                stationMemberRepo,
                stationRepo,
                readOnlyGuard,
                memberPermissionResolver,
                memberNameResolver,
                notifications);
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
                station.id(), name, FieldType.EXPIRY_DATE, ProfileFieldConfig.parse(config), false, true, null);
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

    private List<Sent> sent() {
        var to = ArgumentCaptor.forClass(Audience.class);
        var data = ArgumentCaptor.forClass(NotificationData.class);
        verify(notifications, org.mockito.Mockito.atLeast(0))
                .notify(to.capture(), eq(NotificationType.EXPIRY_REMINDER), data.capture(), eq(Delivery.EVERY_TIME));
        var all = new ArrayList<Sent>();
        for (int i = 0; i < to.getAllValues().size(); i++) {
            if (to.getAllValues().get(i) instanceof StationAudience station) {
                all.add(new Sent(station.memberIds(), data.getAllValues().get(i)));
            }
        }
        return all;
    }

    private void clusterAudienceTold(int times, ArgumentCaptor<NotificationData> data) {
        verify(notifications, times(times))
                .notify(
                        org.mockito.ArgumentMatchers.isA(ClusterAudience.class),
                        eq(NotificationType.EXPIRY_REMINDER),
                        data.capture(),
                        eq(Delivery.EVERY_TIME));
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

        verify(notifications, never()).notify(any(StationAudience.class), any(), any(), any());
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

        verify(notifications, never()).notify(any(StationAudience.class), any(), any(), any());
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

        verify(notifications, never()).notify(any(StationAudience.class), any(), any(), any());
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
            var field = associationExpiryField(cluster.id());
            var anna = memberAt(clusterStation, "Anna", StationUserType.MEMBER, StationPermission.LOGIN);
            memberAt(clusterStation, "Maria", StationUserType.TEAM, StationPermission.MEMBER_MANAGER);
            var officeMember = associationManager(cluster.id());
            clusterProfileFieldRepo.setValue(anna.id(), field.id(), StringNode.valueOf("2026-03-31"));

            service.sweep(MARCH_FIRST);

            var own = leadingTo(sent(), "profile");
            assertEquals(List.of(anna.id()), List.copyOf(own.to()));
            assertEquals("Maschinist gültig bis", own.params().fieldName());
            assertTrue(
                    sent().stream().noneMatch(one -> one.data().link().route().equals("members-list")),
                    "the station's member management is not told about the association's question");

            var data = ArgumentCaptor.forClass(NotificationData.class);
            verify(notifications)
                    .notify(
                            eq(ClusterAudience.holders(cluster.id(), ClusterPermission.CLUSTER_MEMBER_MANAGER)),
                            eq(NotificationType.EXPIRY_REMINDER),
                            data.capture(),
                            eq(Delivery.EVERY_TIME));
            assertEquals(
                    List.of(officeMember.id()),
                    clusterService.findMemberIdsWith(cluster.id(), ClusterPermission.CLUSTER_MEMBER_MANAGER));
            assertEquals(NotificationLinks.clusterMembers(), data.getValue().link());
            assertEquals(1, ((NotificationParams.ExpiryReminder) data.getValue().params()).count());

            assertEquals(1, ledger.findSent(FieldOrigin.CLUSTER, field.id()).size());
            assertTrue(ledger.findSent(FieldOrigin.STATION, field.id()).isEmpty());

            service.sweep(MARCH_TWENTY_FOURTH);

            clusterAudienceTold(2, data);
            assertEquals(
                    data.getAllValues().getFirst(),
                    data.getAllValues().getLast(),
                    "the second reminder reads the same as the first and still goes out");
        } finally {
            stationRepo.delete(clusterStation.id());
            clusterService.delete(cluster.id());
        }
    }

    /**
     * An association's field reads each answer on the clock of the member's own station, so a station
     * whose day has already begun is reminded while one still on the day before is not.
     */
    @Test
    @SuppressWarnings("unchecked")
    void anAssociationsMembersAreRemindedOnTheirOwnStationsClock() {
        var cluster = clusterService.create("Expiry Zones " + System.nanoTime(), null);
        var ahead = clusterService.createStation(cluster.id(), "Expiry Ahead " + System.nanoTime());
        var behind = clusterService.createStation(cluster.id(), "Expiry Behind " + System.nanoTime());
        stationRepo.updateTimezone(ahead.id(), "Pacific/Auckland");
        stationRepo.updateTimezone(behind.id(), "Europe/Berlin");
        try {
            var field = associationExpiryField(cluster.id());
            var anna = memberAt(ahead, "Anna", StationUserType.MEMBER, StationPermission.LOGIN);
            var ben = memberAt(behind, "Ben", StationUserType.MEMBER, StationPermission.LOGIN);
            associationManager(cluster.id());
            clusterProfileFieldRepo.setValue(anna.id(), field.id(), StringNode.valueOf("2026-03-31"));
            clusterProfileFieldRepo.setValue(ben.id(), field.id(), StringNode.valueOf("2026-03-31"));

            service.sweep(FEBRUARY_LAST_NOON);

            assertEquals(
                    List.of(anna.id()), List.copyOf(leadingTo(sent(), "profile").to()));
            var data = ArgumentCaptor.forClass(NotificationData.class);
            clusterAudienceTold(1, data);
            var params = (NotificationParams.ExpiryReminder) data.getValue().params();
            assertEquals(1, params.count(), "only the station already on the first of March is due");
            assertTrue(params.members().startsWith("Anna"));
            assertTrue(ledger.findSent(FieldOrigin.CLUSTER, field.id()).stream()
                    .noneMatch(reminder -> reminder.memberId() == ben.id()));
        } finally {
            stationRepo.delete(ahead.id());
            stationRepo.delete(behind.id());
            clusterService.delete(cluster.id());
        }
    }

    @Test
    void aStationAheadOfUtcIsRemindedOnItsOwnDay() {
        stationRepo.updateTimezone(station.id(), "Pacific/Auckland");
        var field = expiryField("Erste Hilfe gültig bis", "{}");
        var anna = member("Anna", StationUserType.MEMBER, StationPermission.LOGIN);
        answer(anna, field, "2026-03-31");

        service.sweep(FEBRUARY_LAST_NOON);

        var own = leadingTo(sent(), "profile");
        assertEquals(30, own.params().days(), "it is already the first of March in Auckland");
        assertEquals(
                List.of(LocalDate.of(2026, 3, 1)),
                ledger.findSent(FieldOrigin.STATION, field.id()).stream()
                        .map(reminder -> reminder.reminderDate())
                        .toList());
    }

    @Test
    void aStationBehindUtcWaitsForItsOwnDay() {
        stationRepo.updateTimezone(station.id(), "America/New_York");
        var field = expiryField("Erste Hilfe gültig bis", "{}");
        var anna = member("Anna", StationUserType.MEMBER, StationPermission.LOGIN);
        answer(anna, field, "2026-03-31");

        service.sweep(Instant.parse("2026-03-01T03:00:00Z"));

        verify(notifications, never()).notify(any(StationAudience.class), any(), any(), any());
        assertTrue(ledger.findSent(FieldOrigin.STATION, field.id()).isEmpty(), "it is still February in New York");
    }

    @Test
    void aFormerGuardianIsNotReminded() {
        var field = expiryField("Erste Hilfe gültig bis", "{}");
        var ben = member("Ben", StationUserType.MEMBER);
        var gerda = member("Gerda", StationUserType.GUARDIAN);
        stationMemberRepo.addManager(gerda.id(), ben.id());
        stationMemberRepo.setFormer(gerda.id(), true);
        answer(ben, field, "2026-03-31");

        service.sweep(MARCH_FIRST);

        verify(notifications, never()).notify(any(StationAudience.class), any(), any(), any());
        assertEquals(1, ledger.findSent(FieldOrigin.STATION, field.id()).size(), "Ben's reminder is still done");
    }

    /** A reminder day added once somebody's date has passed does not remind them of it again. */
    @Test
    void aReminderDayAddedAfterTheDateRemindsNobody() {
        var field = expiryField("Erste Hilfe gültig bis", "{\"reminderDays\":[30],\"remindManagement\":true}");
        member("Maria", StationUserType.TEAM, StationPermission.MEMBER_MANAGER);
        var anna = member("Anna", StationUserType.MEMBER, StationPermission.LOGIN);
        var ben = member("Ben", StationUserType.MEMBER, StationPermission.LOGIN);
        answer(anna, field, "2026-02-20");
        answer(ben, field, "2026-03-20");

        service.sweep(MARCH_FIRST);
        assertEquals(3, sent().size(), "Anna hears her date ran out, Ben that his is near, management of both");

        profileFieldRepo.update(
                field.id(),
                field.name(),
                FieldType.EXPIRY_DATE,
                ProfileFieldConfig.parse("{\"reminderDays\":[30,20],\"remindManagement\":true}"),
                false,
                true,
                null,
                false);
        service.sweep(MARCH_FIRST.plusSeconds(1800));

        var sent = sent();
        assertEquals(5, sent.size(), "only Ben, still before his date, and management hear of the new day");
        assertEquals(List.of(ben.id()), List.copyOf(sent.get(3).to()));
        assertEquals(19, sent.get(3).params().days());
        assertEquals(1, sent.get(4).params().count());
        assertTrue(
                ledger.findSent(FieldOrigin.STATION, field.id()).stream()
                        .anyMatch(reminder -> reminder.memberId() == anna.id()
                                && reminder.reminderDate().equals(LocalDate.of(2026, 1, 31))),
                "Anna's new day is recorded as done");

        service.sweep(MARCH_FIRST.plusSeconds(3600));
        assertEquals(5, sent().size());
    }

    /**
     * A member whose reminder fails stays owed and is left out of management's reminder, while the
     * others are reminded, recorded and named as usual.
     */
    @Test
    void aFailedReminderCostsNobodyElseTheirs() {
        var field = expiryField("Erste Hilfe gültig bis", "{\"remindManagement\":true}");
        member("Maria", StationUserType.TEAM, StationPermission.MEMBER_MANAGER);
        var anna = member("Anna", StationUserType.MEMBER, StationPermission.LOGIN);
        var ben = member("Ben", StationUserType.MEMBER, StationPermission.LOGIN);
        answer(anna, field, "2026-03-31");
        answer(ben, field, "2026-03-31");
        doThrow(new IllegalStateException("unreachable"))
                .when(notifications)
                .notify(eq(StationAudience.member(ben.id())), any(), any(), any());

        service.sweep(MARCH_FIRST);

        var management = leadingTo(sent(), "members-list");
        assertEquals(1, management.params().count());
        assertTrue(management.params().members().startsWith("Anna"));
        assertTrue(ledger.findSent(FieldOrigin.STATION, field.id()).stream()
                .noneMatch(reminder -> reminder.memberId() == ben.id()));

        doReturn(1).when(notifications).notify(eq(StationAudience.member(ben.id())), any(), any(), any());
        service.sweep(MARCH_FIRST.plusSeconds(1800));

        var retried = sent().stream()
                .filter(one -> one.data().link().route().equals("members-list"))
                .toList()
                .getLast();
        assertEquals(1, retried.params().count());
        assertTrue(retried.params().members().startsWith("Ben"), "Ben is still owed and reminded next time");
    }

    private ClusterProfileField associationExpiryField(int clusterId) {
        var field = clusterProfileFieldRepo.create(
                clusterId,
                "Maschinist gültig bis",
                FieldType.EXPIRY_DATE,
                ProfileFieldConfig.parse("{\"remindManagement\":true}"),
                false,
                true,
                null,
                false,
                false,
                null);
        clusterProfileFieldRepo.assignToRole(field.id(), ProfileFieldScope.MEMBER, 0, null, null, null);
        return field;
    }

    /** Someone in the association's office who manages its members. */
    private ClusterMember associationManager(int clusterId) {
        var office = accountRepo.create("office" + System.nanoTime() + "@expiry.test", "Olga", "Office");
        accounts.add(office.id());
        var officeMember = clusterService.addMember(clusterId, office.id(), ClusterUserType.CLUSTER_USER);
        clusterService.grant(officeMember.id(), ClusterPermission.CLUSTER_MEMBER_MANAGER);
        return officeMember;
    }
}
