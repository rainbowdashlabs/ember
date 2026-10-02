/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.api.refusal.WaitingListRefusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.WaitlistInvitationAnswered;
import dev.chojo.ember.event.events.WaitlistPublicRegistration;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AccountInviteService;
import dev.chojo.ember.feature.account.service.SetupMail;
import dev.chojo.ember.feature.legal.entity.ConsentProof;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.GroupMembershipService;
import dev.chojo.ember.feature.members.service.UserTypeChangeService;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.FieldTypes;
import dev.chojo.ember.feature.question.QuestionCheck;
import dev.chojo.ember.feature.question.QuestionValues;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.waitinglist.entity.GuardianInput;
import dev.chojo.ember.feature.waitinglist.entity.WaitingList;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListAnswer;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntry;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryGuardian;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryStatus;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryValue;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListField;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldConfig;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListInvitation;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListInvite;
import dev.chojo.ember.feature.waitinglist.repository.WaitingListRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Singleton
public class WaitingListService implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(WaitingListService.class);

    private final WaitingListRepository repository;
    private final StationRepository stationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final GroupMembershipService groupMemberships;
    private final UserTypeChangeService userTypeChanges;
    private final AccountRepository accountRepository;
    private final EmailService emailService;
    private final Notifier notifier;
    private final AccountInviteService accountInviteService;
    private final WaitlistInvitationMessage invitationMessage;
    private final DomainEventBus eventBus;

    @Inject
    public WaitingListService(
            WaitingListRepository repository,
            StationRepository stationRepository,
            StationMemberRepository stationMemberRepository,
            GroupMembershipService groupMemberships,
            UserTypeChangeService userTypeChanges,
            AccountRepository accountRepository,
            EmailService emailService,
            Notifier notifier,
            AccountInviteService accountInviteService,
            WaitlistInvitationMessage invitationMessage,
            DomainEventBus eventBus) {
        this.repository = repository;
        this.stationRepository = stationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.groupMemberships = groupMemberships;
        this.userTypeChanges = userTypeChanges;
        this.accountRepository = accountRepository;
        this.emailService = emailService;
        this.notifier = notifier;
        this.accountInviteService = accountInviteService;
        this.invitationMessage = invitationMessage;
        this.eventBus = eventBus;
    }

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
            @Nullable String scoringFormula,
            int confirmIntervalDays,
            @Nullable Integer testingGroupId,
            @Nullable Integer joinGroupId,
            int attendanceThreshold,
            boolean isPublic,
            boolean sendsMail,
            @Nullable Integer minAgeRegister,
            @Nullable Integer minAgeJoin) {
        requireGroupsFit(stationId, testingGroupId, joinGroupId);
        var list = repository.create(
                stationId,
                name,
                description,
                scoringFormula,
                confirmIntervalDays,
                testingGroupId,
                joinGroupId,
                attendanceThreshold,
                isPublic,
                sendsMail,
                minAgeRegister,
                minAgeJoin);
        log.info("Created waiting list {} on station {} (public {})", list.id(), stationId, isPublic);
        return list;
    }

    public Optional<WaitingList> update(
            int id,
            String name,
            String description,
            @Nullable String scoringFormula,
            int confirmIntervalDays,
            @Nullable Integer testingGroupId,
            @Nullable Integer joinGroupId,
            int attendanceThreshold,
            boolean isPublic,
            boolean sendsMail,
            @Nullable Integer minAgeRegister,
            @Nullable Integer minAgeJoin) {
        repository.findById(id).ifPresent(list -> requireGroupsFit(list.stationId(), testingGroupId, joinGroupId));
        var updated = repository.update(
                id,
                name,
                description,
                scoringFormula,
                confirmIntervalDays,
                testingGroupId,
                joinGroupId,
                attendanceThreshold,
                isPublic,
                sendsMail,
                minAgeRegister,
                minAgeJoin);
        if (updated.isPresent()) {
            log.info("Updated waiting list {}", id);
        } else {
            log.warn("Waiting list update affected zero rows for list {}", id);
        }
        return updated;
    }

    public Optional<WaitingList> updateVisibleFields(int id, String visibleFieldsJson) {
        var updated = repository.updateVisibleFields(id, visibleFieldsJson);
        if (updated.isPresent()) {
            log.info("Updated visible fields for waiting list {}", id);
        } else {
            log.warn("Visible-fields update affected zero rows for waiting list {}", id);
        }
        return updated;
    }

    public void delete(int id) {
        repository.delete(id);
        log.info("Deleted waiting list {}", id);
    }

    public List<WaitingListField> findFieldsByList(int listId) {
        return repository.findFieldsByList(listId);
    }

    public WaitingListField createField(
            int listId,
            String name,
            FieldType fieldType,
            WaitingListFieldConfig config,
            int position,
            boolean required,
            boolean isPublic) {
        requireOffered(fieldType);
        requireSingleBirthDate(listId, fieldType, 0, WaitingListRefusal.WAITING_LIST_SECOND_BIRTH_DATE_ON_CREATE);
        var field = repository.createField(listId, name, fieldType, config, position, required, isPublic);
        log.info("Created waiting-list field {} on list {} (type {})", field.id(), listId, fieldType);
        return field;
    }

    public Optional<WaitingListField> updateField(
            int fieldId,
            String name,
            FieldType fieldType,
            WaitingListFieldConfig config,
            int position,
            boolean required,
            boolean isPublic) {
        requireOffered(fieldType);
        repository
                .findFieldById(fieldId)
                .ifPresent(field -> requireSingleBirthDate(
                        field.listId(),
                        fieldType,
                        fieldId,
                        WaitingListRefusal.WAITING_LIST_SECOND_BIRTH_DATE_ON_CHANGE));
        var updated = repository.updateField(fieldId, name, fieldType, config, position, required, isPublic);
        if (updated.isPresent()) {
            log.info("Updated waiting-list field {}", fieldId);
        } else {
            log.warn("Field update affected zero rows for waiting-list field {}", fieldId);
        }
        return updated;
    }

    /**
     * Refuses a type a waiting list does not offer, such as a member or a place.
     *
     * @throws io.javalin.http.HttpResponseException {@link WaitingListRefusal#WAITING_LIST_FIELD_TYPE_NOT_OFFERED}
     */
    private static void requireOffered(FieldType fieldType) {
        if (!FieldTypes.WAITING_LIST.contains(fieldType))
            throw WaitingListRefusal.WAITING_LIST_FIELD_TYPE_NOT_OFFERED.raise();
    }

    /**
     * Rejects a second birth date field on the same list.
     *
     * <p>One is what makes the age findable without being told where it is. Two would leave the
     * list to guess, and the guess would be silent.
     *
     * @param excludedId the field being changed, so it does not clash with itself; 0 when creating
     * @param refusal    what the caller refuses a second date of birth with
     */
    private void requireSingleBirthDate(int listId, FieldType fieldType, int excludedId, Refusal refusal) {
        if (fieldType != FieldType.BIRTH_DATE) return;
        findFieldsByList(listId).stream()
                .filter(existing -> existing.fieldType() == FieldType.BIRTH_DATE)
                .filter(existing -> existing.id() != excludedId)
                .findFirst()
                .ifPresent(existing -> {
                    throw refusal.raise(existing.name());
                });
    }

    public void deleteField(int fieldId) {
        repository.deleteField(fieldId);
        log.info("Deleted waiting-list field {}", fieldId);
    }

    public List<WaitingListInvite> findInvitesByList(int listId) {
        return repository.findInvitesByList(listId);
    }

    public WaitingListInvite createInvite(int listId, int maxUses, @Nullable Instant expiresAt) {
        String code = UUID.randomUUID().toString();
        var invite = repository.createInvite(listId, code, maxUses, expiresAt);
        log.info("Created waiting-list invite {} on list {} (maxUses {})", invite.id(), listId, maxUses);
        return invite;
    }

    public Optional<WaitingListInvite> findInviteByCode(String code) {
        return repository.findInviteByCode(code);
    }

    public void deleteInvite(int inviteId) {
        repository.deleteInvite(inviteId);
        log.info("Deleted waiting-list invite {}", inviteId);
    }

    public WaitingListEntry registerViaInvite(
            String inviteCode,
            String firstname,
            String lastname,
            List<GuardianInput> guardians,
            Map<Integer, JsonNode> fieldValues,
            @Nullable String notes,
            ConsentProof consent) {
        var invite = repository
                .findInviteByCode(inviteCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite code"));
        if (!invite.hasUsesLeft()) {
            throw new IllegalStateException("Invite code has been used up");
        }
        if (invite.isExpired()) {
            throw new IllegalStateException("Invite code has expired");
        }

        String parentName = primaryGuardianName(guardians);
        String email = primaryGuardianEmail(guardians);

        String accessToken = UUID.randomUUID().toString();
        var entry = repository.createEntry(
                invite.listId(),
                firstname,
                lastname,
                parentName,
                email,
                accessToken,
                notes != null ? notes : "",
                consent);
        repository.incrementInviteUses(invite.id());

        if (guardians != null) {
            insertGuardians(entry.id(), guardians);
        }

        writeAnswers(
                invite.listId(),
                entry.id(),
                fieldValues,
                WaitingListRefusal.WAITING_LIST_ANSWER_NOT_ACCEPTED_ON_INVITE);

        String displayName = entry.fullName();

        var listForEmail = repository.findById(invite.listId()).orElse(null);
        String stationName = listForEmail != null ? resolveStationName(listForEmail.stationId()) : "";
        int stationId = stationIdForList(invite.listId());
        boolean writesToThem = listForEmail == null || listForEmail.sendsMail();
        if (guardians != null && writesToThem) {
            for (var g : guardians) {
                if (g.email() != null && !g.email().isBlank()) {
                    emailService.sendWaitlistRegistrationEmail(
                            g.email(),
                            g.firstname().isBlank() ? displayName : g.firstname(),
                            accessToken,
                            stationName,
                            languageOf(stationId),
                            stationId);
                }
            }
        }

        repository
                .findById(invite.listId())
                .ifPresent(list -> notifier.notify(
                        StationAudience.holders(list.stationId(), StationPermission.WAITLIST_MANAGER),
                        NotificationType.WAITLIST_NEW_ENTRY,
                        NotificationData.of(
                                new NotificationParams.WaitlistNewEntry(displayName, list.name()),
                                new NotificationData.NotificationLink("waiting-lists", Map.of())),
                        Delivery.EVERY_TIME));

        log.info(
                "Registered waiting-list entry {} on list {} (station {}) via invite",
                entry.id(),
                invite.listId(),
                stationId);
        return entry;
    }

    public Optional<WaitingListEntry> findEntryByToken(String token) {
        return repository.findEntryByToken(token);
    }

    public List<WaitingListEntryValue> findEntryValues(int entryId) {
        return repository.findEntryValues(entryId);
    }

    /**
     * Takes an entry off the list on the strength of its own access token, and removes it for good.
     *
     * <p>Only while the entry is WAITING or INVITED, because that is where nothing has been built on
     * it yet. The token never rotates and never expires, so a link that has been sitting in a mailbox
     * for years still names the entry it was sent for; once that entry is in testing or has joined
     * there is a member behind it, with attendance and guardians of their own, and a mail nobody has
     * to prove they still hold must not be able to destroy that. Withdrawal from the station side
     * keeps its wider reach, because somebody with a permission is standing behind it.
     *
     * @param token the entry's access token
     * @throws dev.chojo.ember.api.refusal.RefusalResponse when the entry has moved past being a list entry
     */
    public void removeByToken(String token) {
        repository
                .findEntryByToken(token)
                .ifPresentOrElse(
                        entry -> {
                            if (entry.status() != WaitingListEntryStatus.WAITING
                                    && entry.status() != WaitingListEntryStatus.INVITED) {
                                log.info(
                                        "Self-service removal refused for waiting-list entry {} (is {})",
                                        entry.id(),
                                        entry.status());
                                throw WaitingListRefusal.WAITING_LIST_ENTRY_NO_LONGER_REMOVABLE.raise();
                            }
                            withdrawEntry(entry.id());
                            log.info("Removed waiting-list entry {} via self-service token", entry.id());
                        },
                        () -> log.warn("Self-service withdrawal skipped: no waiting-list entry for token"));
    }

    public void confirmInterest(String token) {
        repository
                .findEntryByToken(token)
                .ifPresentOrElse(
                        entry -> {
                            repository.updateConfirmedAt(entry.id(), Instant.now());
                            log.info("Confirmed interest for waiting-list entry {}", entry.id());
                        },
                        () -> log.warn("Interest confirmation skipped: no waiting-list entry for token"));
    }

    public List<WaitingListEntry> findEntriesByList(int listId) {
        return repository.findEntriesByList(listId);
    }

    public List<WaitingListEntry> findEntriesByStatus(int listId, WaitingListEntryStatus status) {
        return repository.findEntriesByStatus(listId, status);
    }

    public Optional<WaitingListEntry> findEntryById(int id) {
        return repository.findEntryById(id);
    }

    /**
     * Writes the answers a sign-up gives, measured against the questions the list asks.
     *
     * <p>The one path for all three ways an entry gains answers: an invite code, a form somebody at
     * the station fills in, and an edit afterwards. Each used to write straight through, so a list
     * could hold a date that is not a date and a choice nobody offered.
     *
     * <p>What is left blank is not refused here. A list decides for itself which of its questions
     * have to be answered, and turning a family away mid-form over a question they were about to
     * reach is not what the check is for. Every answer is kept in the one shape its type is kept in,
     * and a blank one clears what the question held.
     *
     * @param refusal what the caller refuses an answer with
     * @throws dev.chojo.ember.api.refusal.RefusalResponse naming the question and what is wrong with the
     *                                             answer
     */
    private void writeAnswers(int listId, int entryId, Map<Integer, JsonNode> fieldValues, Refusal refusal) {
        if (fieldValues == null || fieldValues.isEmpty()) return;
        var fields = repository.findFieldsByList(listId).stream()
                .collect(Collectors.toMap(WaitingListField::id, field -> field));
        for (var answer : fieldValues.entrySet()) {
            var field = fields.get(answer.getKey());
            if (field == null) continue;
            QuestionCheck.answerIfGiven(field.question(), QuestionValues.read(answer.getValue()))
                    .ifPresent(problem -> {
                        throw refusal.raise(problem.question());
                    });
        }
        for (var answer : fieldValues.entrySet()) {
            var field = fields.get(answer.getKey());
            if (field == null) continue;
            var stored = QuestionValues.write(field.fieldType(), QuestionValues.read(answer.getValue()));
            if (stored == null) {
                repository.deleteEntryValue(entryId, field.id());
            } else {
                repository.upsertEntryValue(entryId, field.id(), stored);
            }
        }
    }

    public WaitingListEntry createEntry(
            int listId,
            String firstname,
            String lastname,
            List<GuardianInput> guardians,
            Map<Integer, JsonNode> fieldValues,
            @Nullable String notes) {
        String parentName = primaryGuardianName(guardians);
        String email = primaryGuardianEmail(guardians);
        String accessToken = UUID.randomUUID().toString();
        var entry = repository.createEntry(
                listId, firstname, lastname, parentName, email, accessToken, notes != null ? notes : "", null);
        if (guardians != null) {
            insertGuardians(entry.id(), guardians);
        }
        writeAnswers(listId, entry.id(), fieldValues, WaitingListRefusal.WAITING_LIST_ANSWER_NOT_ACCEPTED_ON_CREATE);
        log.info("Created waiting-list entry {} on list {}", entry.id(), listId);
        return entry;
    }

    public void updateEntry(
            int entryId,
            String firstname,
            String lastname,
            List<GuardianInput> guardians,
            @Nullable String notes,
            @Nullable Map<Integer, JsonNode> fieldValues) {
        String parentName = primaryGuardianName(guardians);
        String email = primaryGuardianEmail(guardians);
        repository.updateEntry(entryId, firstname, lastname, parentName, email, notes != null ? notes : "");
        if (guardians != null) {
            repository.deleteGuardiansByEntry(entryId);
            insertGuardians(entryId, guardians);
        }
        if (fieldValues != null) {
            repository
                    .findEntryById(entryId)
                    .ifPresent(entry -> writeAnswers(
                            entry.listId(),
                            entryId,
                            fieldValues,
                            WaitingListRefusal.WAITING_LIST_ANSWER_NOT_ACCEPTED_ON_CHANGE));
        }
        log.info("Updated waiting-list entry {}", entryId);
    }

    public void updateCreatedAt(int entryId, Instant createdAt) {
        repository.updateCreatedAt(entryId, createdAt);
        log.info("Updated created-at for waiting-list entry {}", entryId);
    }

    public void updateEntryStatus(int entryId, WaitingListEntryStatus status) {
        repository.updateEntryStatus(entryId, status);
        log.info("Set waiting-list entry {} status to {}", entryId, status);
    }

    public void deleteEntry(int entryId) {
        repository.deleteEntry(entryId);
        log.info("Deleted waiting-list entry {}", entryId);
    }

    public int countEntries(int listId) {
        return repository.countEntriesByList(listId);
    }

    /**
     * Invite a WAITING entry: record the appointment it is about, set status to INVITED, stamp the
     * moment and write the invitation to the guardians.
     *
     * <p>Nothing is created here. The account, the membership, the trial user type and the testing
     * group only come into being once the person actually turns up, in {@link #moveToTesting(int)}.
     * Until then the entry is a waiting list entry and nothing else, so a refusal or a withdrawal
     * leaves no member behind.
     *
     * <p>The invitation names an appointment and a date and nobody is signed up from it. They have
     * not joined anything, so putting them on the attendee list would make them part of a Tuesday
     * they never agreed to and would count them in the totals the station plans from.
     *
     * <p>A list that writes to nobody records the invitation and sends nothing. The entry still
     * moves to invited, because the station has invited them, by whatever means it uses instead.
     *
     * @param invitation the appointment they are asked to come to, or {@code null} to invite without
     *                   naming one
     */
    public WaitingListEntry inviteEntry(int entryId, @Nullable WaitingListInvitation invitation) {
        var entry =
                repository.findEntryById(entryId).orElseThrow(() -> new IllegalArgumentException("Entry not found"));
        if (entry.status() != WaitingListEntryStatus.WAITING) {
            throw new IllegalStateException("Entry must be in WAITING status to invite");
        }
        var list = repository.findById(entry.listId()).orElseThrow();
        var station = stationRepository.findById(list.stationId()).orElseThrow();

        Transactions.run(() -> {
            repository.updateInvitation(entryId, invitation);
            repository.updateEntryStatusWithTimestamp(entryId, WaitingListEntryStatus.INVITED, "invited_at");
        });
        log.info("Invited waiting-list entry {} on station {}", entryId, list.stationId());

        if (list.sendsMail()) {
            invitationMessage.send(entry, repository.findGuardiansByEntry(entryId), station, invitation);
        }

        return repository.findEntryById(entryId).orElseThrow();
    }

    /**
     * Takes an invited entry back to waiting, which is what a station does when the answer was that
     * the date does not suit.
     *
     * <p>The invitation goes with it: an entry carries one current invitation, so an answer given
     * from a mail that has been superseded can never apply to the one that replaced it.
     */
    public WaitingListEntry returnToWaiting(int entryId) {
        var entry =
                repository.findEntryById(entryId).orElseThrow(() -> new IllegalArgumentException("Entry not found"));
        if (entry.status() != WaitingListEntryStatus.INVITED) {
            throw new IllegalStateException("Entry must be in INVITED status to go back to waiting");
        }
        Transactions.run(() -> {
            repository.updateInvitation(entryId, null);
            repository.updateEntryStatus(entryId, WaitingListEntryStatus.WAITING);
        });
        log.info("Returned waiting-list entry {} to waiting", entryId);
        return repository.findEntryById(entryId).orElseThrow();
    }

    /**
     * Records what somebody answered to the invitation their entry currently holds, and tells the
     * station.
     *
     * <p>The answer names the appointment it answers and is refused when that is not the appointment
     * the entry is currently invited to. Together with an entry carrying one current invitation, that is
     * what makes a click from a mail that has been superseded harmless rather than misleading.
     *
     * <p>A refusal is recorded and nothing more. Withdrawing the entry would move it out of the
     * section the manager is looking at, and an answer that disappears on arrival is the same
     * failure as no answer at all.
     *
     * @param eventId the appointment the answer is about, {@code null} for an invitation naming none
     * @param date    the one date of it, {@code null} for the same
     */
    public WaitingListEntry answerInvitation(
            String token,
            @Nullable Integer eventId,
            @Nullable LocalDate date,
            WaitingListAnswer answer,
            @Nullable String note) {
        var entry =
                repository.findEntryByToken(token).orElseThrow(() -> new IllegalArgumentException("Entry not found"));
        if (entry.status() != WaitingListEntryStatus.INVITED) {
            log.info("Invitation answer refused for waiting-list entry {} (is {})", entry.id(), entry.status());
            throw WaitingListRefusal.WAITING_LIST_INVITATION_NO_LONGER_OPEN.raise();
        }
        requireAnswersTheCurrentInvitation(entry, eventId, date);

        repository.updateInvitationAnswer(entry.id(), answer, note == null ? "" : note.trim());
        log.info("Waiting-list entry {} answered its invitation with {}", entry.id(), answer);

        repository
                .findById(entry.listId())
                .ifPresent(list -> eventBus.publish(
                        new WaitlistInvitationAnswered(list.stationId(), entry.fullName(), list.name(), answer)));

        return repository.findEntryById(entry.id()).orElseThrow();
    }

    /**
     * Refuses an answer given to an invitation the entry no longer holds.
     *
     * <p>The token never expires and an old mail stays in a mailbox for good, so what the answer
     * says it is about has to match what the entry is actually invited to.
     */
    private static void requireAnswersTheCurrentInvitation(
            WaitingListEntry entry, @Nullable Integer eventId, @Nullable LocalDate date) {
        var current = entry.invitation();
        boolean matches = current == null
                ? eventId == null
                : Integer.valueOf(current.eventId()).equals(eventId)
                        && current.date().equals(date);
        if (!matches) {
            throw WaitingListRefusal.WAITING_LIST_INVITATION_ANSWER_FOR_ANOTHER.raise();
        }
    }

    /**
     * Move an INVITED entry to TESTING, which is where the member comes into being: the account, the
     * membership, the trial user type, the testing group and the permission that lets the station
     * see them.
     *
     * <p>Every effect carries its own guard rather than one guard around the block. An entry invited
     * before this moved here already has a member, and skipping everything for it would leave it out
     * of a testing group the list gained after the invitation. Setting the user type and granting the
     * permission are idempotent on their own, and so is joining the testing group.
     *
     * <p>The writes run as one, so a failure halfway cannot leave a member nothing points at.
     */
    public WaitingListEntry moveToTesting(int entryId) {
        var entry =
                repository.findEntryById(entryId).orElseThrow(() -> new IllegalArgumentException("Entry not found"));
        if (entry.status() != WaitingListEntryStatus.INVITED) {
            throw new IllegalStateException("Entry must be in INVITED status to move to testing");
        }
        var list = repository.findById(entry.listId()).orElseThrow();

        int memberId = Transactions.call(() -> {
            Integer existing = entry.memberId();
            int member = existing != null ? existing : createTrialMember(entry, list.stationId());
            userTypeChanges.change(member, StationUserType.TRIAL);
            stationMemberRepository
                    .findPermissionByName(StationPermission.USER)
                    .ifPresent(permission -> stationMemberRepository.grantPermission(member, permission.id()));
            Integer testingGroupId = list.testingGroupId();
            if (testingGroupId != null) {
                groupMemberships.joinAutomatically(testingGroupId, member);
            }
            repository.updateEntryStatusWithTimestamp(entryId, WaitingListEntryStatus.TESTING, "testing_at");
            return member;
        });

        log.info(
                "Moved waiting-list entry {} to testing on station {} (member {})",
                entryId,
                list.stationId(),
                memberId);
        return repository.findEntryById(entryId).orElseThrow();
    }

    /**
     * Creates the account and the membership the trial period runs on and points the entry at it.
     *
     * <p>The account carries no address of its own: the people who can be written to are the
     * guardians, and they get their own accounts when the entry joins.
     *
     * @return the id of the new member
     */
    private int createTrialMember(WaitingListEntry entry, int stationId) {
        var account = accountRepository.create(null, entry.firstname(), entry.lastname(), stationId);
        var member = stationMemberRepository.create(stationId, account.id());
        repository.linkMember(entry.id(), member.id());
        return member.id();
    }

    /**
     * Move a TESTING entry to JOINED: remove testing group, set MEMBER type, assign join group,
     * and create guardian accounts for each guardian on the entry.
     *
     * <p>The type is set before the join group is added, because a group bound to members takes
     * nobody who is still on trial.
     */
    public WaitingListEntry moveToJoined(int entryId) {
        var entry =
                repository.findEntryById(entryId).orElseThrow(() -> new IllegalArgumentException("Entry not found"));
        if (entry.status() != WaitingListEntryStatus.TESTING) {
            throw new IllegalStateException("Entry must be in TESTING status to join");
        }
        var list = repository.findById(entry.listId()).orElseThrow();

        Integer memberId = entry.memberId();
        if (memberId != null) {
            Integer testingGroupId = list.testingGroupId();
            if (testingGroupId != null) {
                groupMemberships.leaveAutomatically(testingGroupId, memberId);
            }
            stationMemberRepository
                    .findPermissionByName(StationPermission.USER)
                    .ifPresent(role -> stationMemberRepository.revokePermission(memberId, role.id()));
            userTypeChanges.change(memberId, StationUserType.MEMBER);
            Integer joinGroupId = list.joinGroupId();
            if (joinGroupId != null) {
                groupMemberships.joinAutomatically(joinGroupId, memberId);
            }

            createGuardianAccounts(entry, memberId, list);
        }

        repository.updateEntryStatusWithTimestamp(entryId, WaitingListEntryStatus.JOINED, "joined_at");
        log.info("Moved waiting-list entry {} to joined on station {}", entryId, list.stationId());
        return repository.findEntryById(entryId).orElseThrow();
    }

    /**
     * Withdraw an entry (from WAITING, INVITED, or TESTING).
     * Deletes the linked member, orphaned account, and the entry itself.
     */
    public void withdrawEntry(int entryId) {
        var entry =
                repository.findEntryById(entryId).orElseThrow(() -> new IllegalArgumentException("Entry not found"));
        if (entry.status() == WaitingListEntryStatus.JOINED || entry.status() == WaitingListEntryStatus.WITHDRAWN) {
            throw new IllegalStateException("Cannot withdraw an entry that is already JOINED or WITHDRAWN");
        }

        Integer memberId = entry.memberId();
        if (memberId != null) {
            var member = stationMemberRepository.findById(memberId).orElse(null);
            if (member != null) {
                stationMemberRepository.delete(member.id());
                Integer accountId = member.accountId();
                if (accountId != null) {
                    var otherMembers = stationMemberRepository.findAllByAccountId(accountId);
                    if (otherMembers.isEmpty()) {
                        var account = accountRepository.findById(accountId).orElse(null);
                        if (account != null && account.email() == null) {
                            accountRepository.delete(account.id());
                        }
                    }
                }
            }
        }

        repository.deleteEntry(entryId);
        log.info("Withdrew waiting-list entry {} (was {})", entryId, entry.status());
    }

    /**
     * Computes the waiting-list position of an entry ranked by score (highest first),
     * with {@link WaitingListEntry#createdAt()} as the tiebreaker so the order is stable.
     * Only entries in {@link WaitingListEntryStatus#WAITING} take part in the ranking;
     * if the given entry is not WAITING the method returns {@code 0}.
     *
     * @param entry the entry to find the position of
     * @return 1-based position when WAITING, or {@code 0} otherwise
     */
    public int findWaitingPositionByScore(WaitingListEntry entry) {
        if (entry.status() != WaitingListEntryStatus.WAITING) return 0;
        var list = repository.findById(entry.listId()).orElseThrow();
        var fields = repository.findFieldsByList(entry.listId());
        var waiting = repository.findEntriesByList(entry.listId()).stream()
                .filter(e -> e.status() == WaitingListEntryStatus.WAITING)
                .toList();
        record Scored(WaitingListEntry e, double score) {}
        var ranked = waiting.stream()
                .map(e -> {
                    var values = repository.findEntryValues(e.id());
                    return new Scored(e, evaluateScore(e, values, fields, list.scoringFormula()));
                })
                .sorted((a, b) -> {
                    int cmp = Double.compare(b.score(), a.score());
                    return cmp != 0 ? cmp : a.e().createdAt().compareTo(b.e().createdAt());
                })
                .toList();
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i).e().id() == entry.id()) return i + 1;
        }
        return 0;
    }

    /**
     * The field a list reads a date of birth from, if it has declared one.
     *
     * <p>Declared by type rather than by name, so nothing has to be told which field holds it and a
     * list that renames the field keeps working.
     */
    public Optional<WaitingListField> birthDateField(int listId) {
        return findFieldsByList(listId).stream()
                .filter(field -> field.fieldType() == FieldType.BIRTH_DATE)
                .findFirst();
    }

    /**
     * How old somebody on the list is today, from whatever they answered in the birth date field.
     *
     * @return the age in whole years, or empty when the list has no birth date field or the entry
     *         left it unanswered
     */
    public Optional<Integer> ageOf(int listId, List<WaitingListEntryValue> values) {
        return birthDateField(listId).flatMap(field -> values.stream()
                .filter(value -> value.fieldId() == field.id())
                .findFirst()
                .flatMap(value -> ageFrom(QuestionValues.read(value.value()))));
    }

    private static Optional<Integer> ageFrom(String date) {
        if (date.isBlank()) return Optional.empty();
        try {
            return Optional.of((int) ChronoUnit.YEARS.between(LocalDate.parse(date.trim()), LocalDate.now()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * How old somebody signing themselves up is, from what they just filled in.
     *
     * @param values the answers as submitted, keyed by field
     */
    public Optional<Integer> ageFromSubmitted(int listId, Map<Integer, JsonNode> values) {
        return birthDateField(listId).flatMap(field -> ageFrom(QuestionValues.read(values.get(field.id()))));
    }

    /**
     * Refuses a sign-up from somebody too young for the list to take.
     *
     * <p>Only when an age can actually be worked out. A list asking for a birth date that nobody
     * filled in is a form to fix, not a person to turn away.
     */
    public void requireOldEnoughToRegister(WaitingList list, Map<Integer, JsonNode> values) {
        Integer minAge = list.minAgeRegister();
        if (minAge == null) return;
        ageFromSubmitted(list.id(), values).ifPresent(age -> {
            if (age < minAge) {
                throw WaitingListRefusal.WAITING_LIST_REGISTRANT_TOO_YOUNG.raise(
                        RefusalDetail.count(minAge, RefusalDetail.CountUnit.YEARS));
            }
        });
    }

    /**
     * Whether this entry is waiting for its age rather than for its turn.
     *
     * <p>An entry whose age cannot be worked out is not held back: a list that asks for a birth date
     * and does not get one is a gap in the answers, not a reason to treat somebody as too young.
     */
    public boolean belowJoinAge(WaitingList list, Optional<Integer> age) {
        Integer minAge = list.minAgeJoin();
        if (minAge == null) return false;
        return age.map(years -> years < minAge).orElse(false);
    }

    public double evaluateScore(
            WaitingListEntry entry,
            List<WaitingListEntryValue> values,
            List<WaitingListField> fields,
            @Nullable String formula) {
        if (formula == null || formula.isBlank()) return 0.0;
        Map<String, String> variables = new HashMap<>();
        for (var field : fields) {
            String value = values.stream()
                    .filter(v -> v.fieldId() == field.id())
                    .map(entryValue -> QuestionValues.read(entryValue.value()))
                    .filter(text -> !text.isEmpty())
                    .findFirst()
                    .orElse("0");
            variables.put(field.name(), value);
        }

        putWaitingTime(variables, entry);
        return ScoreEvaluator.evaluate(substituteAgeCalls(formula, variables), variables);
    }

    private static void putWaitingTime(Map<String, String> variables, WaitingListEntry entry) {
        long waitingDays = Duration.between(entry.createdAt(), Instant.now()).toDays();
        variables.put("wartezeit_tage", String.valueOf(waitingDays));
        variables.put("wartezeit_monate", String.valueOf(waitingDays / 30));
        variables.put("wartezeit_quartale", String.valueOf(waitingDays / 91));
        variables.put("wartezeit_jahre", String.valueOf(waitingDays / 365));
    }

    /** Replaces every {@code age([field])} call with the age in years the field's date gives today. */
    private static String substituteAgeCalls(String formula, Map<String, String> variables) {
        var agePattern = Pattern.compile("age\\(\\[([^]]+)]\\)");
        var matcher = agePattern.matcher(formula);
        var sb = new StringBuilder();
        while (matcher.find()) {
            String fieldName = matcher.group(1);
            String dateValue = variables.getOrDefault(fieldName, "");
            long age = 0;
            if (!dateValue.isEmpty()) {
                try {
                    var birthDate = LocalDate.parse(dateValue);
                    age = ChronoUnit.YEARS.between(birthDate, LocalDate.now());
                } catch (Exception e) {
                    log.warn("Field '{}' does not hold a date, its age counts as 0 in the score", fieldName, e);
                }
            }
            matcher.appendReplacement(sb, String.valueOf(age));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Asks the people on a list whether they are still interested, and drops those who never say so.
     *
     * <p>A list that writes to nobody is left alone entirely, removals included: the reminder and the
     * warning are the whole of what a person is given before they are dropped, and dropping somebody
     * who was never asked is losing them rather than tidying up.
     */
    public void checkExpiredConfirmations(WaitingList list) {
        if (!list.sendsMail()) return;
        String stationName = resolveStationName(list.stationId());
        String language = languageOf(list.stationId());

        var expired = repository.findExpiredConfirmations(list.id(), list.confirmIntervalDays());
        for (var entry : expired) {
            emailService.sendWaitlistConfirmReminderEmail(
                    entry.email(),
                    entry.parentName().isBlank() ? entry.fullName() : entry.parentName(),
                    entry.accessToken(),
                    stationName,
                    language,
                    list.stationId());
            repository.updateReminderSentAt(entry.id(), Instant.now());
        }

        var preRemoval = repository.findPreRemovalWarningDue(list.id());
        for (var entry : preRemoval) {
            emailService.sendWaitlistRemovalWarningEmail(
                    entry.email(),
                    entry.parentName().isBlank() ? entry.fullName() : entry.parentName(),
                    entry.accessToken(),
                    stationName,
                    language,
                    list.stationId());
        }

        var gracePeriodExpired = repository.findGracePeriodExpired(list.id());
        for (var entry : gracePeriodExpired) {
            repository.updateEntryStatus(entry.id(), WaitingListEntryStatus.WITHDRAWN);
        }
        if (!gracePeriodExpired.isEmpty()) {
            log.info(
                    "Auto-withdrew {} waiting-list entries past grace period on list {}",
                    gracePeriodExpired.size(),
                    list.id());
        }
    }

    public List<WaitingListEntryGuardian> findGuardiansByEntry(int entryId) {
        return repository.findGuardiansByEntry(entryId);
    }

    public List<WaitingListEntryGuardian> findGuardiansByList(int listId) {
        return repository.findGuardiansByList(listId);
    }

    public List<WaitingList> findPublicByStation(int stationId) {
        return repository.findPublicByStation(stationId);
    }

    public List<WaitingListField> findPublicFieldsByList(int listId) {
        return repository.findPublicFieldsByList(listId);
    }

    public boolean hasPublicWaitlists(int stationId) {
        return repository.hasPublicWaitlists(stationId);
    }

    /**
     * Takes what somebody filled in on the public form, which waits for their address to be
     * confirmed before it becomes an entry.
     *
     * <p>A list that writes to nobody has nothing to confirm with and no way of asking, so the
     * registration becomes an entry at once. It is still one waiting to be looked at: the mail was
     * never what decided who gets onto the list, whoever manages it is.
     */
    public void submitPublicRegistration(
            int listId,
            String firstname,
            String lastname,
            String email,
            List<GuardianInput> guardians,
            Map<Integer, JsonNode> fieldValues,
            @Nullable String notes,
            ConsentProof consent) {
        var list = repository.findById(listId).orElseThrow(() -> new IllegalArgumentException("List not found"));
        if (!list.isPublic()) {
            throw new IllegalStateException("List is not public");
        }

        if (!list.sendsMail()) {
            var entry = createPendingEntry(list, firstname, lastname, email, guardians, fieldValues, notes, consent);
            log.info(
                    "Public waiting-list registration accepted unconfirmed as entry {} on list {} (station {})",
                    entry.id(),
                    listId,
                    list.stationId());
            return;
        }

        String token = UUID.randomUUID().toString();
        repository.createVerificationToken(
                token,
                listId,
                firstname,
                lastname,
                email,
                guardians != null ? guardians : List.of(),
                fieldValues != null ? fieldValues : Map.of(),
                notes != null ? notes : "",
                consent);

        String stationName = resolveStationName(list.stationId());
        emailService.sendWaitlistVerifyEmail(
                email, firstname, stationName, token, languageOf(list.stationId()), list.stationId());
        log.info(
                "Public waiting-list registration awaiting verification for list {} (station {})",
                listId,
                list.stationId());
    }

    public boolean verifyPublicRegistration(String token) {
        var verification = repository.findVerificationByToken(token).orElse(null);
        if (verification == null) return false;
        if (verification.isExpired()) {
            repository.deleteVerificationToken(verification.id());
            return false;
        }

        var list = repository.findById(verification.listId()).orElse(null);
        if (list == null) {
            repository.deleteVerificationToken(verification.id());
            return false;
        }

        var entry = createPendingEntry(
                list,
                verification.firstname(),
                verification.lastname(),
                verification.email(),
                verification.guardians(),
                verification.fieldValues(),
                verification.notes(),
                verification.consent());

        repository.deleteVerificationToken(verification.id());

        log.info(
                "Verified public waiting-list registration: created entry {} on list {}",
                entry.id(),
                verification.listId());
        return true;
    }

    /** Writes a public registration onto the list, where it waits for somebody to look at it. */
    private WaitingListEntry createPendingEntry(
            WaitingList list,
            String firstname,
            String lastname,
            String email,
            List<GuardianInput> guardians,
            Map<Integer, JsonNode> fieldValues,
            @Nullable String notes,
            @Nullable ConsentProof consent) {
        var entry = repository.createEntryWithStatus(
                list.id(),
                firstname,
                lastname,
                primaryGuardianName(guardians),
                email,
                UUID.randomUUID().toString(),
                notes != null ? notes : "",
                WaitingListEntryStatus.PENDING,
                consent);

        if (guardians != null) {
            insertGuardians(entry.id(), guardians);
        }
        if (fieldValues != null) {
            for (var value : fieldValues.entrySet()) {
                repository.upsertEntryValue(entry.id(), value.getKey(), value.getValue());
            }
        }

        eventBus.publish(new WaitlistPublicRegistration(list.stationId(), entry.fullName(), list.name()));
        return entry;
    }

    public WaitingListEntry approvePendingEntry(int entryId) {
        var entry =
                repository.findEntryById(entryId).orElseThrow(() -> new IllegalArgumentException("Entry not found"));
        if (entry.status() != WaitingListEntryStatus.PENDING) {
            throw new IllegalStateException("Entry is not pending");
        }
        repository.updateEntryStatus(entryId, WaitingListEntryStatus.WAITING);
        log.info("Approved pending waiting-list entry {}", entryId);

        var list = repository.findById(entry.listId()).orElse(null);
        if (list == null || list.sendsMail()) {
            String stationName = list != null ? resolveStationName(list.stationId()) : "";
            int stationId = list != null ? list.stationId() : 0;
            String language = languageOf(stationId);
            var guardians = repository.findGuardiansByEntry(entryId);
            for (var g : guardians) {
                if (g.email() != null && !g.email().isBlank()) {
                    emailService.sendWaitlistRegistrationEmail(
                            g.email(), g.fullName(), entry.accessToken(), stationName, language, stationId);
                }
            }
            if (guardians.isEmpty() && entry.email() != null && !entry.email().isBlank()) {
                emailService.sendWaitlistRegistrationEmail(
                        entry.email(), entry.fullName(), entry.accessToken(), stationName, language, stationId);
            }
        }

        return repository.findEntryById(entryId).orElseThrow();
    }

    public void rejectPendingEntry(int entryId) {
        var entry =
                repository.findEntryById(entryId).orElseThrow(() -> new IllegalArgumentException("Entry not found"));
        if (entry.status() != WaitingListEntryStatus.PENDING) {
            throw new IllegalStateException("Entry is not pending");
        }
        repository.deleteEntry(entryId);
        log.info("Rejected pending waiting-list entry {}", entryId);
    }

    private void checkAllExpiredConfirmations() {
        try {
            for (var list : repository.findAll()) {
                checkExpiredConfirmations(list);
            }
        } catch (Exception e) {
            log.warn("Error checking waiting list confirmations", e);
        }
    }

    /**
     * Derives the primary guardian's display name from the guardian list, or an empty
     * string when no guardian is present.
     *
     * @param guardians the guardian inputs, may be {@code null}
     * @return the trimmed primary guardian name, or an empty string
     */
    private static String primaryGuardianName(List<GuardianInput> guardians) {
        return guardians != null && !guardians.isEmpty()
                ? (guardians.getFirst().firstname() + " " + guardians.getFirst().lastname()).trim()
                : "";
    }

    /**
     * Derives the primary guardian's email from the guardian list, or an empty string
     * when no guardian is present.
     *
     * @param guardians the guardian inputs, may be {@code null}
     * @return the primary guardian email, or an empty string
     */
    private static String primaryGuardianEmail(List<GuardianInput> guardians) {
        return guardians != null && !guardians.isEmpty() ? guardians.getFirst().email() : "";
    }

    private void insertGuardians(int entryId, List<GuardianInput> guardians) {
        for (int i = 0; i < guardians.size(); i++) {
            var g = guardians.get(i);
            repository.createGuardian(
                    entryId,
                    g.firstname() != null ? g.firstname() : "",
                    g.lastname() != null ? g.lastname() : "",
                    g.email() != null ? g.email() : "",
                    g.phone() != null ? g.phone() : "",
                    i);
        }
    }

    /**
     * Gives every guardian on the entry an account at the station and puts them over the member.
     *
     * <p>On a list that writes to nobody the account is made without its setup mail. The account
     * stands either way, and the link that claims it is minted by hand from the member list, at a
     * moment somebody is there to pass it on.
     */
    private void createGuardianAccounts(WaitingListEntry entry, int memberId, WaitingList list) {
        int stationId = list.stationId();
        var setupMail = list.sendsMail() ? SetupMail.SEND_NOW : SetupMail.LATER;
        var guardians = repository.findGuardiansByEntry(entry.id());
        if (guardians.isEmpty()) return;

        var loginRole = stationMemberRepository.findPermissionByName(StationPermission.LOGIN);
        var guardianRole = stationMemberRepository.findPermissionByName(StationPermission.MEMBER_GUARDIAN);

        for (var guardian : guardians) {
            String address = guardian.email().trim();

            var known = address.isBlank()
                    ? Optional.<StationMember>empty()
                    : accountRepository
                            .findByEmail(address)
                            .flatMap(account ->
                                    stationMemberRepository.findByStationAndAccount(stationId, account.id()));
            if (known.isPresent()) {
                stationMemberRepository.addManager(known.get().id(), memberId);
                log.info(
                        "Guardian {} already at station {}, linked to member {}",
                        known.get().id(),
                        stationId,
                        memberId);
                continue;
            }

            AccountInviteService.Invited invited;
            try {
                invited = address.isBlank()
                        ? accountInviteService.createWithoutAddress(
                                stationId, guardian.firstname(), guardian.lastname())
                        : accountInviteService.resolveOrCreate(
                                stationId, address, guardian.firstname(), guardian.lastname(), setupMail);
            } catch (AccountInviteService.EmailInUseException e) {
                log.warn("Guardian of member {} was not taken on: {} is somebody else's", memberId, address);
                continue;
            }

            var member =
                    stationMemberRepository.create(stationId, invited.account().id());
            stationMemberRepository.setUserType(member.id(), StationUserType.GUARDIAN);
            loginRole.ifPresent(role -> stationMemberRepository.grantPermission(member.id(), role.id()));
            guardianRole.ifPresent(role -> stationMemberRepository.grantPermission(member.id(), role.id()));

            stationMemberRepository.addManager(member.id(), memberId);
            log.info("Guardian {} joined station {} and answers for member {}", member.id(), stationId, memberId);
        }
    }

    /**
     * Refuses a list whose groups are not the station's, or do not take the people the list puts in
     * them: somebody on trial joins the testing group, a member the join group. Refused where the list
     * is saved, so the manager hears of it rather than a family on the list later.
     */
    private void requireGroupsFit(int stationId, @Nullable Integer testingGroupId, @Nullable Integer joinGroupId) {
        groupMemberships.requireAdmits(
                stationId,
                testingGroupId,
                StationUserType.TRIAL,
                WaitingListRefusal.WAITING_LIST_TESTING_GROUP_NOT_HERE,
                WaitingListRefusal.WAITING_LIST_TESTING_GROUP_WRONG_USER_TYPE);
        groupMemberships.requireAdmits(
                stationId,
                joinGroupId,
                StationUserType.MEMBER,
                WaitingListRefusal.WAITING_LIST_JOIN_GROUP_NOT_HERE,
                WaitingListRefusal.WAITING_LIST_JOIN_GROUP_WRONG_USER_TYPE);
    }

    private Integer stationIdForList(int listId) {
        return repository.findById(listId).map(WaitingList::stationId).orElse(null);
    }

    private String resolveStationName(int stationId) {
        return stationRepository.findById(stationId).map(Station::name).orElse("");
    }

    private String languageOf(int stationId) {
        return StationFormat.languageOf(stationRepository.findById(stationId).orElse(null));
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "waiting-list-confirmation-check",
                Schedule.fixedRate(Duration.ofHours(1), Duration.ofHours(24)),
                this::checkAllExpiredConfirmations));
    }
}
