/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.members.entity.ExpirySettings;
import dev.chojo.ember.feature.members.entity.FieldDraft;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.OwnedProfileField;
import dev.chojo.ember.feature.members.entity.ProfileAuthor;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
import dev.chojo.ember.feature.members.entity.ProfileWriter;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.ProfileFieldChangeRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.ProfileFieldService.FieldKey;
import dev.chojo.ember.feature.members.service.ProfileFieldService.MergedField;
import dev.chojo.ember.feature.members.service.ProfileFieldService.MergedValue;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.QuestionCheck;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The one answer pipeline for profile questions, whoever asked them, and the checks every owner's
 * definitions pass.
 *
 * <p>A station and its association each keep their own questions and answers in their own tables, and
 * used to keep their own copy of the rules beside them: which answers a writer may give, how a change is
 * recorded, what leaving the station clears. The copies drifted, and every bug found sat where one was
 * fixed and the other was not. The rules live here once; each owner is a {@link ProfileFieldOwner} that
 * answers questions about its own tables, codes and audience, and the core decides.
 *
 * <p>What a save does, for every answer it carries: the question has to be one the member's station is
 * asked by that owner, or the save is refused; an answer to a question the member is not asked, or one
 * locked against the writer, is left out, because the screens send every answer back and refusing the
 * whole save would lose the ones beside it; an unchanged answer is skipped; a changed one is measured,
 * kept, and recorded in the one change history, merged with the same author's change from the last five
 * minutes. The whole save is one transaction, so a refused answer leaves nothing of the save behind, and
 * the owners are told once it has landed.
 */
@Singleton
public class ProfileFieldCore {
    private static final Logger log = LoggerFactory.getLogger(ProfileFieldCore.class);
    private static final Duration MERGE_WINDOW = Duration.ofMinutes(5);
    /** What an unnamed spacer is filed under, numbered until the name is free. */
    private static final String SPACER_NAME = "Abstand %d";

    private final Map<FieldOrigin, ProfileFieldOwner> owners;
    private final ProfileFieldChangeRepository changeRepository;
    private final StationMemberRepository memberRepository;
    private final MemberPermissionResolver permissionResolver;

    @Inject
    public ProfileFieldCore(
            Map<FieldOrigin, ProfileFieldOwner> owners,
            ProfileFieldChangeRepository changeRepository,
            StationMemberRepository memberRepository,
            MemberPermissionResolver permissionResolver) {
        this.owners = new EnumMap<>(FieldOrigin.class);
        this.owners.putAll(owners);
        this.changeRepository = changeRepository;
        this.memberRepository = memberRepository;
        this.permissionResolver = permissionResolver;
    }

    /**
     * The owner of one kind of question, for a reader that walks every owner's questions on its own, such
     * as the expiry reminders.
     *
     * @param origin who asked
     * @return the owner
     */
    public ProfileFieldOwner owner(FieldOrigin origin) {
        var owner = owners.get(origin);
        if (owner == null) throw new IllegalStateException("No owner of profile questions bound for " + origin);
        return owner;
    }

    private List<ProfileFieldOwner> ordered() {
        return List.copyOf(owners.values());
    }

    /**
     * The one role a kind of member is asked as.
     *
     * <p>Every kind of member answers as itself; a station that wants a manager asked the team's questions
     * assigns them to both.
     */
    static ProfileFieldScope roleOf(@Nullable StationUserType userType) {
        if (userType == null) return ProfileFieldScope.MEMBER;
        return switch (userType) {
            case TRIAL -> ProfileFieldScope.TRIAL;
            case MEMBER -> ProfileFieldScope.MEMBER;
            case GUARDIAN -> ProfileFieldScope.GUARDIAN;
            case TEAM -> ProfileFieldScope.TEAM;
            case MANAGER -> ProfileFieldScope.MANAGER;
        };
    }

    /**
     * The questions one kind of member is put at a station, every owner's, the station's first.
     *
     * @param stationId the station
     * @param role      the kind of member
     * @return the questions, as one form
     */
    public List<MergedField> fieldsAt(int stationId, ProfileFieldScope role) {
        List<MergedField> fields = new ArrayList<>();
        for (ProfileFieldOwner owner : ordered()) fields.addAll(owner.putTo(stationId, role));
        return fields;
    }

    /**
     * The questions one member's profile asks them: those of their kind of member, then those of their
     * groups, each once. Whether two are the same question is told by who asked as well as the id, since
     * the owners number their questions apart.
     *
     * @param member the member
     * @return the questions
     */
    public List<MergedField> fieldsFor(StationMember member) {
        List<MergedField> fields = new ArrayList<>(fieldsAt(member.stationId(), roleOf(member.userType())));
        var seen = fields.stream().map(MergedField::key).collect(Collectors.toSet());
        for (ProfileFieldOwner owner : ordered()) {
            for (MergedField field : owner.putToGroupsOf(member)) {
                if (seen.add(field.key())) fields.add(field);
            }
        }
        return fields;
    }

    /**
     * Every answer one member has, each naming who asked the question.
     *
     * @param memberId the member
     * @return the answers
     */
    public List<MergedValue> answersOf(int memberId) {
        List<MergedValue> values = new ArrayList<>();
        for (ProfileFieldOwner owner : ordered()) {
            for (ProfileFieldValue value : owner.answersOf(memberId)) {
                values.add(new MergedValue(value.fieldId(), value.value(), owner.origin()));
            }
        }
        return values;
    }

    /**
     * Saves a member's answers through the locks the writer has to pass.
     *
     * @param memberId whose answers these are
     * @param entries  the answers, each naming who asked its question
     * @param author   who is recorded as having made the change
     * @param writer   who writes them, which decides the locks they pass
     * @return every answer the member now has
     * @throws RefusalResponse the owner's refusal for a question it does not ask at the member's station,
     *                         or the answer refusals of {@link ProfileAnswers}; nothing of the save is kept
     */
    public List<MergedValue> write(
            int memberId, List<FieldValueEntry> entries, ProfileAuthor author, ProfileWriter writer) {
        var member = memberRepository.findById(memberId).orElse(null);
        if (member == null) return List.of();
        ProfileAuthor recorded = authorAt(member, author);
        List<String> changed = Transactions.call(() -> writeAll(member, entries, recorded, writer));
        if (!changed.isEmpty()) {
            for (ProfileFieldOwner owner : ordered()) owner.changed(member, recorded.memberId(), writer, changed);
            log.info("Profile answers changed: member={}, author={}, fields={}", memberId, recorded, changed);
        }
        return answersOf(memberId);
    }

    private List<String> writeAll(
            StationMember member, List<FieldValueEntry> entries, ProfileAuthor author, ProfileWriter writer) {
        Set<FieldKey> writable = fieldsFor(member).stream()
                .filter(field -> ProfileAnswers.writable(field, writer))
                .map(MergedField::key)
                .collect(Collectors.toSet());
        Map<FieldOrigin, Map<Integer, String>> before = new EnumMap<>(FieldOrigin.class);
        boolean seenByTheStation = acknowledgesTheirOwn(author, writer);

        List<String> changed = new ArrayList<>();
        for (FieldValueEntry entry : entries) {
            String said = ProfileAnswers.said(entry.value());
            ProfileFieldOwner owner = owner(entry.origin());
            OwnedProfileField field =
                    owner.askedAt(member.stationId(), entry.fieldId()).orElseThrow(owner.notAskedHere()::raise);
            if (!writable.contains(new FieldKey(field.origin(), field.id()))) continue;

            String old = before.computeIfAbsent(field.origin(), origin -> storedAnswers(owner, member.id()))
                    .getOrDefault(field.id(), ProfileAnswers.NOTHING);
            if (ProfileAnswers.unchanged(old, said)) continue;

            JsonNode kept = ProfileAnswers.kept(field.type(), field.question(), said);
            owner.keep(member.id(), field.id(), kept);
            record(field, member.id(), old, ProfileAnswers.recorded(kept), author, !seenByTheStation);
            changed.add(field.name());
        }
        return changed;
    }

    private static Map<Integer, String> storedAnswers(ProfileFieldOwner owner, int memberId) {
        Map<Integer, String> stored = new HashMap<>();
        for (ProfileFieldValue value : owner.answersOf(memberId)) {
            stored.put(value.fieldId(), Objects.requireNonNullElse(value.value(), ProfileAnswers.NOTHING));
        }
        return stored;
    }

    /**
     * Who the change is recorded against at the member's station: their membership there where they have
     * one, their account otherwise. An association manager acting from another station, or from none, is
     * recorded by account.
     */
    private ProfileAuthor authorAt(StationMember member, ProfileAuthor author) {
        Integer authorMember = author.memberId();
        Integer accountId = author.accountId();
        if (authorMember != null) {
            var row = memberRepository.findById(authorMember).orElse(null);
            if (row == null)
                return accountId == null ? ProfileAuthor.unknown() : authorAt(member, ProfileAuthor.account(accountId));
            if (row.stationId() == member.stationId()) return ProfileAuthor.member(row);
            accountId = row.accountId();
        }
        if (accountId == null) return ProfileAuthor.unknown();
        return memberRepository
                .findByStationAndAccount(member.stationId(), accountId)
                .map(ProfileAuthor::member)
                .orElse(ProfileAuthor.account(accountId));
    }

    /**
     * Whether the change has already been seen by somebody the station would put it in front of.
     *
     * <p>Acknowledging exists so that what a member alters about themselves is seen by somebody at the
     * station. Where the station's own confirmer made the change, it has been seen. What the association
     * writes has not been seen at the station, whoever wrote it.
     */
    private boolean acknowledgesTheirOwn(ProfileAuthor author, ProfileWriter writer) {
        Integer authorMember = author.memberId();
        if (writer.owningAssociation() || authorMember == null) return false;
        return permissionResolver.resolve(authorMember).contains(StationPermission.MEMBER_CHANGES);
    }

    /**
     * Records one change, unless it says nothing new: a calculated value, or nothing where there was
     * nothing. The same author changing the same answer again within the merge window updates their record
     * rather than adding one.
     */
    private void record(
            OwnedProfileField field,
            int memberId,
            String oldValue,
            String newValue,
            ProfileAuthor author,
            boolean unseen) {
        if (field.type().isCalculated()) return;
        if (ProfileAnswers.saysNothing(oldValue) && ProfileAnswers.saysNothing(newValue)) return;
        Instant cutoff = Instant.now().minus(MERGE_WINDOW);
        changeRepository
                .findRecentChange(field.origin(), field.id(), memberId, author, cutoff)
                .ifPresentOrElse(
                        recent -> changeRepository.updateChangeNewValue(recent, newValue),
                        () -> changeRepository.create(
                                field.origin(),
                                field.id(),
                                memberId,
                                oldValue,
                                newValue,
                                author,
                                unseen && field.notifyOnChange()));
    }

    /**
     * Removes a leaving member's answers to every owner's questions not marked to be kept.
     *
     * @param memberId the member who leaves
     */
    public void clearOnArchive(int memberId) {
        for (ProfileFieldOwner owner : ordered()) owner.clearOnArchive(memberId);
    }

    /**
     * Checks a question an owner is about to write down, and says what to file it under.
     *
     * <p>The same checks for every owner, each refusing in its own public codes: a name unless it is a
     * spacer, a type the owner offers, expiry settings that neither count backwards nor repeat without a
     * gap, and a starting value the question would accept as an answer. A spacer is a gap and nobody wants
     * to name one, so an unnamed one is numbered: the first free {@code Abstand N} of that owner.
     *
     * @param owner whose question it is
     * @param draft the question
     * @return the name to file it under, trimmed
     * @throws RefusalResponse the owner's refusal for whichever check failed
     */
    public String checkedName(Owner owner, FieldDraft draft) {
        ProfileFieldOwner fields = ownerOf(owner);
        var refusals = fields.definitionRefusals();
        String given = draft.name();
        String name = given == null ? "" : given.trim();
        if (name.isEmpty() && draft.type() != FieldType.SPACER)
            throw refusals.nameMissing().raise();
        if (!fields.offeredTypes().contains(draft.type()))
            throw refusals.typeNotOffered().raise();
        if (ExpirySettings.outOfRange(draft.config()))
            throw refusals.expiryOutOfRange().raise();
        String chosen = name.isEmpty() ? freeSpacerName(fields.namesTaken(idOf(owner))) : name;
        draft.config()
                .settings(false)
                .asQuestion(chosen, draft.type())
                .flatMap(QuestionCheck::defaultValue)
                .ifPresent(problem -> {
                    throw refusals.defaultNotAccepted().raise(problem.question());
                });
        return chosen;
    }

    private static String freeSpacerName(Set<String> taken) {
        int number = 1;
        while (taken.contains(SPACER_NAME.formatted(number))) number++;
        return SPACER_NAME.formatted(number);
    }

    private ProfileFieldOwner ownerOf(Owner owner) {
        return switch (owner) {
            case Owner.Station ignored -> owner(FieldOrigin.STATION);
            case Owner.Association ignored -> owner(FieldOrigin.CLUSTER);
            case Owner.Instance ignored -> throw new IllegalArgumentException("The instance asks no profile questions");
        };
    }

    private static int idOf(Owner owner) {
        return switch (owner) {
            case Owner.Station station -> station.stationId();
            case Owner.Association association -> association.clusterId();
            case Owner.Instance ignored -> throw new IllegalArgumentException("The instance asks no profile questions");
        };
    }
}
