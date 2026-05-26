/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.FormDeleted;
import dev.chojo.ember.event.events.FormPublished;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormAnswer;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormResponse;
import dev.chojo.ember.feature.form.entity.QuestionEntry;
import dev.chojo.ember.feature.form.repository.FormRepository;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionRepository;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import dev.chojo.ember.feature.restriction.RestrictionType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service layer for form management, including form CRUD, questions, responses, answers, and access control.
 * Delegates persistence to {@link FormRepository} and coordinates with member, group, and tag services for access checks.
 */
@Singleton
public class FormService {
    private final FormRepository repository;
    private final StationMemberService memberService;
    private final MemberGroupService groupService;
    private final UserTagService tagService;
    private final RestrictionRepository restrictionRepository;
    private final DomainEventBus eventBus;

    @Inject
    public FormService(
            FormRepository repository,
            StationMemberService memberService,
            MemberGroupService groupService,
            UserTagService tagService,
            RestrictionRepository restrictionRepository,
            DomainEventBus eventBus) {
        this.repository = repository;
        this.memberService = memberService;
        this.groupService = groupService;
        this.tagService = tagService;
        this.restrictionRepository = restrictionRepository;
        this.eventBus = eventBus;
    }

    /**
     * Check if a specific member has access to a form based on its restrictions.
     * Uses the unified restriction system with RestrictionSet.matches().
     */
    public boolean canMemberAccess(int formId, int memberId) {
        var restrictions = findRestrictions(formId);
        if (!restrictions.hasRestrictions()) return true;

        var memberRoleIds =
                memberService.findRoles(memberId).stream().map(Role::id).toList();
        var memberGroupIds = groupService.findGroupsForMember(memberId).stream()
                .map(MemberGroup::id)
                .toList();
        var memberTagIds =
                tagService.findTagsForMember(memberId).stream().map(UserTag::id).toList();

        return restrictions.matches(memberRoleIds, memberGroupIds, memberTagIds, memberId);
    }

    /**
     * Retrieves the restriction set for a form.
     */
    public RestrictionSet findRestrictions(int formId) {
        var form = repository.findById(formId).orElse(null);
        RestrictionMode mode = form != null ? form.restrictionMode() : RestrictionMode.OR;
        return restrictionRepository.findRestrictionSet(
                RestrictionType.FORM.table(), RestrictionType.FORM.fkColumn(), formId, mode);
    }

    /**
     * Updates the restriction mode for a form.
     *
     * @param formId the form ID
     * @param mode   the restriction mode ("AND" or "OR")
     */
    public void updateRestrictionMode(int formId, RestrictionMode mode) {
        repository.updateRestrictionMode(formId, mode);
    }

    // -- Forms --

    /**
     * Retrieves all forms for a station.
     *
     * @param stationId the station ID
     * @return list of forms ordered by creation date descending
     */
    public List<Form> findByStation(int stationId) {
        return repository.findByStation(stationId);
    }

    /**
     * Retrieves forms for a station that the given member is allowed to see.
     *
     * @param stationId the station ID
     * @param memberId  the requesting member ID
     * @return the filtered list of forms
     */
    public List<Form> findByStationForMember(int stationId, int memberId) {
        return repository.findByStationForMember(stationId, memberId);
    }

    /**
     * Finds a form by its ID.
     *
     * @param id the form ID
     * @return the form, or empty if not found
     */
    public Optional<Form> findById(int id) {
        return repository.findById(id);
    }

    /**
     * Creates a new form in DRAFT status.
     *
     * @param stationId        the station this form belongs to
     * @param title            form title
     * @param description      form description
     * @param shuffleQuestions whether to randomize question order
     * @param allowEdit        whether respondents may edit their response
     * @param startAt          optional start time for accepting responses
     * @param endAt            optional end time for accepting responses
     * @param createdBy        member ID of the creator
     * @return the newly created form
     */
    public Form create(
            int stationId,
            String title,
            String description,
            boolean shuffleQuestions,
            boolean allowEdit,
            Instant startAt,
            Instant endAt,
            int createdBy) {
        return repository.create(stationId, title, description, shuffleQuestions, allowEdit, startAt, endAt, createdBy);
    }

    /**
     * Updates the editable fields of a form.
     *
     * @param id               the form ID
     * @param title            new title
     * @param description      new description
     * @param shuffleQuestions whether to randomize question order
     * @param allowEdit        whether respondents may edit their response
     * @param startAt          optional start time
     * @param endAt            optional end time
     * @return {@code true} if the form was updated
     */
    public boolean update(
            int id,
            String title,
            String description,
            boolean shuffleQuestions,
            boolean allowEdit,
            Instant startAt,
            Instant endAt) {
        return repository.update(id, title, description, shuffleQuestions, allowEdit, startAt, endAt);
    }

    /**
     * Deletes a form by ID.
     *
     * @param id the form ID
     * @return {@code true} if the form was deleted
     */
    public boolean delete(int id) {
        var form = repository.findById(id).orElse(null);
        boolean deleted = repository.delete(id);
        if (deleted && form != null) {
            eventBus.publish(new FormDeleted(form.stationId(), id));
        }
        return deleted;
    }

    /**
     * Publishes a form by transitioning its status to OPEN.
     *
     * @param id the form ID
     * @return {@code true} if the status was updated
     */
    public boolean publish(int id) {
        var form = repository.findById(id).orElse(null);
        boolean updated = repository.updateStatus(id, Form.FormStatus.OPEN);
        if (updated && form != null) {
            eventBus.publish(new FormPublished(form.stationId(), id, form.title()));
        }
        return updated;
    }

    /**
     * Closes a form by transitioning its status to CLOSED.
     *
     * @param id the form ID
     * @return {@code true} if the status was updated
     */
    public boolean close(int id) {
        return repository.updateStatus(id, Form.FormStatus.CLOSED);
    }

    /**
     * Checks whether a form is currently accepting responses based on its status and time window.
     *
     * @param form the form to check
     * @return {@code true} if the form is OPEN and the current time is within its start/end window
     */
    public boolean isAcceptingResponses(Form form) {
        if (form.status() != Form.FormStatus.OPEN) return false;
        var now = Instant.now();
        if (form.startAt() != null && now.isBefore(form.startAt())) return false;
        return form.endAt() == null || !now.isAfter(form.endAt());
    }

    // -- Questions --

    /**
     * Retrieves all questions for a form, ordered by position.
     *
     * @param formId the form ID
     * @return list of questions
     */
    public List<FormQuestion> findQuestions(int formId) {
        return repository.findQuestions(formId);
    }

    /**
     * Creates a new question for a form.
     *
     * @param formId       the form to add the question to
     * @param position     display order position
     * @param questionType the type of question
     * @param title        the question text
     * @param description  optional description
     * @param required     whether an answer is mandatory
     * @param shuffle      whether answer options should be randomized
     * @param config       type-specific configuration as JSON
     * @return the newly created question
     */
    // Individual CRUD — routes use replaceQuestions() instead, kept for programmatic use
    public FormQuestion createQuestion(
            int formId,
            int position,
            FormQuestion.QuestionType questionType,
            String title,
            String description,
            boolean required,
            boolean shuffle,
            String config) {
        return repository.createQuestion(formId, position, questionType, title, description, required, shuffle, config);
    }

    /**
     * Updates an existing question's fields.
     *
     * @param id          the question ID
     * @param title       new question text
     * @param description new description
     * @param required    whether an answer is mandatory
     * @param shuffle     whether answer options should be randomized
     * @param config      type-specific configuration as JSON
     * @param position    new display order position
     * @return {@code true} if the question was updated
     */
    // Individual CRUD — routes use replaceQuestions() instead, kept for programmatic use
    public boolean updateQuestion(
            int id, String title, String description, boolean required, boolean shuffle, String config, int position) {
        return repository.updateQuestion(id, title, description, required, shuffle, config, position);
    }

    /**
     * Deletes a question by ID.
     *
     * @param id the question ID
     * @return {@code true} if the question was deleted
     */
    // Individual CRUD — routes use replaceQuestions() instead, kept for programmatic use
    public boolean deleteQuestion(int id) {
        return repository.deleteQuestion(id);
    }

    /**
     * Replaces all questions for a form. Deletes existing questions and creates new ones with sequential positions.
     *
     * @param formId    the form ID
     * @param questions the new set of questions to create
     */
    public void replaceQuestions(int formId, List<QuestionEntry> questions) {
        repository.deleteQuestionsByForm(formId);
        for (int i = 0; i < questions.size(); i++) {
            var q = questions.get(i);
            repository.createQuestion(
                    formId, i, q.questionType(), q.title(), q.description(), q.required(), q.shuffle(), q.config());
        }
    }

    // -- Responses --

    /**
     * Retrieves all responses for a form.
     *
     * @param formId the form ID
     * @return list of responses ordered by submission time
     */
    public List<FormResponse> findResponses(int formId) {
        return repository.findResponses(formId);
    }

    /**
     * Finds a specific member's response to a form.
     *
     * @param formId   the form ID
     * @param memberId the member ID
     * @return the response, or empty if the member has not responded
     */
    public Optional<FormResponse> findResponse(int formId, int memberId) {
        return repository.findResponse(formId, memberId);
    }

    /**
     * Counts the total number of responses for a form.
     *
     * @param formId the form ID
     * @return the response count
     */
    public int countResponses(int formId) {
        return repository.countResponses(formId);
    }

    /**
     * Checks whether a member has already submitted a response to a form.
     *
     * @param formId   the form ID
     * @param memberId the member ID
     * @return {@code true} if the member has responded
     */
    public boolean hasResponded(int formId, int memberId) {
        return repository.hasResponded(formId, memberId);
    }

    /**
     * Submits or updates a response for a member, upserting all provided answers.
     *
     * @param formId      the form ID
     * @param memberId    the member the response is for
     * @param submittedBy the member who submitted the response (may differ for managed members)
     * @param answers     map of question ID to answer value (JSON string)
     * @return the created or updated response
     */
    public FormResponse submitResponse(int formId, int memberId, int submittedBy, Map<Integer, String> answers) {
        var response = repository.createResponse(formId, memberId, submittedBy);
        for (var entry : answers.entrySet()) {
            repository.upsertAnswer(response.id(), entry.getKey(), entry.getValue());
        }
        return response;
    }

    // -- Answers --

    /**
     * Retrieves all answers for a specific response.
     *
     * @param responseId the response ID
     * @return list of answers
     */
    public List<FormAnswer> findAnswers(int responseId) {
        return repository.findAnswers(responseId);
    }

    /**
     * Retrieves all answers submitted for a specific question across all responses. Useful for analytics.
     *
     * @param questionId the question ID
     * @return list of answers from all respondents
     */
    public List<FormAnswer> findAllAnswersForQuestion(int questionId) {
        return repository.findAllAnswersForQuestion(questionId);
    }

    // -- Restrictions --

    /**
     * Replaces all access restrictions for a form. Null lists are treated as empty.
     *
     * @param formId    the form ID
     * @param roleIds   role IDs to restrict access to, or {@code null} for none
     * @param groupIds  group IDs to restrict access to, or {@code null} for none
     * @param tagIds    tag IDs to restrict access to, or {@code null} for none
     * @param memberIds member IDs to restrict access to, or {@code null} for none
     */
    public void setRestrictions(
            int formId, List<Integer> roleIds, List<Integer> groupIds, List<Integer> tagIds, List<Integer> memberIds) {
        restrictionRepository.setRestrictions(
                RestrictionType.FORM.table(),
                RestrictionType.FORM.fkColumn(),
                formId,
                roleIds != null ? roleIds : List.of(),
                groupIds != null ? groupIds : List.of(),
                tagIds != null ? tagIds : List.of(),
                memberIds != null ? memberIds : List.of());
    }
}
