/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.cluster.repository.ClusterProfileFieldRepository;
import dev.chojo.ember.feature.members.entity.ExpirySettings;
import dev.chojo.ember.feature.members.entity.ExpiryState;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
import dev.chojo.ember.feature.members.entity.SentExpiryReminder;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.ExpiryReminderRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.ClusterAudience;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.ExpiryReminderKind;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reminds people of expiry dates that are close or have passed, whether a station asks for the date
 * or the association above it does.
 *
 * <p>One sweep looks at every expiry date field, and reads each answer on the clock of the station
 * the member belongs to, so midnight does not come early or late for a station outside UTC. An
 * association's field reaches members at several stations, which is why the clock is the member's
 * station's and not the field's. A read-only station sends nothing, as for appointment reminders.
 *
 * <p>Only members the field still applies to are reminded, and never former ones: an answer kept on
 * archive, or one that outlived the group a field is asked of, is still stored. Nobody without a
 * login hears anything, since there is nowhere for them to read it, and former guardians are left
 * out as well.
 *
 * <p>The member and whoever looks after them each get their own reminder. Member management gets one
 * per field per sweep, naming the members who became due in it, because a hundred notifications on
 * one morning are noise; on a day nobody became due, it gets nothing. For a station's field that is
 * the station's member management, for an association's field the association's own.
 */
@Singleton
public class ExpiryReminderService implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(ExpiryReminderService.class);

    /** How many members management's reminder names before it only counts them. */
    private static final int NAMED_MEMBERS = 3;

    private final ProfileFieldRepository profileFieldRepository;
    private final ClusterProfileFieldRepository clusterFieldRepository;
    private final ExpiryReminderRepository reminderRepository;
    private final ProfileFieldService profileFieldService;
    private final StationMemberRepository stationMemberRepository;
    private final StationRepository stationRepository;
    private final StationReadOnlyGuard readOnlyGuard;
    private final MemberPermissionResolver permissionResolver;
    private final MemberNameResolver memberNameResolver;
    private final Notifier notifier;

    @Inject
    public ExpiryReminderService(
            ProfileFieldRepository profileFieldRepository,
            ClusterProfileFieldRepository clusterFieldRepository,
            ExpiryReminderRepository reminderRepository,
            ProfileFieldService profileFieldService,
            StationMemberRepository stationMemberRepository,
            StationRepository stationRepository,
            StationReadOnlyGuard readOnlyGuard,
            MemberPermissionResolver permissionResolver,
            MemberNameResolver memberNameResolver,
            Notifier notifier) {
        this.profileFieldRepository = profileFieldRepository;
        this.clusterFieldRepository = clusterFieldRepository;
        this.reminderRepository = reminderRepository;
        this.profileFieldService = profileFieldService;
        this.stationMemberRepository = stationMemberRepository;
        this.stationRepository = stationRepository;
        this.readOnlyGuard = readOnlyGuard;
        this.permissionResolver = permissionResolver;
        this.memberNameResolver = memberNameResolver;
        this.notifier = notifier;
    }

    /**
     * An expiry date field, the station's or the association's.
     *
     * @param origin  whose field it is, which also says which numbering its id belongs to
     * @param id      the field
     * @param name    its label
     * @param config  its settings
     * @param ownerId the station or the association asking
     */
    private record ExpiryField(FieldOrigin origin, int id, String name, ProfileFieldConfig config, int ownerId) {}

    /**
     * One member whose date owes a reminder in this sweep.
     *
     * @param member    the member
     * @param expiresOn the last valid day
     * @param today     the day it is at the member's station
     * @param due       the reminder owed
     */
    private record DueMember(
            StationMember member, LocalDate expiresOn, LocalDate today, ExpiryReminderSchedule.Due due) {}

    /**
     * Sends every reminder owed at this moment and records it as done.
     *
     * <p>One field failing does not stop the others: its reminders stay owed and go out on a later
     * sweep.
     *
     * @param now the moment of the sweep
     */
    public void sweep(Instant now) {
        int forgotten = reminderRepository.forgetDeletedFields();
        if (forgotten > 0) log.info("Cleared {} expiry reminder record(s) of deleted fields", forgotten);
        var zones = new HashMap<Integer, ZoneId>();
        for (ExpiryField field : expiryFields()) {
            var settings = field.config().expiry();
            if (!settings.remindsAnybody()) continue;
            try {
                sweepField(field, settings, now, zones);
            } catch (RuntimeException e) {
                log.error("Expiry reminders for {} field {} failed", field.origin(), field.id(), e);
            }
        }
    }

    private List<ExpiryField> expiryFields() {
        var ofStations = profileFieldRepository.findAllByType(ProfileFieldType.EXPIRY_DATE).stream()
                .map(field -> new ExpiryField(
                        FieldOrigin.STATION, field.id(), field.name(), field.config(), field.stationId()));
        var ofClusters = clusterFieldRepository.findAllByType(ProfileFieldType.EXPIRY_DATE).stream()
                .map(field -> new ExpiryField(
                        FieldOrigin.CLUSTER, field.id(), field.name(), field.config(), field.clusterId()));
        return Stream.concat(ofStations, ofClusters).toList();
    }

    /**
     * Settles every member of one field owing a reminder, each on their own, then tells member management
     * about everyone who was reminded. A member whose reminder fails stays owed and is left out of
     * management's reminder, without costing the members settled before or after them theirs.
     */
    private void sweepField(ExpiryField field, ExpirySettings settings, Instant now, Map<Integer, ZoneId> zones) {
        var reminded = new ArrayList<DueMember>();
        for (DueMember member : dueMembers(field, settings, now, zones)) {
            try {
                Transactions.run(() -> settle(field, settings, member, now));
            } catch (RuntimeException e) {
                log.error(
                        "Expiry reminder for member {} on {} field {} failed",
                        member.member().id(),
                        field.origin(),
                        field.id(),
                        e);
                continue;
            }
            if (member.due().sends()) reminded.add(member);
        }
        if (reminded.isEmpty()) return;
        if (settings.remindManagement()) remindManagement(field, reminded);
        log.info(
                "Expiry reminders for {} field '{}' (id={}, owner={}): {} member(s) due",
                field.origin(),
                field.name(),
                field.id(),
                field.ownerId(),
                reminded.size());
    }

    /**
     * Reminds one member and records the reminder as done, as one write, so a reminder is never
     * recorded without going out nor goes out without being recorded.
     */
    private void settle(ExpiryField field, ExpirySettings settings, DueMember member, Instant now) {
        if (settings.remindMember() && member.due().sends()) remindMember(field, member);
        reminderRepository.markSent(
                member.member().id(),
                field.origin(),
                field.id(),
                member.expiresOn(),
                member.due().done(),
                now);
    }

    /** The members of one field whose date owes a reminder today, in the order their answers were read. */
    private List<DueMember> dueMembers(
            ExpiryField field, ExpirySettings settings, Instant now, Map<Integer, ZoneId> zones) {
        Map<Integer, List<SentExpiryReminder>> sentByMember =
                reminderRepository.findSent(field.origin(), field.id()).stream()
                        .collect(Collectors.groupingBy(SentExpiryReminder::memberId));
        var due = new ArrayList<DueMember>();
        for (var value : valuesOf(field)) {
            LocalDate expiresOn = dayOf(value.plainValue());
            if (expiresOn == null) continue;
            var member = stationMemberRepository.findById(value.memberId()).orElse(null);
            if (member == null || member.former() || !readOnlyGuard.isWritable(member.stationId())) continue;
            ZoneId zone = zones.computeIfAbsent(member.stationId(), this::zoneOf);
            LocalDate today = LocalDate.ofInstant(now, zone);
            var sent = sentByMember.getOrDefault(member.id(), List.of()).stream()
                    .filter(reminder -> reminder.expiresOn().equals(expiresOn))
                    .toList();
            var owed = ExpiryReminderSchedule.due(expiresOn, settings, today, sent, zone);
            if (owed.isEmpty() || !isAskedOf(field, member)) continue;
            due.add(new DueMember(member, expiresOn, today, owed.get()));
        }
        return due;
    }

    private List<ProfileFieldValue> valuesOf(ExpiryField field) {
        return field.origin() == FieldOrigin.CLUSTER
                ? clusterFieldRepository.findValuesOfField(field.id())
                : profileFieldRepository.findValuesOfField(field.id());
    }

    /** The clock of one station, or UTC where it has named none. */
    private ZoneId zoneOf(int stationId) {
        return StationFormat.timezoneOf(stationRepository.findById(stationId).orElse(null));
    }

    /**
     * Whether the member is still asked the field: by their kind of member or one of their groups for a
     * station's field, and at their station for an association's.
     */
    private boolean isAskedOf(ExpiryField field, StationMember member) {
        return profileFieldService.findApplicableFields(member.id()).stream()
                .anyMatch(asked -> asked.origin() == field.origin() && asked.id() == field.id());
    }

    /**
     * Tells the member, where they can sign in, and whoever looks after them. The member's copy leads to
     * their own profile; a guardian's leads to the page for the member in their care.
     */
    private void remindMember(ExpiryField field, DueMember due) {
        var member = due.member();
        long daysLeft = ExpiryState.daysLeft(due.expiresOn(), due.today());
        var params = new NotificationParams.ExpiryReminder(
                ExpiryReminderKind.forDaysLeft(daysLeft),
                field.name(),
                memberNameResolver.called(member.id()),
                due.expiresOn(),
                Math.toIntExact(Math.abs(daysLeft)),
                null,
                null);
        if (hasLogin(member)) {
            notifier.notify(
                    StationAudience.member(member.id()),
                    NotificationType.EXPIRY_REMINDER,
                    NotificationData.of(params, NotificationLinks.ownProfile()),
                    Delivery.EVERY_TIME);
        }
        var guardians = stationMemberRepository.findManagers(member.id()).stream()
                .filter(this::hasLogin)
                .map(StationMember::id)
                .toList();
        if (guardians.isEmpty()) return;
        notifier.notify(
                StationAudience.members(guardians),
                NotificationType.EXPIRY_REMINDER,
                NotificationData.of(params, NotificationLinks.managedProfile(member.id())),
                Delivery.EVERY_TIME);
    }

    /**
     * Tells member management once which members became due for one field, naming the first few and
     * counting all of them. A station's management is led to its member list narrowed to the field's
     * dates running out; an association's to its own member list, which has no column for the answers.
     *
     * <p>Both are told on every sweep that has members due, even while an earlier reminder is unread:
     * a later reminder about the same members reads the same as the first, carrying no date, and must
     * still go out.
     */
    private void remindManagement(ExpiryField field, List<DueMember> due) {
        var names = due.stream()
                .map(member -> memberNameResolver.called(member.member().id()))
                .filter(Objects::nonNull)
                .toList();
        String named = String.join(", ", names.subList(0, Math.min(NAMED_MEMBERS, names.size())));
        String members = names.size() > NAMED_MEMBERS ? named + ", …" : named;
        var params = new NotificationParams.ExpiryReminder(
                ExpiryReminderKind.MEMBERS_DUE, field.name(), null, null, null, members, due.size());
        if (field.origin() == FieldOrigin.CLUSTER) {
            notifier.notify(
                    ClusterAudience.holders(field.ownerId(), ClusterPermission.CLUSTER_MEMBER_MANAGER),
                    NotificationType.EXPIRY_REMINDER,
                    NotificationData.of(params, NotificationLinks.clusterMembers()),
                    Delivery.EVERY_TIME);
            return;
        }
        var recipients =
                stationMemberRepository
                        .findMembersWithPermission(field.ownerId(), StationPermission.MEMBER_MANAGER)
                        .stream()
                        .filter(this::hasLogin)
                        .map(StationMember::id)
                        .toList();
        if (recipients.isEmpty()) return;
        notifier.notify(
                StationAudience.members(recipients),
                NotificationType.EXPIRY_REMINDER,
                NotificationData.of(params, NotificationLinks.runningOut(field.id())),
                Delivery.EVERY_TIME);
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

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "expiry-reminder-check",
                Schedule.fixedDelay(Duration.ofMinutes(5), Duration.ofMinutes(30)),
                () -> sweep(Instant.now())));
    }
}
