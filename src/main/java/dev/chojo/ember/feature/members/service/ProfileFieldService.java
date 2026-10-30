/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.AssignedProfileField;
import dev.chojo.ember.feature.members.entity.FieldDraft;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.FieldSet;
import dev.chojo.ember.feature.members.entity.FieldSetLabel;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.MemberChangeSummary;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.entity.PagedChanges;
import dev.chojo.ember.feature.members.entity.ProfileAuthor;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldAssignment;
import dev.chojo.ember.feature.members.entity.ProfileFieldChange;
import dev.chojo.ember.feature.members.entity.ProfileFieldChangeAcknowledgement;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldTarget;
import dev.chojo.ember.feature.members.entity.ProfileWriter;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldChangeRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.StationFieldSetRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A station's profile questions, its members' answers and the history of what changed.
 *
 * <p>The station's definitions and their audiences are kept here. Answers, whoever asked the question,
 * go through {@link ProfileFieldCore}, and so does the check a definition passes before it is written
 * down, which an association's questions pass the same way.
 */
@Singleton
public class ProfileFieldService {
    private static final Logger log = LoggerFactory.getLogger(ProfileFieldService.class);

    private final ProfileFieldRepository profileFieldRepository;
    private final ProfileFieldChangeRepository changeRepository;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final ProfileFieldCore core;
    private final GenderFields genders;
    private final StationFieldSetRepository sets;

    @Inject
    public ProfileFieldService(
            ProfileFieldRepository profileFieldRepository,
            ProfileFieldChangeRepository changeRepository,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            MemberGroupRepository memberGroupRepository,
            ProfileFieldCore core,
            GenderFields genders,
            StationFieldSetRepository sets) {
        this.profileFieldRepository = profileFieldRepository;
        this.changeRepository = changeRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.core = core;
        this.genders = genders;
        this.sets = sets;
    }

    /** The station's bundles of questions, by name. */
    public List<FieldSet> findSets(int stationId) {
        return sets.findByOwner(stationId);
    }

    /**
     * Writes a new, empty bundle down.
     *
     * @throws RefusalResponse {@link MemberRefusal#PROFILE_FIELD_SET_NEEDS_A_NAME} for a blank name
     */
    public FieldSet createSet(int stationId, @Nullable String name, boolean nameAsSection) {
        FieldSet set = sets.create(stationId, requireSetName(name), nameAsSection);
        log.info("Station {} bundled questions as set {} '{}'", stationId, set.id(), set.name());
        return set;
    }

    /** Renames a bundle, or changes whether its name stands above its questions. */
    public void updateSet(int stationId, int setId, @Nullable String name, boolean nameAsSection) {
        requireSet(stationId, setId);
        sets.update(setId, requireSetName(name), nameAsSection);
    }

    /** Dissolves a bundle. Its questions stay, on their own, where they stand. */
    public void dissolveSet(int stationId, int setId) {
        requireSet(stationId, setId);
        sets.delete(setId);
        log.info("Station {} dissolved set {}", stationId, setId);
    }

    /** Puts a bundle's questions in a new order, which every form follows. */
    public void orderSet(int stationId, int setId, List<Integer> fieldIds) {
        requireSet(stationId, setId);
        sets.applyOrder(setId, fieldIds);
        keepSetsTogether(stationId);
    }

    /**
     * Refuses a bundle that is not the station's, before anything else of a question is written.
     *
     * @param setId the bundle, or {@code null} for none, which is always allowed
     */
    public void requireSetAllowed(int stationId, @Nullable Integer setId) {
        if (setId != null) requireSet(stationId, setId);
    }

    /**
     * Puts a question into a bundle, at its end, or takes it out. A question joining a bundle is put to
     * everybody the bundle's first question is put to, so the bundle stays one thing on every form.
     *
     * @param setId the bundle, or {@code null} to stand on its own
     */
    public void joinSet(int fieldId, @Nullable Integer setId) {
        ProfileField field = profileFieldRepository.findById(fieldId).orElse(null);
        if (field == null || Objects.equals(field.fieldSetId(), setId)) return;
        requireSetAllowed(field.stationId(), setId);
        List<Integer> others = setId == null ? List.of() : sets.members(setId);
        sets.place(fieldId, setId);
        if (!others.isEmpty()) {
            for (ProfileFieldAssignment asked : profileFieldRepository.findAssignments(others.getFirst())) {
                askIfNotYet(fieldId, asked);
            }
        }
        keepSetsTogether(field.stationId());
        log.info("Profile field {} now in set {}", fieldId, setId);
    }

    private FieldSet requireSet(int stationId, int setId) {
        return sets.findById(setId)
                .filter(set -> set.ownerId() == stationId)
                .orElseThrow(MemberRefusal.PROFILE_FIELD_SET_NOT_HERE::raise);
    }

    private static String requireSetName(@Nullable String name) {
        if (name == null || name.isBlank()) throw MemberRefusal.PROFILE_FIELD_SET_NEEDS_A_NAME.raise();
        return name.strip();
    }

    /** The other questions of the bundle a question is in, or none. */
    private List<Integer> siblingsOf(int fieldId) {
        Integer setId = profileFieldRepository
                .findById(fieldId)
                .map(ProfileField::fieldSetId)
                .orElse(null);
        if (setId == null) return List.of();
        return sets.members(setId).stream().filter(id -> id != fieldId).toList();
    }

    /** Puts a question to the audience of an assignment, unless that audience is asked it already. */
    private void askIfNotYet(int fieldId, ProfileFieldAssignment like) {
        boolean asked = profileFieldRepository.findAssignments(fieldId).stream()
                .anyMatch(a -> a.targetKind() == like.targetKind()
                        && Objects.equals(a.role(), like.role())
                        && Objects.equals(a.groupId(), like.groupId()));
        if (asked) return;
        ProfileFieldScope role = like.role();
        Integer groupId = like.groupId();
        if (role != null) {
            profileFieldRepository.assignToRole(fieldId, role, like.position(), null, null, null);
        } else if (groupId != null) {
            profileFieldRepository.assignToGroup(fieldId, groupId, like.position(), null, null, null);
        }
    }

    /**
     * Puts every form of the station back into an order that keeps each bundle together. The forms of
     * kinds of member are kept in order; a group's form follows the order the questions were put to it.
     */
    private void keepSetsTogether(int stationId) {
        var placements = sets.placements(stationId);
        Map<ProfileFieldScope, List<ProfileFieldAssignment>> forms =
                profileFieldRepository.findAssignmentsByStation(stationId).stream()
                        .filter(assignment -> assignment.role() != null)
                        .collect(Collectors.groupingBy(ProfileFieldAssignment::role));
        forms.forEach((role, form) -> profileFieldRepository.applyOrder(
                stationId,
                role,
                FieldSets.keepTogether(
                        form.stream()
                                .sorted(Comparator.comparingInt(ProfileFieldAssignment::position))
                                .map(ProfileFieldAssignment::fieldId)
                                .toList(),
                        placements)));
    }

    public List<ProfileField> findByStation(int stationId) {
        return profileFieldRepository.findByStation(stationId);
    }

    /**
     * The fields a reader holding these roles may see.
     *
     * @param stationId the station whose fields these are
     * @param roles     the roles the reader may read
     * @return the definitions any of those roles is asked
     */
    public List<ProfileField> findReadableBy(int stationId, Collection<ProfileFieldScope> roles) {
        return profileFieldRepository.findReadableBy(stationId, roles);
    }

    public List<AssignedProfileField> findByStationAndScope(int stationId, ProfileFieldScope role) {
        return profileFieldRepository.findByStationAndScope(stationId, role);
    }

    /**
     * The questions one member's profile asks them.
     *
     * <p>Two things decide it. What kind of member somebody is picks one role, which is most of the form.
     * On top of that a station can ask something of one group alone: whoever drives asks for a licence
     * class, and nobody else is asked at all.
     *
     * @param memberId the member whose profile is being filled in
     * @return the fields of their kind, every owner's, followed by the ones their groups are asked
     */
    public List<MergedField> findApplicableFields(int memberId) {
        return stationMemberRepository.findById(memberId).map(core::fieldsFor).orElse(List.of());
    }

    /**
     * The fields one kind of member is put on a station's profile: its own, and its association's.
     *
     * <p>Unioned rather than returned as two lists, so the profile lays out as one form. Each entry carries
     * where it came from, because that decides two things the reader has to see: whether the station may
     * write the answer, and who to blame for the question.
     *
     * @param stationId the station
     * @param role      which kind of member the fields are put to
     * @return the station's own fields first, then the association's
     */
    public List<MergedField> findMergedFields(int stationId, ProfileFieldScope role) {
        return core.fieldsAt(stationId, role);
    }

    /**
     * One question as one audience meets it.
     *
     * @param required          whether this audience must answer, the definition's answer unless their
     *                          assignment says otherwise
     * @param position          where it sits on this audience's form
     * @param width             how much of a row it takes, null meaning the whole row
     * @param readonly          whether this audience may read the answer but not write it
     * @param role              the kind of member this was read for, null where a group is asked
     * @param origin            who asked
     * @param readonlyAtStation whether the people at the station may read the answer but not write it, which
     *                          only an association's field can be
     * @param shownIfFieldId    the yes/no field of the same origin this one depends on, or {@code null}. It only
     *                          takes effect where that field is asked on the same form: hidden and not required
     *                          until it is answered yes
     * @param fieldSet          the bundle of the same origin this one belongs to, or {@code null}. A form keeps
     *                          its questions together and shows its name above them where it says so
     */
    public record MergedField(
            int id,
            String name,
            FieldType fieldType,
            ProfileFieldConfig config,
            boolean required,
            int position,
            @Nullable String width,
            boolean readonly,
            @Nullable ProfileFieldScope role,
            FieldOrigin origin,
            boolean readonlyAtStation,
            @Nullable Integer shownIfFieldId,
            @Nullable FieldSetLabel fieldSet) {
        /**
         * What tells this question apart from every other one a member can be asked.
         *
         * @return who asked, and the id within their numbering
         */
        public FieldKey key() {
            return new FieldKey(origin, id);
        }
    }

    /**
     * One question, named by who asked it and its id within their numbering.
     *
     * @param origin who asked
     * @param id     the field
     */
    public record FieldKey(FieldOrigin origin, int id) {}

    public Optional<ProfileField> findById(int id) {
        return profileFieldRepository.findById(id);
    }

    /**
     * Writes a question down. Who is asked it is a separate act.
     *
     * @param stationId     the station asking
     * @param name          what it is called, which a spacer may leave empty to be numbered
     * @param fieldType     what kind of answer it takes
     * @param config        its settings, a gender field's pronouns kept as
     *                      {@link ProfileFieldConfig#withPronounsOfItsAnswers()} prunes them
     * @param required      whether an answer is expected
     * @param readonly      whether only the member management writes the answer
     * @param width         how much of a row it takes, null for the whole row
     * @param keepOnArchive whether its answers stay when a member leaves
     * @return the question
     * @throws RefusalResponse the checks of {@link ProfileFieldCore#checkedName} and
     *                         {@link GenderFields#requireAllowed}, or
     *                         {@link MemberRefusal#PROFILE_BIRTH_DATE_ALREADY_ASKED}
     */
    public ProfileField create(
            int stationId,
            @Nullable String name,
            FieldType fieldType,
            ProfileFieldConfig config,
            boolean required,
            boolean readonly,
            @Nullable String width,
            boolean keepOnArchive) {
        var stored = config.withPronounsOfItsAnswers();
        String chosen = core.checkedName(new Owner.Station(stationId), new FieldDraft(name, fieldType, stored, false));
        requireSingleBirthDate(stationId, fieldType, 0);
        genders.requireAllowed(new Owner.Station(stationId), new FieldDraft(chosen, fieldType, stored, false), null, 0);
        var field = profileFieldRepository.create(
                stationId, chosen, fieldType, stored, required, readonly, width, keepOnArchive);
        log.info(
                "Profile field created: id={}, station={}, name='{}', type={}",
                field.id(),
                stationId,
                chosen,
                fieldType);
        return field;
    }

    /**
     * The same, for a question whose answers go when a member leaves.
     *
     * @see #create(int, String, FieldType, ProfileFieldConfig, boolean, boolean, String, boolean)
     */
    public ProfileField create(
            int stationId,
            @Nullable String name,
            FieldType fieldType,
            ProfileFieldConfig config,
            boolean required,
            boolean readonly,
            @Nullable String width) {
        return create(stationId, name, fieldType, config, required, readonly, width, false);
    }

    public Optional<ProfileField> update(
            int id,
            String name,
            FieldType fieldType,
            ProfileFieldConfig config,
            boolean required,
            boolean readonly,
            @Nullable String width,
            boolean keepOnArchive) {
        var existing = profileFieldRepository.findById(id);
        if (existing.isEmpty()) {
            log.warn("Profile field update affected no rows: id={}", id);
            return Optional.empty();
        }
        int stationId = existing.get().stationId();
        FieldDependencies.requireNotStillUsed(id, fieldType, dependencyNodes(stationId));
        var stored = config.withPronounsOfItsAnswers();
        String chosen = core.checkedName(new Owner.Station(stationId), new FieldDraft(name, fieldType, stored, false));
        requireSingleBirthDate(stationId, fieldType, id);
        genders.requireAllowed(
                new Owner.Station(stationId),
                new FieldDraft(chosen, fieldType, stored, false),
                existing.get().fieldType(),
                id);
        if (profileFieldRepository.update(id, chosen, fieldType, stored, required, readonly, width, keepOnArchive)) {
            log.info("Profile field updated: id={}, name='{}', type={}", id, chosen, fieldType);
            return profileFieldRepository.findById(id);
        }
        log.warn("Profile field update affected no rows: id={}", id);
        return Optional.empty();
    }

    /**
     * Rejects a second birth date field in the same station.
     *
     * <p>A question is written once and assigned to everybody who is asked it, so a station needs exactly
     * one date of birth however many kinds of member it wants it from, and a second is a duplicate rather
     * than a different question.
     *
     * @param stationId  the station the field belongs to
     * @param fieldType  the type the field is about to carry
     * @param excludedId the field being updated, so it does not clash with itself; 0 when creating
     * @throws RefusalResponse if the station already has a date of birth
     */
    private void requireSingleBirthDate(int stationId, FieldType fieldType, int excludedId) {
        if (fieldType != FieldType.BIRTH_DATE) return;
        for (ProfileField other : profileFieldRepository.findAllByStationAndType(stationId, FieldType.BIRTH_DATE)) {
            if (other.id() == excludedId) continue;
            throw MemberRefusal.PROFILE_BIRTH_DATE_ALREADY_ASKED.raise(other.name());
        }
    }

    /**
     * Puts one audience's form in the given order, in one write.
     *
     * <p>The order belongs to the audience rather than to the field, so reordering one form leaves every
     * other alone.
     *
     * @param stationId the station whose fields these are
     * @param role      the audience whose form is being ordered
     * @param fieldIds  the fields in the order they should stand
     */
    public void reorder(int stationId, ProfileFieldScope role, List<Integer> fieldIds) {
        int moved = profileFieldRepository.applyOrder(
                stationId, role, FieldSets.keepTogether(fieldIds, sets.placements(stationId)));
        log.info("Profile fields reordered: station={}, role={}, fields={}", stationId, role, moved);
    }

    /** Every assignment of one station's fields, for the screen that configures them. */
    public List<ProfileFieldAssignment> findAssignmentsByStation(int stationId) {
        return profileFieldRepository.findAssignmentsByStation(stationId);
    }

    /** The assignments of one field. */
    public List<ProfileFieldAssignment> findAssignments(int fieldId) {
        return profileFieldRepository.findAssignments(fieldId);
    }

    /**
     * Puts this question to a kind of member, or changes how it is put to them.
     *
     * @param fieldId          the question
     * @param role             who is to be asked
     * @param position         where it sits on their form
     * @param widthOverride    how much of a row it takes here, null to follow the definition
     * @param readonlyOverride whether only the member management writes it here, null to follow the definition
     * @param requiredOverride whether they must answer, null to follow the definition
     */
    public void assignToRole(
            int fieldId,
            ProfileFieldScope role,
            int position,
            @Nullable String widthOverride,
            @Nullable Boolean readonlyOverride,
            @Nullable Boolean requiredOverride) {
        profileFieldRepository.assignToRole(fieldId, role, position, widthOverride, readonlyOverride, requiredOverride);
        List<Integer> siblings = siblingsOf(fieldId);
        if (!siblings.isEmpty()) {
            for (Integer sibling : siblings) {
                askIfNotYet(sibling, roleAssignment(fieldId, role, position));
            }
            profileFieldRepository.findById(fieldId).ifPresent(field -> keepSetsTogether(field.stationId()));
        }
        log.info("Profile field {} is asked of {}", fieldId, role);
    }

    private static ProfileFieldAssignment roleAssignment(int fieldId, ProfileFieldScope role, int position) {
        return new ProfileFieldAssignment(0, fieldId, ProfileFieldTarget.ROLE, role, null, position, null, null, null);
    }

    /**
     * Puts this question to one of the station's groups, or changes how it is put to them.
     *
     * @param fieldId          the question
     * @param groupId          the group to be asked
     * @param position         where it sits on their form
     * @param widthOverride    how much of a row it takes here, null to follow the definition
     * @param readonlyOverride whether only the member management writes it here, null to follow the definition
     * @param requiredOverride whether they must answer, null to follow the definition
     * @throws RefusalResponse {@link MemberRefusal#PROFILE_FIELD_GROUP_NOT_HERE} for a group of another
     *                         station, or none at all
     */
    public void assignToGroup(
            int fieldId,
            int groupId,
            int position,
            @Nullable String widthOverride,
            @Nullable Boolean readonlyOverride,
            @Nullable Boolean requiredOverride) {
        requireGroupOfTheFieldsStation(fieldId, groupId);
        profileFieldRepository.assignToGroup(
                fieldId, groupId, position, widthOverride, readonlyOverride, requiredOverride);
        for (Integer sibling : siblingsOf(fieldId)) {
            askIfNotYet(
                    sibling,
                    new ProfileFieldAssignment(
                            0, fieldId, ProfileFieldTarget.GROUP, null, groupId, position, null, null, null));
        }
        log.info("Profile field {} is asked of group {}", fieldId, groupId);
    }

    private void requireGroupOfTheFieldsStation(int fieldId, int groupId) {
        var stationId = profileFieldRepository.findById(fieldId).map(ProfileField::stationId);
        boolean own = stationId.isPresent()
                && memberGroupRepository
                        .findById(groupId)
                        .filter(group -> group.stationId() == stationId.get())
                        .isPresent();
        if (!own) throw MemberRefusal.PROFILE_FIELD_GROUP_NOT_HERE.raise();
    }

    /**
     * Stops asking a kind of member this question, and the rest of its bundle with it. The definitions and
     * their answers stay.
     */
    public boolean unassignRole(int fieldId, ProfileFieldScope role) {
        for (Integer sibling : siblingsOf(fieldId)) profileFieldRepository.unassignRole(sibling, role);
        boolean removed = profileFieldRepository.unassignRole(fieldId, role);
        if (removed) log.info("Profile field {} is no longer asked of {}", fieldId, role);
        return removed;
    }

    /**
     * Stops asking a group this question, and the rest of its bundle with it. The definitions and their
     * answers stay.
     */
    public boolean unassignGroup(int fieldId, int groupId) {
        for (Integer sibling : siblingsOf(fieldId)) profileFieldRepository.unassignGroup(sibling, groupId);
        boolean removed = profileFieldRepository.unassignGroup(fieldId, groupId);
        if (removed) log.info("Profile field {} is no longer asked of group {}", fieldId, groupId);
        return removed;
    }

    /**
     * Makes a question depend on a yes/no question of the same station, or no longer depend on any.
     *
     * @param id         the question
     * @param controller the yes/no question, or {@code null} for none
     * @throws RefusalResponse the refusals of {@link FieldDependencies#requireAllowed}
     */
    public void dependOn(int id, @Nullable Integer controller) {
        var field = profileFieldRepository.findById(id).orElse(null);
        if (field == null || Objects.equals(field.shownIfFieldId(), controller)) return;
        requireDependencyAllowed(field.stationId(), id, controller);
        profileFieldRepository.setShownIf(id, controller);
        log.info("Profile field {} now depends on {}", id, controller);
    }

    /**
     * Refuses a dependency before anything else of the question is written, so a refused one leaves the
     * question as it was.
     *
     * @param stationId  the station whose questions these are
     * @param id         the question, or 0 for one about to be created
     * @param controller the yes/no question, or {@code null} for none
     * @throws RefusalResponse the refusals of {@link FieldDependencies#requireAllowed}
     */
    public void requireDependencyAllowed(int stationId, int id, @Nullable Integer controller) {
        FieldDependencies.requireAllowed(id, controller, dependencyNodes(stationId));
    }

    private List<FieldDependencies.Node> dependencyNodes(int stationId) {
        return profileFieldRepository.findByStation(stationId).stream()
                .map(field ->
                        new FieldDependencies.Node(field.id(), field.name(), field.fieldType(), field.shownIfFieldId()))
                .toList();
    }

    /**
     * Deletes a question and its answers.
     *
     * @throws RefusalResponse {@link MemberRefusal#PROFILE_DEPENDENCY_STILL_USED} while others depend on it
     */
    public boolean delete(int id) {
        profileFieldRepository
                .findById(id)
                .ifPresent(
                        field -> FieldDependencies.requireNotStillUsed(id, null, dependencyNodes(field.stationId())));
        boolean deleted = profileFieldRepository.delete(id);
        if (deleted) {
            log.info("Profile field deleted: id={}", id);
        } else {
            log.warn("Profile field delete affected no rows: id={}", id);
        }
        return deleted;
    }

    /**
     * Whether this member has answered everything their profile asks of them.
     *
     * <p>What is counted as missing must be something the member was actually shown on
     * {@link #findApplicableFields(int)}, and something they were shown and left empty must be counted.
     * The task list, the badge beside it, the reminder on the dashboard and the member list all ask the one
     * rule the repository holds, the list for every row at once.
     *
     * @param memberId the member whose profile is being judged
     * @return whether nothing required of them is left blank
     */
    public boolean isProfileComplete(int memberId) {
        return profileFieldRepository.isProfileComplete(memberId);
    }

    public List<MergedValue> findValues(int memberId) {
        return core.answersOf(memberId);
    }

    /**
     * An answer, and which table its question lives in.
     *
     * <p>The two id spaces are separate, so a bare field id says nothing on its own: the profile screen
     * carries the origin back with every answer it saves, and this is the shape it reads them in.
     *
     * @param fieldId the field the answer belongs to, in its own table
     * @param value   the answer
     * @param origin  who asked
     */
    public record MergedValue(int fieldId, @Nullable String value, FieldOrigin origin) {}

    /**
     * Saves answers written by the member, or by somebody at the station who is not its member management.
     *
     * @param memberId  whose answers these are
     * @param entries   the answers, each naming which table its question lives in
     * @param changedBy the member row recorded as the author
     * @return every answer this member now has
     */
    public List<MergedValue> setValues(int memberId, List<FieldValueEntry> entries, int changedBy) {
        return setValues(memberId, entries, changedBy, ProfileWriter.station(false));
    }

    /**
     * Saves answers through the locks the writer has to pass, recorded against a member row.
     *
     * <p>A member row of another station than the member's, or none at all, is recorded by the account it
     * belongs to, which is what an association manager acting from their home station leaves.
     *
     * @param memberId  whose answers these are
     * @param entries   the answers, each naming which table its question lives in
     * @param changedBy the member row recorded as the author
     * @param writer    who writes them, which decides the locks they pass
     * @return every answer this member now has
     * @see ProfileFieldCore#write
     */
    public List<MergedValue> setValues(
            int memberId, List<FieldValueEntry> entries, int changedBy, ProfileWriter writer) {
        ProfileAuthor author = stationMemberRepository
                .findById(changedBy)
                .map(ProfileAuthor::member)
                .orElse(ProfileAuthor.unknown());
        return core.write(memberId, entries, author, writer);
    }

    /**
     * Saves answers through the locks the writer has to pass.
     *
     * @param memberId whose answers these are
     * @param entries  the answers, each naming which table its question lives in
     * @param author   who is recorded as having made the change
     * @param writer   who writes them, which decides the locks they pass
     * @return every answer this member now has
     * @see ProfileFieldCore#write
     */
    public List<MergedValue> setValues(
            int memberId, List<FieldValueEntry> entries, ProfileAuthor author, ProfileWriter writer) {
        return core.write(memberId, entries, author, writer);
    }

    public boolean deleteValue(int memberId, int fieldId) {
        boolean deleted = profileFieldRepository.deleteValue(memberId, fieldId);
        if (deleted) {
            log.info("Profile field value deleted: member={}, field={}", memberId, fieldId);
        } else {
            log.warn("Profile field value delete affected no rows: member={}, field={}", memberId, fieldId);
        }
        return deleted;
    }

    public List<MemberChangeSummary> findUnacknowledgedSummary(int stationId, int acknowledgedBy) {
        return changeRepository.findUnacknowledgedSummary(stationId, acknowledgedBy);
    }

    public List<ProfileFieldChange> findChanges(int memberId) {
        var changes = changeRepository.findByMember(memberId);
        if (changes.isEmpty()) return changes;

        var allAcks = changeRepository.findAcknowledgementsForMember(memberId);
        Map<Integer, List<ProfileFieldChangeAcknowledgement>> acksByChange =
                allAcks.stream().collect(Collectors.groupingBy(ProfileFieldChangeAcknowledgement::changeId));

        return changes.stream()
                .map(c -> withDetails(c, acksByChange.getOrDefault(c.id(), List.of()), null))
                .toList();
    }

    private static ProfileFieldChange withDetails(
            ProfileFieldChange c,
            List<ProfileFieldChangeAcknowledgement> acknowledgements,
            @Nullable String memberName) {
        return new ProfileFieldChange(
                c.id(),
                c.fieldId(),
                c.clusterFieldId(),
                c.memberId(),
                c.oldValue(),
                c.newValue(),
                c.changedBy(),
                c.changedAt(),
                c.requiresAcknowledgement(),
                c.changedByName(),
                c.fieldName(),
                c.fieldType(),
                acknowledgements,
                memberName);
    }

    /**
     * The changes of the given members, for a caller who may not see the whole station.
     *
     * @param memberIds the members the caller is allowed to see
     * @param limit     page size
     * @param offset    page offset
     * @return the page of changes, enriched the same way the station-wide list is
     */
    public PagedChanges findChangesByMembers(List<Integer> memberIds, int limit, int offset) {
        return enrich(
                changeRepository.findByMembers(memberIds, limit, offset), changeRepository.countByMembers(memberIds));
    }

    /**
     * The member a change was recorded for.
     *
     * @param changeId the change identifier
     * @return the member, empty if there is no such change
     */
    public Optional<Integer> findMemberOfChange(int changeId) {
        return changeRepository.findMemberOfChange(changeId);
    }

    public PagedChanges findChangesByStation(int stationId, int limit, int offset) {
        return enrich(
                changeRepository.findByStation(stationId, limit, offset), changeRepository.countByStation(stationId));
    }

    /**
     * Fills a page of changes with their acknowledgements and the names of the members they belong to.
     */
    private PagedChanges enrich(List<ProfileFieldChange> changes, int total) {
        if (changes.isEmpty()) return new PagedChanges(changes, total);

        var allAcks = new ArrayList<ProfileFieldChangeAcknowledgement>();
        for (var change : changes) {
            allAcks.addAll(changeRepository.findAcknowledgements(change.id()));
        }
        Map<Integer, List<ProfileFieldChangeAcknowledgement>> acksByChange =
                allAcks.stream().collect(Collectors.groupingBy(ProfileFieldChangeAcknowledgement::changeId));

        var enriched = changes.stream()
                .map(c -> withDetails(c, acksByChange.getOrDefault(c.id(), List.of()), memberNameOf(c.memberId())))
                .toList();
        return new PagedChanges(enriched, total);
    }

    private String memberNameOf(int memberId) {
        return stationMemberRepository
                .findById(memberId)
                .flatMap(m -> Optional.ofNullable(m.accountId()))
                .flatMap(accountRepository::findById)
                .map(a -> NameParts.of(a).called())
                .orElse("");
    }

    public ProfileFieldChangeAcknowledgement acknowledge(int changeId, int acknowledgedBy, String comment) {
        var ack = changeRepository.acknowledge(changeId, acknowledgedBy, comment);
        log.info("Profile field change acknowledged: change={}, by={}", changeId, acknowledgedBy);
        return ack;
    }

    public List<ProfileFieldChangeAcknowledgement> acknowledgeAll(int memberId, int acknowledgedBy, String comment) {
        var unacknowledgedIds = changeRepository.findUnacknowledgedChangeIds(memberId, acknowledgedBy);
        var result = new ArrayList<ProfileFieldChangeAcknowledgement>();
        for (int changeId : unacknowledgedIds) {
            result.add(changeRepository.acknowledge(changeId, acknowledgedBy, comment));
        }
        log.info(
                "Profile field changes acknowledged in bulk: member={}, by={}, count={}",
                memberId,
                acknowledgedBy,
                result.size());
        return result;
    }
}
