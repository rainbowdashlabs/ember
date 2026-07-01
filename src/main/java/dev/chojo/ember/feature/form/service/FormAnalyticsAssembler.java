/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.form.entity.FormAnswer;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.entity.FormResponse;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Builds the analytics + response listing DTOs shared between the auth-gated forms surface
 * (managers viewing INTERNAL forms via {@code /forms/{id}/…}) and the page-editor surface
 * (page editors viewing CONTACT / POLL forms via {@code /pages/forms/{id}/…} and
 * {@code /pages/polls/forms/{id}/…}). Keeping the DTO + assembly logic here means improvements
 * to the analytics shape land in both surfaces.
 *
 * <p>Permission and form-purpose gating remain the responsibility of the calling route — this
 * helper just turns an already-authorised form id into a {@link FormAnalyticsDto} or response
 * list.
 */
@Singleton
public class FormAnalyticsAssembler {
    private final FormService formService;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public FormAnalyticsAssembler(
            FormService formService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            MemberIdentityFactory memberIdentityFactory) {
        this.formService = formService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    /**
     * Aggregated analytics for a form, including per-question answer data and — for forms
     * marked as required — the list of eligible members who have not yet submitted a response.
     */
    public FormAnalyticsDto buildAnalytics(int formId) {
        var questions = formService.findQuestions(formId);
        var questionAnalytics = questions.stream()
                .map(q -> {
                    var answers = formService.findAllAnswersForQuestion(q.id());
                    var values = answers.stream().map(FormAnswer::value).toList();
                    return new QuestionAnalyticsDto(q.id(), q.formQuestionType(), q.title(), q.config(), values);
                })
                .toList();
        return new FormAnalyticsDto(
                formId, formService.countResponses(formId), questionAnalytics, buildMissingResponses(formId));
    }

    private List<MemberIdentity> buildMissingResponses(int formId) {
        var form = formService.findById(formId).orElse(null);
        if (form == null || !form.forced()) return List.of();
        return stationMemberRepository.findByStation(form.stationId()).stream()
                .filter(m -> formService.canMemberAccess(formId, m.id()))
                .filter(m -> !formService.hasResponded(formId, m.id()))
                .map(m -> memberIdentityFactory.local(m.stationId(), m.id()))
                .toList();
    }

    /**
     * All responses for a form, mapped to the listing DTO shape.
     */
    public List<FormResponseEntryDto> listResponses(int formId) {
        return formService.findResponses(formId).stream().map(this::toEntry).toList();
    }

    /**
     * Detail view for a single response: metadata + all answers. The route is responsible for
     * checking that the {@code responseId} actually belongs to the form being queried.
     */
    public ResponseDetailDto getResponseDetail(int formId, int responseId) {
        var answers = formService.findAnswers(responseId);
        var response = formService.findResponses(formId).stream()
                .filter(r -> r.id() == responseId)
                .findFirst()
                .orElseThrow(NotFoundResponse::new);
        return new ResponseDetailDto(toEntry(response), answers);
    }

    private FormResponseEntryDto toEntry(FormResponse r) {
        Integer memberId = r.memberId();
        Integer submittedBy = r.submittedBy();
        String submittedByName = memberId != null && submittedBy != null && !submittedBy.equals(memberId)
                ? resolveMemberName(submittedBy)
                : null;
        MemberIdentity memberIdentity = memberId == null
                ? null
                : stationMemberRepository
                        .findById(memberId)
                        .map(m -> memberIdentityFactory.local(m.stationId(), memberId))
                        .orElse(null);
        MemberIdentity acknowledgedByIdentity = Optional.ofNullable(r.acknowledgedBy())
                .flatMap(stationMemberRepository::findById)
                .map(m -> memberIdentityFactory.local(m.stationId(), r.acknowledgedBy()))
                .orElse(null);
        return new FormResponseEntryDto(
                r.id(),
                r.formId(),
                memberId,
                submittedBy,
                submittedByName,
                memberIdentity,
                r.submittedAt(),
                r.updatedAt(),
                r.acknowledgedAt(),
                r.acknowledgedBy(),
                acknowledgedByIdentity);
    }

    private String resolveMemberName(int memberId) {
        return stationMemberRepository
                .findById(memberId)
                .flatMap(m -> accountRepository.findById(m.accountId()))
                .map(a -> (a.firstName() + " " + a.lastName()).trim())
                .orElse(null);
    }

    /**
     * Aggregated analytics payload — wire shape returned by the analytics endpoints.
     */
    public record FormAnalyticsDto(
            int formId,
            int totalResponses,
            List<QuestionAnalyticsDto> questions,
            List<MemberIdentity> missingResponses) {}

    /**
     * Per-question analytics row carried inside {@link FormAnalyticsDto}.
     */
    public record QuestionAnalyticsDto(
            int questionId,
            FormQuestionType questionType,
            String title,
            FormQuestionConfig config,
            List<String> values) {}

    /**
     * Listing entry for a single form response. {@code memberId} and {@code submittedBy} are
     * {@code null} for anonymous public submissions (CONTACT / POLL purposes); managers see them
     * as anonymous in the UI. {@code submittedByName} is only populated when the response was
     * submitted on behalf of someone else (e.g. a guardian). {@code acknowledgedAt} carries the
     * CONTACT-form acknowledgement state, or {@code null} when the submission is still unhandled
     * or the form is not a CONTACT form. {@code acknowledgedByIdentity} is the enriched
     * {@link MemberIdentity} of the acknowledger so the frontend can render it via the standard
     * {@code MemberName} component.
     */
    public record FormResponseEntryDto(
            int id,
            int formId,
            Integer memberId,
            Integer submittedBy,
            String submittedByName,
            MemberIdentity memberIdentity,
            Instant submittedAt,
            Instant updatedAt,
            Instant acknowledgedAt,
            Integer acknowledgedBy,
            MemberIdentity acknowledgedByIdentity) {}

    /**
     * Detail view: response metadata + all answers in submission order.
     */
    public record ResponseDetailDto(FormResponseEntryDto response, List<FormAnswer> answers) {}
}
