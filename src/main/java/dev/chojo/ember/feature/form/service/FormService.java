/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormAnswer;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormResponse;
import dev.chojo.ember.feature.form.repository.FormRepository;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
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

    @Inject
    public FormService(
            FormRepository repository,
            StationMemberService memberService,
            MemberGroupService groupService,
            UserTagService tagService) {
        this.repository = repository;
        this.memberService = memberService;
        this.groupService = groupService;
        this.tagService = tagService;
    }

    /**
     * Check if a specific member has access to a form based on its restrictions.
     * If the form has no restrictions, everyone has access.
     * Otherwise, the member must match at least one role, group, or tag restriction.
     */
    public boolean canMemberAccess(int formId, int memberId) {
        var roleRestrictions = repository.findRoleRestrictions(formId);
        var groupRestrictions = repository.findGroupRestrictions(formId);
        var tagRestrictions = repository.findTagRestrictions(formId);

        // No restrictions = open to all
        if (roleRestrictions.isEmpty() && groupRestrictions.isEmpty() && tagRestrictions.isEmpty()) {
            return true;
        }

        // Check role match
        if (!roleRestrictions.isEmpty()) {
            var memberRoleIds =
                    memberService.findRoles(memberId).stream().map(Role::id).toList();
            if (memberRoleIds.stream().anyMatch(roleRestrictions::contains)) return true;
        }

        // Check group match
        if (!groupRestrictions.isEmpty()) {
            var memberGroupIds = groupService.findGroupsForMember(memberId).stream()
                    .map(MemberGroup::id)
                    .toList();
            if (memberGroupIds.stream().anyMatch(groupRestrictions::contains)) return true;
        }

        // Check tag match
        if (!tagRestrictions.isEmpty()) {
            var memberTagIds = tagService.findTagsForMember(memberId).stream()
                    .map(UserTag::id)
                    .toList();
            return memberTagIds.stream().anyMatch(tagRestrictions::contains);
        }

        return false;
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
        return repository.delete(id);
    }

    /**
     * Publishes a form by transitioning its status to OPEN.
     *
     * @param id the form ID
     * @return {@code true} if the status was updated
     */
    public boolean publish(int id) {
        return repository.updateStatus(id, Form.FormStatus.OPEN);
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
     * Retrieves the role IDs that restrict access to a form.
     *
     * @param formId the form ID
     * @return list of role IDs
     */
    public List<Integer> findRoleRestrictions(int formId) {
        return repository.findRoleRestrictions(formId);
    }

    /**
     * Retrieves the group IDs that restrict access to a form.
     *
     * @param formId the form ID
     * @return list of group IDs
     */
    public List<Integer> findGroupRestrictions(int formId) {
        return repository.findGroupRestrictions(formId);
    }

    /**
     * Retrieves the tag IDs that restrict access to a form.
     *
     * @param formId the form ID
     * @return list of tag IDs
     */
    public List<Integer> findTagRestrictions(int formId) {
        return repository.findTagRestrictions(formId);
    }

    /**
     * Replaces all access restrictions (roles, groups, tags) for a form. Null lists are treated as empty.
     *
     * @param formId   the form ID
     * @param roleIds  role IDs to restrict access to, or {@code null} for none
     * @param groupIds group IDs to restrict access to, or {@code null} for none
     * @param tagIds   tag IDs to restrict access to, or {@code null} for none
     */
    public void setRestrictions(int formId, List<Integer> roleIds, List<Integer> groupIds, List<Integer> tagIds) {
        repository.setRoleRestrictions(formId, roleIds != null ? roleIds : List.of());
        repository.setGroupRestrictions(formId, groupIds != null ? groupIds : List.of());
        repository.setTagRestrictions(formId, tagIds != null ? tagIds : List.of());
    }

    /**
     * Data transfer object for creating questions during a bulk replace operation.
     *
     * @param questionType the type of question
     * @param title        the question text
     * @param description  optional description
     * @param required     whether an answer is mandatory
     * @param shuffle      whether answer options should be randomized
     * @param config       type-specific configuration as JSON
     */
    public record QuestionEntry(
            FormQuestion.QuestionType questionType,
            String title,
            String description,
            boolean required,
            boolean shuffle,
            String config) {}
}
