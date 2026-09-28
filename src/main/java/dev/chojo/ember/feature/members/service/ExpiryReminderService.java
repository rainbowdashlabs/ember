/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.ExpirySettings;
import dev.chojo.ember.feature.members.entity.ExpiryState;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.SentExpiryReminder;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.ExpiryReminderRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.ExpiryReminderKind;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Reminds people of expiry dates that are close or have passed.
 *
 * <p>One sweep looks at every station's expiry date fields on the station's own clock, so midnight
 * does not come early or late for a station outside UTC. A read-only station sends nothing, as for
 * appointment reminders.
 *
 * <p>Only members the field still applies to are reminded, and never former ones: an answer kept on
 * archive, or one that outlived the group a field is asked of, is still stored. Nobody without a
 * login hears anything, since there is nowhere for them to read it, and former guardians are left
 * out as well.
 *
 * <p>The member and whoever looks after them each get their own reminder. Member management gets one
 * per field per sweep, naming the members who became due in it, because a hundred notifications on
 * one morning are noise; on a day nobody became due, it gets nothing.
 */
@Singleton
public class ExpiryReminderService {
    private static final Logger log = LoggerFactory.getLogger(ExpiryReminderService.class);

    /** How many members management's reminder names before it only counts them. */
    private static final int NAMED_MEMBERS = 3;

    private final ProfileFieldRepository profileFieldRepository;
    private final ExpiryReminderRepository reminderRepository;
    private final ProfileFieldService profileFieldService;
    private final StationMemberRepository stationMemberRepository;
    private final StationRepository stationRepository;
    private final StationReadOnlyGuard readOnlyGuard;
    private final MemberPermissionResolver permissionResolver;
    private final MemberNameResolver memberNameResolver;
    private final NotificationService notificationService;

    @Inject
    public ExpiryReminderService(
            ProfileFieldRepository profileFieldRepository,
            ExpiryReminderRepository reminderRepository,
            ProfileFieldService profileFieldService,
            StationMemberRepository stationMemberRepository,
            StationRepository stationRepository,
            StationReadOnlyGuard readOnlyGuard,
            MemberPermissionResolver permissionResolver,
            MemberNameResolver memberNameResolver,
            NotificationService notificationService) {
        this.profileFieldRepository = profileFieldRepository;
        this.reminderRepository = reminderRepository;
        this.profileFieldService = profileFieldService;
        this.stationMemberRepository = stationMemberRepository;
        this.stationRepository = stationRepository;
        this.readOnlyGuard = readOnlyGuard;
        this.permissionResolver = permissionResolver;
        this.memberNameResolver = memberNameResolver;
        this.notificationService = notificationService;
    }

    /**
     * One member whose date owes a reminder in this sweep.
     *
     * @param member    the member
     * @param expiresOn the last valid day
     * @param due       the reminder owed
     */
    private record DueMember(StationMember member, LocalDate expiresOn, ExpiryReminderSchedule.Due due) {}

    /**
     * Sends every reminder owed at this moment and records it as done.
     *
     * <p>One station failing does not stop the others: its reminders stay owed and go out on a later
     * sweep.
     *
     * @param now the moment of the sweep
     */
    public void sweep(Instant now) {
        var byStation = profileFieldRepository.findAllByType(ProfileFieldType.EXPIRY_DATE).stream()
                .collect(Collectors.groupingBy(ProfileField::stationId, LinkedHashMap::new, Collectors.toList()));
        for (var station : byStation.entrySet()) {
            try {
                sweepStation(station.getKey(), station.getValue(), now);
            } catch (RuntimeException e) {
                log.error("Expiry reminders for station {} failed", station.getKey(), e);
            }
        }
    }

    private void sweepStation(int stationId, List<ProfileField> fields, Instant now) {
        if (!readOnlyGuard.isWritable(stationId)) return;
        ZoneId zone =
                StationFormat.timezoneOf(stationRepository.findById(stationId).orElse(null));
        LocalDate today = LocalDate.ofInstant(now, zone);
        for (ProfileField field : fields) {
            var settings = field.config().expiry();
            if (settings.remindsAnybody()) sweepField(field, settings, today, zone, now);
        }
    }

    private void sweepField(ProfileField field, ExpirySettings settings, LocalDate today, ZoneId zone, Instant now) {
        var due = dueMembers(field, settings, today, zone);
        for (DueMember member : due) {
            if (settings.remindMember()) remindMember(field, member, today);
            reminderRepository.markSent(
                    member.member().id(),
                    field.id(),
                    member.expiresOn(),
                    member.due().done(),
                    now);
        }
        if (settings.remindManagement() && !due.isEmpty()) remindManagement(field, due);
        if (!due.isEmpty()) {
            log.info(
                    "Expiry reminders for field '{}' (id={}, station={}): {} member(s) due",
                    field.name(),
                    field.id(),
                    field.stationId(),
                    due.size());
        }
    }

    /** The members of one field whose date owes a reminder today, in the order their answers were read. */
    private List<DueMember> dueMembers(ProfileField field, ExpirySettings settings, LocalDate today, ZoneId zone) {
        Map<Integer, List<SentExpiryReminder>> sentByMember = reminderRepository.findSent(field.id()).stream()
                .collect(Collectors.groupingBy(SentExpiryReminder::memberId));
        var due = new ArrayList<DueMember>();
        for (var value : profileFieldRepository.findValuesOfField(field.id())) {
            LocalDate expiresOn = dayOf(value.plainValue());
            if (expiresOn == null) continue;
            var sent = sentByMember.getOrDefault(value.memberId(), List.of()).stream()
                    .filter(reminder -> reminder.expiresOn().equals(expiresOn))
                    .toList();
            var owed = ExpiryReminderSchedule.due(expiresOn, settings, today, sent, zone);
            if (owed.isEmpty()) continue;
            var member = stationMemberRepository.findById(value.memberId()).orElse(null);
            if (member == null || member.former() || !isAskedOf(field, member)) continue;
            due.add(new DueMember(member, expiresOn, owed.get()));
        }
        return due;
    }

    /** Whether the member is still asked the field, by their kind of member or by one of their groups. */
    private boolean isAskedOf(ProfileField field, StationMember member) {
        return profileFieldService.findApplicableFields(member.id()).stream()
                .anyMatch(asked -> asked.origin() == FieldOrigin.STATION && asked.id() == field.id());
    }

    /**
     * Tells the member, where they can sign in, and whoever looks after them. The member's copy leads to
     * their own profile; a guardian's leads to the page for the member in their care.
     */
    private void remindMember(ProfileField field, DueMember due, LocalDate today) {
        var member = due.member();
        long daysLeft = ExpiryState.daysLeft(due.expiresOn(), today);
        var params = new NotificationParams.ExpiryReminder(
                ExpiryReminderKind.forDaysLeft(daysLeft),
                field.name(),
                memberNameResolver.called(member.id()),
                due.expiresOn(),
                Math.toIntExact(Math.abs(daysLeft)),
                null,
                null);
        if (hasLogin(member)) {
            notificationService.notifyMembers(
                    List.of(member.id()),
                    NotificationType.EXPIRY_REMINDER,
                    NotificationData.of(params, NotificationLinks.ownProfile()));
        }
        var guardians = stationMemberRepository.findManagers(member.id()).stream()
                .filter(guardian -> !guardian.former())
                .filter(this::hasLogin)
                .map(StationMember::id)
                .toList();
        if (!guardians.isEmpty()) {
            notificationService.notifyMembers(
                    guardians,
                    NotificationType.EXPIRY_REMINDER,
                    NotificationData.of(params, NotificationLinks.managedProfile(member.id())));
        }
    }

    /**
     * Tells member management once which members became due for one field, naming the first few and
     * counting all of them, and leads to the member list narrowed to the field's dates running out.
     */
    private void remindManagement(ProfileField field, List<DueMember> due) {
        var recipients =
                stationMemberRepository
                        .findMembersWithPermission(field.stationId(), StationPermission.MEMBER_MANAGER)
                        .stream()
                        .filter(this::hasLogin)
                        .map(StationMember::id)
                        .toList();
        if (recipients.isEmpty()) return;
        var names = due.stream()
                .map(member -> memberNameResolver.called(member.member().id()))
                .filter(Objects::nonNull)
                .toList();
        String named = String.join(", ", names.subList(0, Math.min(NAMED_MEMBERS, names.size())));
        String members = names.size() > NAMED_MEMBERS ? named + ", …" : named;
        var params = new NotificationParams.ExpiryReminder(
                ExpiryReminderKind.MEMBERS_DUE, field.name(), null, null, null, members, due.size());
        notificationService.notifyMembers(
                recipients,
                NotificationType.EXPIRY_REMINDER,
                NotificationData.of(params, NotificationLinks.runningOut(field.id())));
    }

    /** Whether somebody can sign in and read a notification at all. */
    private boolean hasLogin(StationMember member) {
        return member.accountId() != null && permissionResolver.resolve(member).contains(StationPermission.LOGIN);
    }

    private static LocalDate dayOf(String stored) {
        if (stored == null || stored.isBlank()) return null;
        try {
            return LocalDate.parse(stored.length() > 10 ? stored.substring(0, 10) : stored);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
