/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.entity.Form;
import dev.chojo.ember.entity.FormAnswer;
import dev.chojo.ember.entity.FormQuestion;
import dev.chojo.ember.entity.FormResponse;
import dev.chojo.ember.entity.MemberGroup;
import dev.chojo.ember.entity.Role;
import dev.chojo.ember.entity.UserTag;
import dev.chojo.ember.repository.FormRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
            if (memberTagIds.stream().anyMatch(tagRestrictions::contains)) return true;
        }

        return false;
    }

    // -- Forms --

    public List<Form> findByStation(int stationId) {
        return repository.findByStation(stationId);
    }

    public Optional<Form> findById(int id) {
        return repository.findById(id);
    }

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

    public boolean delete(int id) {
        return repository.delete(id);
    }

    public boolean publish(int id) {
        return repository.updateStatus(id, "OPEN");
    }

    public boolean close(int id) {
        return repository.updateStatus(id, "CLOSED");
    }

    public boolean isAcceptingResponses(Form form) {
        if (form.status() != Form.FormStatus.OPEN) return false;
        var now = Instant.now();
        if (form.startAt() != null && now.isBefore(form.startAt())) return false;
        return form.endAt() == null || !now.isAfter(form.endAt());
    }

    // -- Questions --

    public List<FormQuestion> findQuestions(int formId) {
        return repository.findQuestions(formId);
    }

    public FormQuestion createQuestion(
            int formId,
            int position,
            String questionType,
            String title,
            String description,
            boolean required,
            boolean shuffle,
            String config) {
        return repository.createQuestion(formId, position, questionType, title, description, required, shuffle, config);
    }

    public boolean updateQuestion(
            int id, String title, String description, boolean required, boolean shuffle, String config, int position) {
        return repository.updateQuestion(id, title, description, required, shuffle, config, position);
    }

    public boolean deleteQuestion(int id) {
        return repository.deleteQuestion(id);
    }

    public void replaceQuestions(int formId, List<QuestionEntry> questions) {
        repository.deleteQuestionsByForm(formId);
        for (int i = 0; i < questions.size(); i++) {
            var q = questions.get(i);
            repository.createQuestion(
                    formId, i, q.questionType(), q.title(), q.description(), q.required(), q.shuffle(), q.config());
        }
    }

    // -- Responses --

    public List<FormResponse> findResponses(int formId) {
        return repository.findResponses(formId);
    }

    public Optional<FormResponse> findResponse(int formId, int memberId) {
        return repository.findResponse(formId, memberId);
    }

    public int countResponses(int formId) {
        return repository.countResponses(formId);
    }

    public boolean hasResponded(int formId, int memberId) {
        return repository.hasResponded(formId, memberId);
    }

    public FormResponse submitResponse(int formId, int memberId, int submittedBy, Map<Integer, String> answers) {
        var response = repository.createResponse(formId, memberId, submittedBy);
        for (var entry : answers.entrySet()) {
            repository.upsertAnswer(response.id(), entry.getKey(), entry.getValue());
        }
        return response;
    }

    // -- Answers --

    public List<FormAnswer> findAnswers(int responseId) {
        return repository.findAnswers(responseId);
    }

    public List<FormAnswer> findAllAnswersForQuestion(int questionId) {
        return repository.findAllAnswersForQuestion(questionId);
    }

    // -- Restrictions --

    public List<Integer> findRoleRestrictions(int formId) {
        return repository.findRoleRestrictions(formId);
    }

    public List<Integer> findGroupRestrictions(int formId) {
        return repository.findGroupRestrictions(formId);
    }

    public List<Integer> findTagRestrictions(int formId) {
        return repository.findTagRestrictions(formId);
    }

    public void setRestrictions(int formId, List<Integer> roleIds, List<Integer> groupIds, List<Integer> tagIds) {
        repository.setRoleRestrictions(formId, roleIds != null ? roleIds : List.of());
        repository.setGroupRestrictions(formId, groupIds != null ? groupIds : List.of());
        repository.setTagRestrictions(formId, tagIds != null ? tagIds : List.of());
    }

    public record QuestionEntry(
            String questionType, String title, String description, boolean required, boolean shuffle, String config) {}
}
