/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.waitinglist.entity.WaitingList;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntry;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryStatus;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryValue;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListField;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListInvite;
import dev.chojo.ember.feature.waitinglist.repository.WaitingListRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Singleton
public class WaitingListService {
    private static final Logger log = LoggerFactory.getLogger(WaitingListService.class);

    private final WaitingListRepository repository;
    private final StationRepository stationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "waitlist-confirmation-checker");
        t.setDaemon(true);
        return t;
    });

    @Inject
    public WaitingListService(
            WaitingListRepository repository,
            StationRepository stationRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            EmailService emailService,
            NotificationService notificationService) {
        this.repository = repository;
        this.stationRepository = stationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.emailService = emailService;
        this.notificationService = notificationService;
        scheduler.scheduleAtFixedRate(this::checkAllExpiredConfirmations, 1, 24, TimeUnit.HOURS);
    }

    // --- List CRUD (delegates) ---

    public List<WaitingList> findByStation(int stationId) {
        return repository.findByStation(stationId);
    }

    public Optional<WaitingList> findById(int id) {
        return repository.findById(id);
    }

    public WaitingList create(
            int stationId,
            String name,
            String description,
            String scoringFormula,
            int confirmIntervalDays,
            Integer testingGroupId,
            Integer joinGroupId,
            Integer joinRoleId,
            int attendanceThreshold) {
        return repository.create(
                stationId,
                name,
                description,
                scoringFormula,
                confirmIntervalDays,
                testingGroupId,
                joinGroupId,
                joinRoleId,
                attendanceThreshold);
    }

    public Optional<WaitingList> update(
            int id,
            String name,
            String description,
            String scoringFormula,
            int confirmIntervalDays,
            Integer testingGroupId,
            Integer joinGroupId,
            Integer joinRoleId,
            int attendanceThreshold) {
        return repository.update(
                id,
                name,
                description,
                scoringFormula,
                confirmIntervalDays,
                testingGroupId,
                joinGroupId,
                joinRoleId,
                attendanceThreshold);
    }

    public Optional<WaitingList> updateVisibleFields(int id, String visibleFieldsJson) {
        return repository.updateVisibleFields(id, visibleFieldsJson);
    }

    public void delete(int id) {
        repository.delete(id);
    }

    // --- Fields ---

    public List<WaitingListField> findFieldsByList(int listId) {
        return repository.findFieldsByList(listId);
    }

    public WaitingListField createField(
            int listId, String name, String fieldType, String config, int position, boolean required) {
        return repository.createField(listId, name, fieldType, config, position, required);
    }

    public Optional<WaitingListField> updateField(
            int fieldId, String name, String fieldType, String config, int position, boolean required) {
        return repository.updateField(fieldId, name, fieldType, config, position, required);
    }

    public void deleteField(int fieldId) {
        repository.deleteField(fieldId);
    }

    // --- Invites ---

    public List<WaitingListInvite> findInvitesByList(int listId) {
        return repository.findInvitesByList(listId);
    }

    public WaitingListInvite createInvite(int listId, int maxUses, Instant expiresAt) {
        String code = UUID.randomUUID().toString();
        return repository.createInvite(listId, code, maxUses, expiresAt);
    }

    public Optional<WaitingListInvite> findInviteByCode(String code) {
        return repository.findInviteByCode(code);
    }

    public void deleteInvite(int inviteId) {
        repository.deleteInvite(inviteId);
    }

    // --- Registration ---

    public WaitingListEntry registerViaInvite(
            String inviteCode,
            String firstname,
            String lastname,
            String parentName,
            String email,
            Map<Integer, String> fieldValues,
            String notes) {
        var invite = repository
                .findInviteByCode(inviteCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite code"));
        if (!invite.hasUsesLeft()) {
            throw new IllegalStateException("Invite code has been used up");
        }
        if (invite.isExpired()) {
            throw new IllegalStateException("Invite code has expired");
        }

        String accessToken = UUID.randomUUID().toString();
        var entry = repository.createEntry(
                invite.listId(), firstname, lastname, parentName, email, accessToken, notes != null ? notes : "");
        repository.incrementInviteUses(invite.id());

        for (var e : fieldValues.entrySet()) {
            repository.upsertEntryValue(entry.id(), e.getKey(), e.getValue());
        }

        String displayName = entry.fullName();

        // Send registration email
        var listForEmail = repository.findById(invite.listId()).orElse(null);
        String stationName = listForEmail != null ? resolveStationName(listForEmail.stationId()) : "";
        emailService.sendWaitlistRegistrationEmail(
                email,
                parentName.isBlank() ? displayName : parentName,
                accessToken,
                stationName,
                "de",
                stationIdForList(invite.listId()));

        // Notify managers
        repository
                .findById(invite.listId())
                .ifPresent(list -> notificationService.notifyMembersWithRole(
                        list.stationId(),
                        "WAITLIST_MANAGER",
                        NotificationType.WAITLIST_NEW_ENTRY,
                        NotificationData.of(
                                new NotificationParams.WaitlistNewEntry(displayName, list.name()),
                                new NotificationData.NotificationLink("waiting-lists", Map.of()))));

        return entry;
    }

    // --- Public self-service ---

    public Optional<WaitingListEntry> findEntryByToken(String token) {
        return repository.findEntryByToken(token);
    }

    public List<WaitingListEntryValue> findEntryValues(int entryId) {
        return repository.findEntryValues(entryId);
    }

    public void removeByToken(String token) {
        repository
                .findEntryByToken(token)
                .ifPresent(entry -> repository.updateEntryStatusWithTimestamp(
                        entry.id(), WaitingListEntryStatus.WITHDRAWN, "withdrawn_at"));
    }

    public void confirmInterest(String token) {
        repository.findEntryByToken(token).ifPresent(entry -> repository.updateConfirmedAt(entry.id(), Instant.now()));
    }

    // --- Entry management ---

    public List<WaitingListEntry> findEntriesByList(int listId) {
        return repository.findEntriesByList(listId);
    }

    public List<WaitingListEntry> findEntriesByStatus(int listId, WaitingListEntryStatus status) {
        return repository.findEntriesByStatus(listId, status);
    }

    public Optional<WaitingListEntry> findEntryById(int id) {
        return repository.findEntryById(id);
    }

    public WaitingListEntry createEntry(
            int listId,
            String firstname,
            String lastname,
            String parentName,
            String email,
            Map<Integer, String> fieldValues,
            String notes) {
        String accessToken = UUID.randomUUID().toString();
        var entry = repository.createEntry(
                listId, firstname, lastname, parentName, email, accessToken, notes != null ? notes : "");
        for (var e : fieldValues.entrySet()) {
            repository.upsertEntryValue(entry.id(), e.getKey(), e.getValue());
        }
        return entry;
    }

    public void updateEntry(
            int entryId,
            String firstname,
            String lastname,
            String parentName,
            String email,
            String notes,
            Map<Integer, String> fieldValues) {
        repository.updateEntry(entryId, firstname, lastname, parentName, email, notes != null ? notes : "");
        if (fieldValues != null) {
            for (var e : fieldValues.entrySet()) {
                repository.upsertEntryValue(entryId, e.getKey(), e.getValue());
            }
        }
    }

    public void updateCreatedAt(int entryId, Instant createdAt) {
        repository.updateCreatedAt(entryId, createdAt);
    }

    public void updateEntryStatus(int entryId, WaitingListEntryStatus status) {
        repository.updateEntryStatus(entryId, status);
    }

    public void deleteEntry(int entryId) {
        repository.deleteEntry(entryId);
    }

    public int countEntries(int listId) {
        return repository.countEntriesByList(listId);
    }

    // --- State transitions ---

    /**
     * Invite a WAITING entry: set status to INVITED, create a non-login member, assign testing group, link member.
     */
    public WaitingListEntry inviteEntry(int entryId) {
        var entry =
                repository.findEntryById(entryId).orElseThrow(() -> new IllegalArgumentException("Entry not found"));
        if (entry.status() != WaitingListEntryStatus.WAITING) {
            throw new IllegalStateException("Entry must be in WAITING status to invite");
        }
        var list = repository.findById(entry.listId()).orElseThrow();

        // Create a non-login station member (no account link) with MEMBER role
        var member = stationMemberRepository.createWithDisplayName(list.stationId(), entry.fullName());
        stationMemberRepository
                .findRoleByName(Roles.MEMBER)
                .ifPresent(role -> stationMemberRepository.addRole(member.id(), role.id()));

        // Assign testing group if configured
        if (list.testingGroupId() != null) {
            memberGroupRepository.addMember(list.testingGroupId(), member.id());
        }

        // Link member to entry and update status
        repository.linkMember(entryId, member.id());
        repository.updateEntryStatusWithTimestamp(entryId, WaitingListEntryStatus.INVITED, "invited_at");

        // Send invite email
        String stationName = resolveStationName(list.stationId());
        emailService.sendWaitlistRegistrationEmail(
                entry.email(),
                entry.parentName().isBlank() ? entry.fullName() : entry.parentName(),
                entry.accessToken(),
                stationName,
                "de",
                list.stationId());

        return repository.findEntryById(entryId).orElseThrow();
    }

    /**
     * Move an INVITED entry to TESTING status.
     */
    public WaitingListEntry moveToTesting(int entryId) {
        var entry =
                repository.findEntryById(entryId).orElseThrow(() -> new IllegalArgumentException("Entry not found"));
        if (entry.status() != WaitingListEntryStatus.INVITED) {
            throw new IllegalStateException("Entry must be in INVITED status to move to testing");
        }
        repository.updateEntryStatusWithTimestamp(entryId, WaitingListEntryStatus.TESTING, "testing_at");
        return repository.findEntryById(entryId).orElseThrow();
    }

    /**
     * Move a TESTING entry to JOINED: remove testing group, assign join group and join role.
     */
    public WaitingListEntry moveToJoined(int entryId) {
        var entry =
                repository.findEntryById(entryId).orElseThrow(() -> new IllegalArgumentException("Entry not found"));
        if (entry.status() != WaitingListEntryStatus.TESTING) {
            throw new IllegalStateException("Entry must be in TESTING status to join");
        }
        var list = repository.findById(entry.listId()).orElseThrow();

        if (entry.memberId() != null) {
            // Remove testing group
            if (list.testingGroupId() != null) {
                memberGroupRepository.removeMember(list.testingGroupId(), entry.memberId());
            }
            // Assign join group
            if (list.joinGroupId() != null) {
                memberGroupRepository.addMember(list.joinGroupId(), entry.memberId());
            }
            // Assign join role
            if (list.joinRoleId() != null) {
                stationMemberRepository.addRole(entry.memberId(), list.joinRoleId());
            }
        }

        repository.updateEntryStatusWithTimestamp(entryId, WaitingListEntryStatus.JOINED, "joined_at");
        return repository.findEntryById(entryId).orElseThrow();
    }

    /**
     * Withdraw an entry (from WAITING, INVITED, or TESTING).
     */
    public WaitingListEntry withdrawEntry(int entryId) {
        var entry =
                repository.findEntryById(entryId).orElseThrow(() -> new IllegalArgumentException("Entry not found"));
        if (entry.status() == WaitingListEntryStatus.JOINED || entry.status() == WaitingListEntryStatus.WITHDRAWN) {
            throw new IllegalStateException("Cannot withdraw an entry that is already JOINED or WITHDRAWN");
        }
        repository.updateEntryStatusWithTimestamp(entryId, WaitingListEntryStatus.WITHDRAWN, "withdrawn_at");
        return repository.findEntryById(entryId).orElseThrow();
    }

    // --- Scoring ---

    public double evaluateScore(
            WaitingListEntry entry, List<WaitingListEntryValue> values, List<WaitingListField> fields, String formula) {
        if (formula == null || formula.isBlank()) return 0.0;
        Map<String, String> variables = new HashMap<>();
        for (var field : fields) {
            String value = values.stream()
                    .filter(v -> v.fieldId() == field.id())
                    .map(WaitingListEntryValue::value)
                    .findFirst()
                    .orElse("0");
            // Strip JSON quotes if present
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            variables.put(field.name(), value);
        }

        // Built-in generated fields: waiting time
        long waitingDays = Duration.between(entry.createdAt(), Instant.now()).toDays();
        variables.put("wartezeit_tage", String.valueOf(waitingDays));
        variables.put("wartezeit_monate", String.valueOf(waitingDays / 30));
        variables.put("wartezeit_quartale", String.valueOf(waitingDays / 91));
        variables.put("wartezeit_jahre", String.valueOf(waitingDays / 365));

        // Preprocess age([fieldname]) function calls — replace with computed age value
        String processedFormula = formula;
        var agePattern = Pattern.compile("age\\(\\[([^]]+)]\\)");
        var matcher = agePattern.matcher(processedFormula);
        var sb = new StringBuilder();
        while (matcher.find()) {
            String fieldName = matcher.group(1);
            String dateValue = variables.getOrDefault(fieldName, "");
            long age = 0;
            if (!dateValue.isEmpty()) {
                try {
                    var birthDate = LocalDate.parse(dateValue);
                    age = ChronoUnit.YEARS.between(birthDate, LocalDate.now());
                } catch (Exception ignored) {
                }
            }
            matcher.appendReplacement(sb, String.valueOf(age));
        }
        matcher.appendTail(sb);
        processedFormula = sb.toString();

        return ScoreEvaluator.evaluate(processedFormula, variables);
    }

    // --- Confirmation checker ---

    private void checkAllExpiredConfirmations() {
        try {
            for (var list : repository.findAll()) {
                checkExpiredConfirmations(list);
            }
        } catch (Exception e) {
            log.warn("Error checking waiting list confirmations", e);
        }
    }

    public void checkExpiredConfirmations(WaitingList list) {
        String stationName = resolveStationName(list.stationId());

        // Send initial reminders for expired entries
        var expired = repository.findExpiredConfirmations(list.id(), list.confirmIntervalDays());
        for (var entry : expired) {
            emailService.sendWaitlistConfirmReminderEmail(
                    entry.email(),
                    entry.parentName().isBlank() ? entry.fullName() : entry.parentName(),
                    entry.accessToken(),
                    stationName,
                    "de",
                    list.stationId());
            repository.updateReminderSentAt(entry.id(), Instant.now());
        }

        // Send pre-removal warning (2 weeks before the 30-day grace period ends)
        var preRemoval = repository.findPreRemovalWarningDue(list.id());
        for (var entry : preRemoval) {
            emailService.sendWaitlistRemovalWarningEmail(
                    entry.email(),
                    entry.parentName().isBlank() ? entry.fullName() : entry.parentName(),
                    entry.accessToken(),
                    stationName,
                    "de",
                    list.stationId());
        }

        // Auto-remove entries past grace period
        var gracePeriodExpired = repository.findGracePeriodExpired(list.id());
        for (var entry : gracePeriodExpired) {
            repository.updateEntryStatus(entry.id(), WaitingListEntryStatus.WITHDRAWN);
        }
    }

    private Integer stationIdForList(int listId) {
        return repository.findById(listId).map(WaitingList::stationId).orElse(null);
    }

    private String resolveStationName(int stationId) {
        return stationRepository.findById(stationId).map(Station::name).orElse("");
    }
}
