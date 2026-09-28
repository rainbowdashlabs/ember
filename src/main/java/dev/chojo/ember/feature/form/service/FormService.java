/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.FormDeleted;
import dev.chojo.ember.event.events.FormPublished;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormAnswer;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormDraft;
import dev.chojo.ember.feature.form.entity.FormPage;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.entity.FormResponse;
import dev.chojo.ember.feature.form.entity.FormVisibility;
import dev.chojo.ember.feature.form.entity.PageEntry;
import dev.chojo.ember.feature.form.entity.PageTarget;
import dev.chojo.ember.feature.form.entity.QuestionAnswerCount;
import dev.chojo.ember.feature.form.entity.QuestionEntry;
import dev.chojo.ember.feature.form.repository.FormRepository;
import dev.chojo.ember.feature.legal.entity.ConsentProof;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.feature.system.service.RequirementsService;
import dev.chojo.ember.util.ShareTokens;
import dev.chojo.ember.util.sql.Transactions;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final RestrictionService restrictionService;
    private final DomainEventBus eventBus;
    private final ShareTokens shareTokens;

    @Inject
    public FormService(
            FormRepository repository,
            StationMemberService memberService,
            MemberGroupService groupService,
            UserTagService tagService,
            RestrictionService restrictionService,
            DomainEventBus eventBus,
            ShareTokens shareTokens) {
        this.repository = repository;
        this.memberService = memberService;
        this.groupService = groupService;
        this.tagService = tagService;
        this.restrictionService = restrictionService;
        this.eventBus = eventBus;
        this.shareTokens = shareTokens;
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
        return restrictionService.findRestrictionSet(RestrictionType.FORM, formId, mode);
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
     * Whether the station has a form anybody outside it can reach at the form's own address.
     *
     * @param stationId the station ID
     * @return whether one such form exists
     */
    public boolean hasOpenlyAddressedForms(int stationId) {
        return repository.hasOpenlyAddressedForms(stationId);
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

    /**
     * The forms that must be answered and that somebody in a household still owes an answer.
     *
     * <p>A form is owed by whom its restrictions take in, not by whoever may open it: a manager sees
     * every restricted form and is asked none of them unless they are among the people it is for. A
     * guardian passes the members in their care along, because they answer for them.
     *
     * @param stationId the station
     * @param household the reader and, for a guardian, everybody in their care
     * @return one entry per form, however many in the household owe it
     */
    public List<RequirementsService.RequirementItem> findForcedPending(int stationId, List<Integer> household) {
        return repository.findForcedOpen(stationId).stream()
                .filter(form -> household.stream().anyMatch(memberId -> owesAnswer(form.id(), memberId)))
                .map(form -> new RequirementsService.RequirementItem(form.id(), form.title()))
                .toList();
    }

    private boolean owesAnswer(int formId, int memberId) {
        return !hasResponded(formId, memberId) && restrictionService.includes(RestrictionType.FORM, formId, memberId);
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
     * Sets what a form tells the reader once it is sent: its own message, and a link to go on to.
     *
     * <p>Blank parts are stored as none, which keeps the general thanks. The link is shown to anybody
     * who sends a public form, so only a web address or an address on this site is taken.
     *
     * @param id      the form
     * @param message what the reader is told
     * @param link    where the reader may go on to
     * @param label   what the link says
     * @throws dev.chojo.ember.api.RefusalResponse where the link is neither
     */
    public void setCompletion(int id, String message, String link, String label) {
        String cleanLink = blankToNull(link);
        if (cleanLink != null && !isOfferableLink(cleanLink)) throw Refusal.FORM_COMPLETION_LINK_NOT_A_LINK.raise();
        repository.updateCompletion(id, blankToNull(message), cleanLink, blankToNull(label));
    }

    private static boolean isOfferableLink(String link) {
        var lower = link.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("https://")
                || lower.startsWith("http://")
                || (link.startsWith("/") && !link.startsWith("//"));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    /**
     * Makes a draft copy of a form: its settings, pages, branches, questions and restrictions.
     *
     * <p>What belongs to the form being asked rather than to how it asks stays behind: the answers,
     * the link it was sent with, its start and end dates and its status, which is always draft. A
     * form is copied within its own kind, since its questions are only allowed for that kind.
     *
     * @param id        the form to copy
     * @param title     what the copy is called
     * @param createdBy the member making the copy
     * @return the copy, or empty where the form is not there
     */
    public Optional<Form> duplicate(int id, String title, int createdBy) {
        var source = repository.findById(id).orElse(null);
        if (source == null) return Optional.empty();
        var copy = Transactions.call(() -> {
            var made = repository.create(
                    source.stationId(),
                    title,
                    source.description(),
                    source.shuffleQuestions(),
                    source.allowEdit(),
                    source.forced(),
                    null,
                    null,
                    createdBy,
                    source.purpose());
            if (source.purpose() != FormPurpose.INTERNAL) repository.updateVisibility(made.id(), source.visibility());
            repository.updateRestrictionMode(made.id(), source.restrictionMode());
            copyRestrictions(source, made.id());
            repository.updateCompletion(
                    made.id(), source.completionMessage(), source.completionLink(), source.completionLinkLabel());
            saveLayout(made.id(), pageEntries(id), questionEntries(id));
            return made;
        });
        log.info("Duplicated form {} as {} (station {})", id, copy.id(), source.stationId());
        return repository.findById(copy.id());
    }

    private void copyRestrictions(Form source, int copyId) {
        if (source.purpose() != FormPurpose.INTERNAL) return;
        var set = findRestrictions(source.id());
        restrictionService.setRestrictions(
                RestrictionType.FORM,
                copyId,
                new RestrictionSelection(set.userTypes(), set.groupIds(), set.tagIds(), set.memberIds(), set.mode()));
    }

    private List<PageEntry> pageEntries(int formId) {
        return repository.findPages(formId).stream()
                .map(page -> new PageEntry(page.key(), page.title(), page.description(), page.after()))
                .toList();
    }

    private List<QuestionEntry> questionEntries(int formId) {
        return repository.findQuestions(formId).stream()
                .map(question -> new QuestionEntry(
                        null,
                        question.pageKey(),
                        question.formQuestionType(),
                        question.title(),
                        question.description(),
                        question.required(),
                        question.shuffle(),
                        question.config(),
                        question.branch()))
                .toList();
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
     * Opens a form for answers.
     *
     * <p>Only an internal form tells the station about it. A contact form and a public poll are
     * answered by people who are not members, and the notification pointed every member at the
     * internal page for filling a form in, which refuses them: wrong audience, wrong destination.
     *
     * @param id the form ID
     * @return {@code true} if the status was updated
     */
    public boolean publish(int id) {
        var form = repository.findById(id).orElse(null);
        boolean updated = repository.updateStatus(id, Form.FormStatus.OPEN);
        if (updated && form != null) {
            if (form.purpose() == FormPurpose.INTERNAL) {
                eventBus.publish(new FormPublished(form.stationId(), id, form.title()));
            }
            log.info("Published form {} (station {})", id, form.stationId());
        } else if (!updated) {
            log.warn("Form publish affected zero rows for form {}", id);
        }
        return updated;
    }

    /**
     * The link this form is sent with, where it has one.
     *
     * <p>Asking does not make one. The screens that show a link show it wherever a form is written
     * or its results read, and minting on sight would give a link to every form anybody ever opened,
     * including the ones nobody means to send. {@link #replaceShareLink} against no link is how the
     * first one is made, which is somebody pressing a button.
     *
     * <p>Only a form meant to be answered from outside has one at all. An internal form is reached
     * from the station's own screens, and a link that let anybody answer it would go around the
     * restrictions it carries.
     *
     * @param id the form ID
     * @return the link, or empty where the form has none, is internal, or is gone
     */
    public Optional<String> shareLink(int id) {
        return repository
                .findById(id)
                .filter(form -> form.purpose() != FormPurpose.INTERNAL)
                .flatMap(form -> repository.findShareToken(id));
    }

    /**
     * Replaces the link, ending every copy of the one the form carried.
     *
     * @param id       the form ID
     * @param expected the link the caller was shown
     * @return the new link, or empty where the form has since been given a different one
     */
    public Optional<String> replaceShareLink(int id, String expected) {
        var form = repository.findById(id).orElse(null);
        if (form == null) return Optional.empty();
        if (form.purpose() == FormPurpose.INTERNAL) {
            throw new BadRequestResponse("A form for the station's own members is not sent by link");
        }
        String replacement = shareTokens.mint();
        if (!repository.replaceShareToken(id, expected, replacement)) return Optional.empty();
        log.info("Form {} share link replaced", id);
        return Optional.of(replacement);
    }

    public Optional<Form> findByShareToken(String token) {
        return repository.findByShareToken(token).filter(form -> form.purpose() != FormPurpose.INTERNAL);
    }

    /**
     * Sets how far a form reaches: at its own address, or at the link it was sent with alone.
     *
     * <p>Only a form meant for people outside the station has either reach, so an internal one is
     * refused rather than quietly given a setting that decides nothing.
     *
     * @param id         the form ID
     * @param visibility what it becomes
     * @return {@code true} if a row changed
     */
    public boolean setVisibility(int id, FormVisibility visibility) {
        var form = repository.findById(id).orElse(null);
        if (form == null) return false;
        if (form.purpose() == FormPurpose.INTERNAL) {
            throw new BadRequestResponse("A form for the station's own members is not reached from outside at all");
        }
        boolean changed = repository.updateVisibility(id, visibility);
        if (changed) log.info("Form {} visibility set to {}", id, visibility);
        return changed;
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
            int drafts = repository.deleteDrafts(id);
            log.info("Closed form {} and ended {} drafts", id, drafts);
        } else {
            log.warn("Form close affected zero rows for form {}", id);
        }
        return updated;
    }

    /**
     * Throws away every answer a form has collected, leaving the form itself to be used again.
     *
     * <p>The way to start a poll over rather than build the same one twice: a trial run, a round
     * that went to the wrong people, or a question asked again next season. What goes is the
     * answers, so whoever answered before may answer again.
     *
     * @param id the form to empty
     * @return how many answers were thrown away
     */
    public int clearResponses(int id) {
        int cleared = repository.deleteResponses(id);
        log.info("Cleared {} responses from form {}", cleared, id);
        return cleared;
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
     * How many answers each question of a form holds, and how many of them name each of its options,
     * so an editor can say what removing a question or an option costs.
     *
     * @param formId the form ID
     * @return one count per question, in the order the questions are asked
     */
    public List<QuestionAnswerCount> countAnswersPerQuestion(int formId) {
        var answersByQuestion = repository.findAllAnswersForForm(formId).stream()
                .collect(Collectors.groupingBy(FormAnswer::questionId));
        return repository.findQuestions(formId).stream()
                .map(question -> countAnswers(question, answersByQuestion.getOrDefault(question.id(), List.of())))
                .toList();
    }

    private static QuestionAnswerCount countAnswers(FormQuestion question, List<FormAnswer> answers) {
        var perOption = new LinkedHashMap<String, Integer>();
        for (var key : question.config().optionKeys()) perOption.put(key, 0);
        for (var answer : answers) {
            var value = FormAnswerValue.parse(question.formQuestionType(), answer.value());
            if (value == null) continue;
            for (var key : value.optionKeys()) perOption.computeIfPresent(key, (k, count) -> count + 1);
        }
        return new QuestionAnswerCount(question.id(), answers.size(), perOption);
    }

    /**
     * Saves a form's questions as the editor sends them, in the order they are sent.
     *
     * <p>A question sent with its id is changed in place and keeps every answer given to it, one sent
     * without an id is added, and a question of the form that is no longer sent is removed together
     * with its answers. Nothing else is removed: deleting every question and writing them back took
     * all answers of the form with it, so fixing a typo in a running poll emptied the poll.
     *
     * <p>A question keeps its type. The answers stored against it were given to that type and mean
     * nothing read as another, so a changed type is refused rather than guessed at, answers or not.
     * Replacing a question by one of another type is removing the one and adding the other, which is
     * what the editor does.
     *
     * <p>Answers name options by key, so reordering and relabelling options leaves them as they are.
     * An option the editor removed takes its selections with it: a choice drops it from what was
     * picked and goes altogether where nothing and no "other" text is left, a ranking drops it from
     * the order, and a Likert grid drops the rating of that statement.
     *
     * <p>Everything happens in one transaction, and a refused question leaves the form as it was.
     *
     * @param formId    the form ID
     * @param questions the questions the form is to have
     * @throws dev.chojo.ember.api.RefusalResponse where an id is not one of this form's questions, an
     *                                             existing question is sent with another type, or the
     *                                             options of a question do not each carry a key of
     *                                             their own
     */
    public void saveQuestions(int formId, List<QuestionEntry> questions) {
        saveLayout(formId, pageEntries(formId), questions);
    }

    /**
     * Retrieves the pages of a form, in their order.
     *
     * @param formId the form ID
     * @return the pages, at least one for a form that exists
     */
    public List<FormPage> findPages(int formId) {
        return repository.findPages(formId);
    }

    /**
     * Saves a form's pages and its questions as the editor sends them, in the order they are sent.
     *
     * <p>Pages are kept by their key the way questions are kept by their id: a page sent with a key
     * the form has is changed in place, one with a new key is added, and a stored page that is not
     * sent is removed. Questions are saved as {@link #saveQuestions} describes, each on the page its
     * entry names, or on the first page where it names none.
     *
     * <p>A page only ever leads further down. A form can then never loop and every path through it
     * ends, which is refused here rather than left to whoever fills it in.
     *
     * @param formId    the form ID
     * @param pages     the pages the form is to have, at least one
     * @param questions the questions the form is to have
     * @throws dev.chojo.ember.api.RefusalResponse where the pages do not each carry a key of their own,
     *                                             one leads anywhere but further down, a question
     *                                             stands on a page that is not sent, or a question is
     *                                             refused as {@link #saveQuestions} describes
     */
    public void saveLayout(int formId, List<PageEntry> pages, List<QuestionEntry> questions) {
        requireDistinctPageKeys(pages);
        requireForwardTargets(pages);
        requireKnownPages(pages, questions);
        requireFittingBranches(pages, questions);
        String firstPage = pages.getFirst().key();
        Transactions.run(() -> {
            var stored = repository.findQuestions(formId).stream()
                    .collect(Collectors.toMap(FormQuestion::id, question -> question));
            requireOwnQuestionsOfUnchangedType(stored, questions);
            requireDistinctOptionKeys(questions);
            var storedPages = repository.findPages(formId);
            var pageIds = writePages(formId, storedPages, pages);
            var kept = questions.stream()
                    .map(QuestionEntry::id)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            stored.keySet().stream().filter(id -> !kept.contains(id)).forEach(repository::deleteQuestion);
            for (int position = 0; position < questions.size(); position++) {
                var question = questions.get(position);
                int pageId = pageIds.get(question.pageKey() == null ? firstPage : question.pageKey());
                writeQuestion(formId, pageId, position, question);
                if (question.id() != null) dropRemovedOptions(stored.get(question.id()), question.config());
            }
            storedPages.stream()
                    .filter(page -> !pageIds.containsKey(page.key()))
                    .forEach(page -> repository.deletePage(page.id()));
        });
        log.info("Saved form {} ({} pages, {} questions)", formId, pages.size(), questions.size());
    }

    private static void requireDistinctPageKeys(List<PageEntry> pages) {
        if (pages.isEmpty()) throw Refusal.FORM_PAGE_KEYS_NOT_DISTINCT.raise();
        var keys = new HashSet<String>();
        for (var page : pages) {
            if (page.key() == null || page.key().isBlank() || !keys.add(page.key())) {
                throw Refusal.FORM_PAGE_KEYS_NOT_DISTINCT.raise();
            }
        }
    }

    private static void requireForwardTargets(List<PageEntry> pages) {
        var positions = pagePositions(pages);
        for (int position = 0; position < pages.size(); position++) {
            if (!leadsForward(PageTarget.orNext(pages.get(position).after()), position, positions)) {
                throw Refusal.FORM_PAGE_TARGET_NOT_FURTHER_DOWN.raise();
            }
        }
    }

    /**
     * Whether a target leaves the page at the given position for somewhere further down, or for the
     * end of the form.
     */
    static boolean leadsForward(PageTarget target, int from, Map<String, Integer> positions) {
        if (target.kind() != PageTarget.TargetKind.PAGE) return true;
        var to = positions.get(target.page());
        return to != null && to > from;
    }

    static Map<String, Integer> pagePositions(List<PageEntry> pages) {
        var positions = new LinkedHashMap<String, Integer>();
        for (int position = 0; position < pages.size(); position++)
            positions.put(pages.get(position).key(), position);
        return positions;
    }

    /**
     * Refuses a branch on anything but a single-answer choice question, on options it does not have,
     * to a page that is not further down than its question's, or on a page that already has one.
     */
    private static void requireFittingBranches(List<PageEntry> pages, List<QuestionEntry> questions) {
        var positions = pagePositions(pages);
        String firstPage = pages.getFirst().key();
        var deciding = new HashSet<String>();
        for (var question : questions) {
            if (question.branch() == null) continue;
            String pageKey = question.pageKey() == null ? firstPage : question.pageKey();
            if (!(question.config() instanceof FormQuestionConfig.Choice choice)
                    || Boolean.TRUE.equals(choice.multiSelect())
                    || !choice.optionKeys()
                            .containsAll(question.branch().targets().keySet())) {
                throw Refusal.QUESTION_BRANCH_NOT_ON_A_SINGLE_CHOICE.raise();
            }
            if (!deciding.add(pageKey)) throw Refusal.PAGE_BRANCHES_ON_TWO_QUESTIONS.raise();
            int from = positions.get(pageKey);
            for (var target : question.branch().targets().values()) {
                if (!leadsForward(PageTarget.orNext(target), from, positions)) {
                    throw Refusal.FORM_PAGE_TARGET_NOT_FURTHER_DOWN.raise();
                }
            }
        }
    }

    private static void requireKnownPages(List<PageEntry> pages, List<QuestionEntry> questions) {
        var keys = pages.stream().map(PageEntry::key).collect(Collectors.toSet());
        for (var question : questions) {
            if (question.pageKey() != null && !keys.contains(question.pageKey())) {
                throw Refusal.QUESTION_ON_NO_PAGE.raise();
            }
        }
    }

    /**
     * Writes the pages as sent, the stored ones in place and the new ones added.
     *
     * @return the id of every page sent, by its key
     */
    private Map<String, Integer> writePages(int formId, List<FormPage> stored, List<PageEntry> pages) {
        var storedByKey = stored.stream().collect(Collectors.toMap(FormPage::key, page -> page));
        var ids = new LinkedHashMap<String, Integer>();
        for (int position = 0; position < pages.size(); position++) {
            var page = pages.get(position);
            var after = PageTarget.orNext(page.after());
            var existing = storedByKey.get(page.key());
            String title = page.title() == null ? "" : page.title();
            String description = page.description() == null ? "" : page.description();
            if (existing == null) {
                ids.put(
                        page.key(),
                        repository
                                .createPage(formId, page.key(), position, title, description, after)
                                .id());
            } else {
                repository.updatePage(existing.id(), position, title, description, after);
                ids.put(page.key(), existing.id());
            }
        }
        return ids;
    }

    private static void requireOwnQuestionsOfUnchangedType(
            Map<Integer, FormQuestion> stored, List<QuestionEntry> questions) {
        for (var question : questions) {
            if (question.id() == null) continue;
            var storedQuestion = stored.get(question.id());
            if (storedQuestion == null) throw Refusal.QUESTION_NOT_ON_THIS_FORM.raise();
            if (storedQuestion.formQuestionType() != question.formQuestionType()) {
                throw Refusal.QUESTION_TYPE_NOT_CHANGEABLE.raise();
            }
        }
    }

    private static void requireDistinctOptionKeys(List<QuestionEntry> questions) {
        for (var question : questions) {
            if (!question.config().hasDistinctOptionKeys()) throw Refusal.QUESTION_OPTION_KEYS_NOT_DISTINCT.raise();
        }
    }

    private void dropRemovedOptions(FormQuestion stored, FormQuestionConfig saved) {
        var removed = new HashSet<>(stored.config().optionKeys());
        removed.removeAll(saved.optionKeys());
        if (removed.isEmpty()) return;
        for (var answer : repository.findAnswersToQuestion(stored.id())) {
            var value = FormAnswerValue.parse(stored.formQuestionType(), answer.value());
            if (value == null || Collections.disjoint(value.optionKeys(), removed)) continue;
            value.withoutOptions(removed)
                    .ifPresentOrElse(
                            left -> repository.updateAnswerValue(answer.id(), left),
                            () -> repository.deleteAnswer(answer.id()));
        }
        log.info("Dropped removed options {} from the answers to question {}", removed, stored.id());
    }

    private void writeQuestion(int formId, int pageId, int position, QuestionEntry q) {
        if (q.id() == null) {
            repository.createQuestion(
                    formId,
                    pageId,
                    position,
                    q.formQuestionType(),
                    q.title(),
                    q.description(),
                    q.required(),
                    q.shuffle(),
                    q.config(),
                    q.branch());
            return;
        }
        repository.updateQuestion(
                q.id(),
                pageId,
                q.title(),
                q.description(),
                q.required(),
                q.shuffle(),
                q.config(),
                q.branch(),
                position);
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
     * Marks a CONTACT-form submission as acknowledged by the supplied member. Idempotent - the
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
     * Submits or updates a response for a member, walking the form's pages with the answers first.
     *
     * <p>The pages the answers lead through are what count: a required question on a page the path
     * skips is not missing, and an answer to one is dropped. The path is stored with the response.
     * Saving a response used to only add and change answers, so an edited response that took the
     * other branch kept the first branch's answers too; every stored answer the new path does not
     * reach is deleted now, and so is every answer sent empty.
     *
     * @param formId      the form ID
     * @param memberId    the member the response is for
     * @param submittedBy the member who submitted the response (may differ for managed members)
     * @param answers     map of question ID to answer value
     * @return the created or updated response
     * @throws FormAnswersRefused where an answer is missing, does not fit its question or answers a
     *                            question the form does not have, one problem per question
     */
    public FormResponse submitResponse(
            int formId, int memberId, int submittedBy, Map<Integer, FormAnswerValue> answers) {
        var walk = walked(formId, answers);
        var response = Transactions.call(() -> {
            var saved = repository.updateResponsePath(
                    repository.createResponse(formId, memberId, submittedBy).id(), walk.path());
            walk.answers().forEach((questionId, value) -> repository.upsertAnswer(saved.id(), questionId, value));
            repository.deleteAnswersExcept(saved.id(), walk.answers().keySet());
            repository.deleteDraft(formId, memberId);
            return saved;
        });
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
     * @throws FormAnswersRefused where the answers are refused, as {@link #submitResponse} describes
     */
    public FormResponse submitAnonymousResponse(
            int formId, byte[] submitterHash, Map<Integer, FormAnswerValue> answers, ConsentProof consent) {
        var walk = walked(formId, answers);
        var response = Transactions.call(() -> {
            var saved = repository.createAnonymousResponse(formId, submitterHash, consent, walk.path());
            walk.answers().forEach((questionId, value) -> repository.upsertAnswer(saved.id(), questionId, value));
            return saved;
        });
        log.info("Submitted anonymous form response {} for form {}", response.id(), formId);
        return response;
    }

    /**
     * The draft a member keeps of a form, where there is one.
     *
     * @param formId   the form
     * @param memberId the member the answer is for
     * @return the draft
     */
    public Optional<FormDraft> findDraft(int formId, int memberId) {
        return repository.findDraft(formId, memberId);
    }

    /**
     * Keeps what a member filled in so far, so the form can be continued later, on any device.
     *
     * <p>A draft is checked for its shape only: answers that are not answers of this form's questions
     * and pages the form does not have are left out, and nothing is required. It is not an answer and
     * is never counted as one.
     *
     * @param formId   the form
     * @param memberId the member the answer is for
     * @param savedBy  the member saving it, a guardian where they fill in for somebody in their care
     * @param answers  the answers so far, by question id
     * @param path     the pages visited so far, the page to continue on last
     */
    public void saveDraft(
            int formId, int memberId, int savedBy, Map<Integer, FormAnswerValue> answers, List<String> path) {
        var questions =
                repository.findQuestions(formId).stream().map(FormQuestion::id).collect(Collectors.toSet());
        var pages = repository.findPages(formId).stream().map(FormPage::key).collect(Collectors.toSet());
        var kept = new LinkedHashMap<Integer, FormAnswerValue>();
        if (answers != null) {
            answers.forEach((id, value) -> {
                if (id != null && value != null && questions.contains(id)) kept.put(id, value);
            });
        }
        var walked = path == null
                ? List.<String>of()
                : path.stream().filter(pages::contains).toList();
        repository.saveDraft(formId, memberId, savedBy, kept, walked);
    }

    /**
     * Throws away a member's draft of a form, which is what starting over amounts to.
     *
     * @param formId   the form
     * @param memberId the member the answer is for
     */
    public void discardDraft(int formId, int memberId) {
        repository.deleteDraft(formId, memberId);
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
     * Retrieves every answer given to a form, each carrying the response it belongs to.
     *
     * @param formId the form ID
     * @return all answers of all responses of the form
     */
    public List<FormAnswer> findAllAnswersForForm(int formId) {
        return repository.findAllAnswersForForm(formId);
    }

    /**
     * Replaces all access restrictions for a form.
     *
     * <p>Only a form answered by the station's own members has any. Narrowing who may answer needs
     * somebody to narrow it to, and a contact form or a poll on a public page is answered by
     * whoever opens the link: nothing anywhere reads a restriction on one, so storing it would keep
     * a promise the form cannot make.
     *
     * @param formId    the form ID
     * @param selection the restriction selection to apply
     * @throws BadRequestResponse where the form is answered from outside the station
     */
    public void setRestrictions(int formId, RestrictionSelection selection) {
        var form = repository.findById(formId).orElseThrow(NotFoundResponse::new);
        if (form.purpose() != FormPurpose.INTERNAL) {
            throw new BadRequestResponse("A form answered from outside the station has nobody to narrow it to");
        }
        restrictionService.setRestrictions(RestrictionType.FORM, formId, selection);
        log.info("Updated access restrictions for form {}", formId);
    }

    // -- Restrictions --

    /**
     * Walks the form with the given answers and refuses them where anything is wrong.
     */
    private FormPathWalker.Walk walked(int formId, Map<Integer, FormAnswerValue> answers) {
        var walk = FormPathWalker.walk(repository.findPages(formId), repository.findQuestions(formId), answers);
        if (!walk.problems().isEmpty()) {
            throw new FormAnswersRefused(Refusal.FORM_ANSWER_REFUSED, walk.problems());
        }
        return walk;
    }
}
