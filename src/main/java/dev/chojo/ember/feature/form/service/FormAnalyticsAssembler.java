/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormAnswer;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.entity.FormResponse;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the analytics + response listing DTOs shared between the auth-gated forms surface
 * (managers viewing INTERNAL forms via {@code /forms/{id}/…}) and the page-editor surface
 * (page editors viewing CONTACT / POLL forms via {@code /pages/forms/{id}/…} and
 * {@code /pages/polls/forms/{id}/…}). Keeping the DTO + assembly logic here means improvements
 * to the analytics shape land in both surfaces.
 *
 * <p>Permission and form-purpose gating remain the responsibility of the calling route - this
 * helper just turns an already-authorised form id into a {@link FormAnalyticsDto} or response
 * list.
 */
@Singleton
public class FormAnalyticsAssembler {
    private final FormService formService;
    private final FormRespondents formRespondents;
    private final FormResultGrouping resultGrouping;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public FormAnalyticsAssembler(
            FormService formService,
            FormRespondents formRespondents,
            FormResultGrouping resultGrouping,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            MemberIdentityFactory memberIdentityFactory) {
        this.formService = formService;
        this.formRespondents = formRespondents;
        this.resultGrouping = resultGrouping;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    /**
     * Aggregated analytics for a form: its questions, the counted answers of every response as one
     * group, and - for forms marked as required - the list of eligible members who have not yet
     * submitted a response.
     */
    public FormAnalyticsDto buildAnalytics(int formId) {
        return buildAnalytics(formId, null);
    }

    /**
     * Analytics for the responses a query picks, counted per group of respondents.
     *
     * <p>The filter narrows everything the result holds: the counts, the responses it names, and the
     * members still missing, so "who in the youth group has not answered" is one question. Without a
     * filter or a grouping, nothing about the respondents is read at all.
     *
     * @param formId the form
     * @param query  which respondents to count and how to group them; {@code null} counts everybody
     *               as one group
     * @return the analytics
     */
    public FormAnalyticsDto buildAnalytics(int formId, FormResultQuery query) {
        var form = formService.findById(formId).orElseThrow(NotFoundResponse::new);
        var questions = formService.findQuestions(formId);
        var answers = formService.findAllAnswersForForm(formId);
        var responses = formService.findResponses(formId);
        var filter = query == null ? null : query.filter();
        var grouping = query == null ? null : query.groupBy();

        List<FormResultGrouping.Bucket> buckets;
        Set<Integer> counted;
        if (filter == null && grouping == null) {
            counted = responses.stream().map(FormResponse::id).collect(Collectors.toCollection(LinkedHashSet::new));
            buckets = List.of(new FormResultGrouping.Bucket(ALL_RESPONSES, "", counted));
        } else {
            var respondents = formRespondents.ofResponses(form.stationId(), responses).stream()
                    .filter(respondent -> filter == null || filter.matches(respondent))
                    .toList();
            counted = respondents.stream()
                    .map(FormRespondents.Respondent::id)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            buckets = grouping == null
                    ? List.of(new FormResultGrouping.Bucket(ALL_RESPONSES, "", counted))
                    : resultGrouping.split(form.stationId(), respondents, grouping);
        }

        var groups = buckets.stream()
                .map(bucket -> new ResultGroupDto(
                        bucket.key(),
                        bucket.label(),
                        bucket.ids().size(),
                        FormResultTally.tally(questions, answers, bucket.ids())))
                .toList();
        return new FormAnalyticsDto(
                formId,
                counted.size(),
                List.copyOf(counted),
                questions.stream().map(QuestionInfoDto::of).toList(),
                groups,
                FormResultGrouping.overlaps(grouping),
                buildMissingResponses(form, filter));
    }

    private List<MemberIdentity> buildMissingResponses(Form form, FormResultQuery.Filter filter) {
        if (!form.forced()) return List.of();
        var missing = stationMemberRepository.findByStation(form.stationId()).stream()
                .filter(m -> formService.canMemberAccess(form.id(), m.id()))
                .filter(m -> !formService.hasResponded(form.id(), m.id()))
                .toList();
        if (filter != null) {
            var passing =
                    formRespondents
                            .ofMembers(
                                    form.stationId(),
                                    missing.stream().map(StationMember::id).toList())
                            .stream()
                            .filter(filter::matches)
                            .map(FormRespondents.Respondent::id)
                            .collect(Collectors.toSet());
            missing = missing.stream().filter(m -> passing.contains(m.id())).toList();
        }
        return missing.stream()
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
                .map(a -> NameParts.of(a).called())
                .orElse(null);
    }

    /** The key of the one group that holds every response, which is what an ungrouped view shows. */
    public static final String ALL_RESPONSES = "all";

    /**
     * Aggregated analytics payload - wire shape returned by the analytics endpoints.
     *
     * <p>{@code totalResponses} counts the responses the filter lets through, and {@code responseIds}
     * names them, so the individual answers can follow the same filter. {@code questions} describes
     * each question once; {@code groups} holds the counted answers, one entry per group of
     * respondents. An ungrouped view is a single group holding every counted response.
     * {@code groupsOverlap} says a respondent can count in more than one group, as with groups and
     * tags, so the groups can add up to more responses than {@code totalResponses}.
     */
    public record FormAnalyticsDto(
            int formId,
            int totalResponses,
            List<Integer> responseIds,
            List<QuestionInfoDto> questions,
            List<ResultGroupDto> groups,
            boolean groupsOverlap,
            List<MemberIdentity> missingResponses) {}

    /**
     * A question as the results view needs to know it: what it asks and how it is set up.
     */
    public record QuestionInfoDto(
            int questionId, FormQuestionType questionType, String title, FormQuestionConfig config) {
        static QuestionInfoDto of(FormQuestion question) {
            return new QuestionInfoDto(question.id(), question.formQuestionType(), question.title(), question.config());
        }
    }

    /**
     * One group of respondents and what they answered.
     *
     * @param key           stable identifier of the group within this result
     * @param label         what the group is called; empty for the group of every response
     * @param responseCount how many responses belong to the group
     * @param tallies       the counted answers, one per question in question order
     */
    public record ResultGroupDto(
            String key, String label, int responseCount, List<FormResultTally.QuestionTally> tallies) {}

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
