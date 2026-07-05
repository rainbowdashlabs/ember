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
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.entity.FormResponse;
import dev.chojo.ember.feature.form.entity.QuestionEntry;
import dev.chojo.ember.feature.form.repository.FormRepository;
import dev.chojo.ember.feature.legal.entity.ConsentProof;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionRepository;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.system.service.RequirementsService;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for form management, including form CRUD, questions, responses, answers, and access control.
 * Delegates persistence to {@link FormRepository} and coordinates with member, group, and tag services for access checks.
 */
@Singleton
public class FormService {
    private static final Logger log = LoggerFactory.getLogger(FormService.class);
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

        var member = memberService.findById(memberId).orElse(null);
        if (member == null) return false;
        var memberGroupIds = groupService.findGroupsForMember(memberId).stream()
                .map(MemberGroup::id)
                .toList();
        var memberTagIds =
                tagService.findTagsForMember(memberId).stream().map(UserTag::id).toList();

        return restrictions.matches(member.userType(), memberGroupIds, memberTagIds, memberId);
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
        log.info("Updated form {} restriction mode to {}", formId, mode);
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
     * Retrieves all forms for a station with the given purpose.
     *
     * @param stationId the station ID
     * @param purpose   the purpose to filter by
     * @return list of forms ordered by creation date descending
     */
    public List<Form> findByStationAndPurpose(int stationId, FormPurpose purpose) {
        return repository.findByStationAndPurpose(stationId, purpose);
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
     * Finds a form by its public UUID.
     *
     * @param publicUid the public UUID
     * @return the form, or empty if not found
     */
    public Optional<Form> findByPublicUid(UUID publicUid) {
        return repository.findByPublicUid(publicUid);
    }

    public List<RequirementsService.RequirementItem> findForcedPending(int stationId, int memberId) {
        return repository.findForcedPending(stationId, memberId).stream()
                .map(f -> new RequirementsService.RequirementItem(f.id(), f.title()))
                .toList();
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
            boolean forced,
            Instant startAt,
            Instant endAt,
            int createdBy,
            FormPurpose purpose) {
        var form = repository.create(
                stationId, title, description, shuffleQuestions, allowEdit, forced, startAt, endAt, createdBy, purpose);
        log.info("Created form {} (station {}, purpose {}, createdBy {})", form.id(), stationId, purpose, createdBy);
        return form;
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
            boolean forced,
            Instant startAt,
            Instant endAt) {
        boolean updated =
                repository.update(id, title, description, shuffleQuestions, allowEdit, forced, startAt, endAt);
        if (updated) {
            log.info("Updated form {}", id);
        } else {
            log.warn("Form update affected zero rows for form {}", id);
        }
        return updated;
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
            log.info("Deleted form {} (station {})", id, form.stationId());
        } else if (!deleted) {
            log.warn("Form delete affected zero rows for form {}", id);
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
            log.info("Published form {} (station {})", id, form.stationId());
        } else if (!updated) {
            log.warn("Form publish affected zero rows for form {}", id);
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
        boolean updated = repository.updateStatus(id, Form.FormStatus.CLOSED);
        if (updated) {
            log.info("Closed form {}", id);
        } else {
            log.warn("Form close affected zero rows for form {}", id);
        }
        return updated;
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
     * @param formId           the form to add the question to
     * @param position         display order position
     * @param formQuestionType the type of question
     * @param title            the question text
     * @param description      optional description
     * @param required         whether an answer is mandatory
     * @param shuffle          whether answer options should be randomized
     * @param config           type-specific configuration as JSON
     * @return the newly created question
     */
    // Individual CRUD — routes use replaceQuestions() instead, kept for programmatic use
    public FormQuestion createQuestion(
            int formId,
            int position,
            FormQuestionType formQuestionType,
            String title,
            String description,
            boolean required,
            boolean shuffle,
            FormQuestionConfig config) {
        var question = repository.createQuestion(
                formId, position, formQuestionType, title, description, required, shuffle, config);
        log.info("Created question {} on form {} (type {})", question.id(), formId, formQuestionType);
        return question;
    }

    /**
     * Deletes a question by ID.
     *
     * @param id the question ID
     * @return {@code true} if the question was deleted
     */
    // Individual CRUD — routes use replaceQuestions() instead, kept for programmatic use
    public boolean deleteQuestion(int id) {
        boolean deleted = repository.deleteQuestion(id);
        if (deleted) {
            log.info("Deleted question {}", id);
        } else {
            log.warn("Question delete affected zero rows for question {}", id);
        }
        return deleted;
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
                    formId, i, q.formQuestionType(), q.title(), q.description(), q.required(), q.shuffle(), q.config());
        }
        log.info("Replaced questions on form {} ({} questions)", formId, questions.size());
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
     * Looks up a single response by its primary key (any form / any submitter).
     */
    public Optional<FormResponse> findResponseById(int responseId) {
        return repository.findResponseById(responseId);
    }

    /**
     * Marks a CONTACT-form submission as acknowledged by the supplied member. Idempotent — the
     * first call wins so a later viewer does not overwrite the original handler.
     */
    public void acknowledgeResponse(int responseId, int acknowledgerMemberId) {
        repository.acknowledgeResponse(responseId, acknowledgerMemberId);
        log.info("Acknowledged form response {} by member {}", responseId, acknowledgerMemberId);
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
     * Submits or updates a response for a member, validating all answers against question configs.
     *
     * @param formId      the form ID
     * @param memberId    the member the response is for
     * @param submittedBy the member who submitted the response (may differ for managed members)
     * @param answers     map of question ID to answer value
     * @return the created or updated response
     * @throws IllegalArgumentException if any answer fails validation
     */
    public FormResponse submitResponse(
            int formId, int memberId, int submittedBy, Map<Integer, FormAnswerValue> answers) {
        validateAnswers(formId, answers);
        var response = repository.createResponse(formId, memberId, submittedBy);
        for (var entry : answers.entrySet()) {
            repository.upsertAnswer(response.id(), entry.getKey(), entry.getValue());
        }
        log.info(
                "Submitted form response {} for form {} (member {}, submittedBy {})",
                response.id(),
                formId,
                memberId,
                submittedBy);
        return response;
    }

    /**
     * Stores an anonymous public form submission identified only by its submitter hash.
     * For POLL forms this also enforces single-submission dedup; CONTACT allows repeats.
     *
     * @param formId        the form ID
     * @param submitterHash SHA-256 hash identifying the submitter
     * @param answers       map of question ID to answer value
     * @param consent       proof that the submitter accepted privacy / ToS / consent
     * @return the created response
     * @throws IllegalArgumentException if any answer fails validation
     * @throws IllegalStateException    if the visitor already submitted a POLL response
     */
    public FormResponse submitAnonymousResponse(
            int formId, byte[] submitterHash, Map<Integer, FormAnswerValue> answers, ConsentProof consent) {
        validateAnswers(formId, answers);
        var response = repository.createAnonymousResponse(formId, submitterHash, consent);
        for (var entry : answers.entrySet()) {
            repository.upsertAnswer(response.id(), entry.getKey(), entry.getValue());
        }
        log.info("Submitted anonymous form response {} for form {}", response.id(), formId);
        return response;
    }

    /**
     * Returns {@code true} when the given hash already produced a response for this form.
     * Used by the public submit endpoint to enforce poll dedup.
     */
    public boolean hasAnonymousResponded(int formId, byte[] submitterHash) {
        return repository.findAnonymousResponse(formId, submitterHash).isPresent();
    }

    /**
     * Retrieves all answers for a specific response.
     *
     * @param responseId the response ID
     * @return list of answers
     */
    public List<FormAnswer> findAnswers(int responseId) {
        return repository.findAnswers(responseId);
    }

    // -- Answers --

    /**
     * Retrieves all answers submitted for a specific question across all responses. Useful for analytics.
     *
     * @param questionId the question ID
     * @return list of answers from all respondents
     */
    public List<FormAnswer> findAllAnswersForQuestion(int questionId) {
        return repository.findAllAnswersForQuestion(questionId);
    }

    /**
     * Replaces all access restrictions for a form.
     *
     * @param formId    the form ID
     * @param selection the restriction selection to apply
     */
    public void setRestrictions(int formId, RestrictionSelection selection) {
        restrictionRepository.setRestrictions(
                RestrictionType.FORM.table(), RestrictionType.FORM.fkColumn(), formId, selection);
        log.info("Updated access restrictions for form {}", formId);
    }

    // -- Restrictions --

    private void validateAnswers(int formId, Map<Integer, FormAnswerValue> answers) {
        var questions = repository.findQuestions(formId);
        var questionMap = questions.stream().collect(Collectors.toMap(FormQuestion::id, q -> q));

        var errors = new ArrayList<String>();

        for (var question : questions) {
            var value = answers.get(question.id());
            if (value == null && question.required()) {
                errors.add("Question '%s' is required".formatted(question.title()));
                continue;
            }
            if (value == null) continue;

            var validationErrors = question.config().validate(value);
            if (!validationErrors.isEmpty()) {
                errors.add("Question '%s': %s".formatted(question.title(), String.join(", ", validationErrors)));
            }
        }

        for (var questionId : answers.keySet()) {
            if (!questionMap.containsKey(questionId)) {
                errors.add("Answer for unknown question ID: " + questionId);
            }
        }

        if (!errors.isEmpty()) {
            throw new BadRequestResponse(String.join("; ", errors));
        }
    }
}
