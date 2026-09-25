/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.FormPublished;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.entity.FormVisibility;
import dev.chojo.ember.feature.form.entity.QuestionEntry;
import dev.chojo.ember.feature.legal.entity.ConsentProof;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.ShareTokens;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FormServiceTest extends RepositoryTestBase {
    private static final ConsentProof TEST_CONSENT =
            new ConsentProof("c", "p", "t", "127.0.0.1", "DE", "test-agent", Instant.now());

    private static FormService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int formId;
    private static int questionId;

    @BeforeAll
    static void setup() {
        var eventBus = new DomainEventBus(Set.of());
        var memberService = mock(StationMemberService.class);
        var groupService = mock(MemberGroupService.class);
        var tagService = mock(UserTagService.class);

        service = new FormService(
                formRepo, memberService, groupService, tagService, restrictionService, eventBus, new ShareTokens());
        station = stationRepo.create("FormSvcStation");
        account = accountRepo.create("form-svc@test.com", "Form", "Svc");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    // -- Forms --

    @Test
    @Order(1)
    void create() {
        var form = service.create(
                station.id(),
                "Survey 2026",
                "Annual survey",
                false,
                true,
                false,
                null,
                null,
                member.id(),
                FormPurpose.INTERNAL);
        assertNotNull(form);
        assertEquals("Survey 2026", form.title());
        assertEquals(Form.FormStatus.DRAFT, form.status());
        formId = form.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(service.findById(formId).isPresent());
        assertTrue(service.findById(99999).isEmpty());
    }

    @Test
    @Order(3)
    void findByStation() {
        var forms = service.findByStation(station.id());
        assertTrue(forms.stream().anyMatch(f -> f.id() == formId));
    }

    @Test
    @Order(4)
    void findByStationForMember() {
        var forms = service.findByStationForMember(station.id(), member.id());
        assertNotNull(forms);
    }

    @Test
    @Order(5)
    void update() {
        Instant start = Instant.parse("2020-01-01T00:00:00Z");
        Instant end = Instant.parse("2030-12-31T00:00:00Z");
        assertTrue(service.update(formId, "Updated Survey", "Updated desc", true, false, false, start, end));
        var found = service.findById(formId).orElseThrow();
        assertEquals("Updated Survey", found.title());
    }

    @Test
    @Order(6)
    void isAcceptingResponsesDraft() {
        var form = service.findById(formId).orElseThrow();
        // Still in DRAFT
        assertFalse(service.isAcceptingResponses(form));
    }

    @Test
    @Order(7)
    void publish() {
        assertTrue(service.publish(formId));
        var form = service.findById(formId).orElseThrow();
        assertEquals(Form.FormStatus.OPEN, form.status());
    }

    @Test
    @Order(8)
    void isAcceptingResponsesOpen() {
        var form = service.findById(formId).orElseThrow();
        assertTrue(service.isAcceptingResponses(form));
    }

    @Test
    @Order(9)
    void isAcceptingResponsesOutsideWindow() {
        // Set end time in the past
        Instant past = Instant.parse("2020-01-01T00:00:00Z");
        service.update(formId, "Updated Survey", "", false, true, false, null, past);
        var form = service.findById(formId).orElseThrow();
        assertFalse(service.isAcceptingResponses(form));
        // Reset
        service.update(formId, "Updated Survey", "", false, true, false, null, null);
    }

    // -- Questions --

    @Test
    @Order(10)
    void createQuestion() {
        var q = service.createQuestion(
                formId,
                0,
                FormQuestionType.TEXT,
                "Your name?",
                "Enter name",
                true,
                false,
                new FormQuestionConfig.Text(false));
        assertNotNull(q);
        assertEquals("Your name?", q.title());
        questionId = q.id();
    }

    @Test
    @Order(11)
    void findQuestions() {
        var qs = service.findQuestions(formId);
        assertFalse(qs.isEmpty());
        assertTrue(qs.stream().anyMatch(q -> q.id() == questionId));
    }

    @Test
    @Order(13)
    void replaceQuestions() {
        var entries = List.of(
                new QuestionEntry(
                        FormQuestionType.TEXT, "Question A", "Desc A", true, false, new FormQuestionConfig.Text(false)),
                new QuestionEntry(
                        FormQuestionType.TEXT,
                        "Question B",
                        "Desc B",
                        false,
                        false,
                        new FormQuestionConfig.Text(false)));
        service.replaceQuestions(formId, entries);
        var qs = service.findQuestions(formId);
        assertEquals(2, qs.size());
    }

    // -- Responses --

    @Test
    @Order(20)
    void submitResponse() {
        // get question IDs after replace
        var qs = service.findQuestions(formId);
        int qId = qs.getFirst().id();
        var response = service.submitResponse(
                formId, member.id(), member.id(), Map.of(qId, new FormAnswerValue.Text("John Doe")));
        assertNotNull(response);
        assertEquals(formId, response.formId());
    }

    @Test
    @Order(21)
    void hasResponded() {
        assertTrue(service.hasResponded(formId, member.id()));
        assertFalse(service.hasResponded(formId, 99999));
    }

    @Test
    @Order(22)
    void findResponse() {
        assertTrue(service.findResponse(formId, member.id()).isPresent());
        assertTrue(service.findResponse(formId, 99999).isEmpty());
    }

    @Test
    @Order(23)
    void findResponses() {
        assertFalse(service.findResponses(formId).isEmpty());
    }

    @Test
    @Order(24)
    void countResponses() {
        assertEquals(1, service.countResponses(formId));
    }

    @Test
    @Order(25)
    void findAnswers() {
        var response = service.findResponse(formId, member.id()).orElseThrow();
        var answers = service.findAnswers(response.id());
        assertFalse(answers.isEmpty());
    }

    @Test
    @Order(26)
    void findAllAnswersForForm() {
        var answers = service.findAllAnswersForForm(formId);
        assertFalse(answers.isEmpty());
    }

    // -- Restrictions --

    @Test
    @Order(30)
    void findRestrictionsEmpty() {
        var rs = service.findRestrictions(formId);
        assertFalse(rs.hasRestrictions());
    }

    @Test
    @Order(31)
    void setRestrictions() {
        service.setRestrictions(
                formId,
                new RestrictionSelection(List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), null));
        var rs = service.findRestrictions(formId);
        assertTrue(rs.hasRestrictions());
        // Clear
        service.setRestrictions(formId, RestrictionSelection.empty());
        assertFalse(service.findRestrictions(formId).hasRestrictions());
    }

    @Test
    @Order(32)
    void setRestrictionsNullLists() {
        service.setRestrictions(formId, new RestrictionSelection(null, null, null, null, null));
        assertFalse(service.findRestrictions(formId).hasRestrictions());
    }

    @Test
    @Order(33)
    void updateRestrictionMode() {
        service.updateRestrictionMode(formId, RestrictionMode.AND);
        assertEquals(RestrictionMode.AND, service.findById(formId).orElseThrow().restrictionMode());
    }

    @Test
    @Order(34)
    void canMemberAccessNoRestrictions() {
        // No restrictions = everyone can access
        assertTrue(service.canMemberAccess(formId, member.id()));
    }

    @Test
    @Order(34)
    void canMemberAccessWithRestrictions() {
        // Create a form with restrictions to exercise lines 72-80
        var form = service.create(
                station.id(), "Restricted Form", "", false, true, false, null, null, member.id(), FormPurpose.INTERNAL);

        // Set restrictions to specific member
        service.setRestrictions(
                form.id(), new RestrictionSelection(List.of(), List.of(), List.of(), List.of(member.id()), null));

        // Need to set up mocks for memberService, groupService, tagService
        var memberService = mock(StationMemberService.class);
        var groupService = mock(MemberGroupService.class);
        var tagService = mock(UserTagService.class);

        when(memberService.findById(member.id())).thenReturn(Optional.of(member));
        when(groupService.findGroupsForMember(member.id())).thenReturn(List.of());
        when(tagService.findTagsForMember(member.id())).thenReturn(List.of());

        var eventBus = new DomainEventBus(Set.of());
        var restrictedService = new FormService(
                formRepo, memberService, groupService, tagService, restrictionService, eventBus, new ShareTokens());

        // Member is in the restriction list - should have access
        assertTrue(restrictedService.canMemberAccess(form.id(), member.id()));

        // A different member ID not in the list - should NOT have access
        when(memberService.findById(99999))
                .thenReturn(Optional.of(new StationMember(
                        99999,
                        station.id(),
                        UUID.randomUUID(),
                        null,
                        false,
                        null,
                        null,
                        StationUserType.MEMBER,
                        null)));
        when(groupService.findGroupsForMember(99999)).thenReturn(List.of());
        when(tagService.findTagsForMember(99999)).thenReturn(List.of());
        assertFalse(restrictedService.canMemberAccess(form.id(), 99999));

        // Clean up
        service.delete(form.id());
    }

    // -- Close --

    @Test
    @Order(40)
    void close() {
        assertTrue(service.close(formId));
        assertEquals(
                Form.FormStatus.CLOSED, service.findById(formId).orElseThrow().status());
    }

    @Test
    @Order(35)
    void isAcceptingResponsesFutureStartAt() {
        // Form is OPEN but startAt is in the future - should not accept responses
        var form = service.create(
                station.id(), "Future Form", "", false, true, false, null, null, member.id(), FormPurpose.INTERNAL);
        service.publish(form.id());
        Instant future = Instant.now().plus(365, ChronoUnit.DAYS);
        service.update(form.id(), "Future Form", "", false, true, false, future, null);
        var updated = service.findById(form.id()).orElseThrow();
        assertFalse(service.isAcceptingResponses(updated));
        service.delete(form.id());
    }

    @Test
    @Order(36)
    void isAcceptingResponsesClosedStatus() {
        var form = service.create(
                station.id(), "Closed Form", "", false, true, false, null, null, member.id(), FormPurpose.INTERNAL);
        service.publish(form.id());
        service.close(form.id());
        var closed = service.findById(form.id()).orElseThrow();
        assertFalse(service.isAcceptingResponses(closed));
        service.delete(form.id());
    }

    @Test
    @Order(37)
    void deleteNonExistentForm() {
        assertFalse(service.delete(999999));
    }

    @Test
    @Order(38)
    void publishNonExistentForm() {
        // publish returns false for non-existent form (repo returns false)
        assertFalse(service.publish(999999));
    }

    @Test
    @Order(39)
    void deleteQuestionNonExistent() {
        assertFalse(service.deleteQuestion(999999));
    }

    @Test
    @Order(40)
    void findByStationAndPurpose() {
        var contactForm = service.create(
                station.id(), "Contact Form", "", false, true, false, null, null, member.id(), FormPurpose.CONTACT);
        var pollForm = service.create(
                station.id(), "Poll Form", "", false, true, false, null, null, member.id(), FormPurpose.POLL);
        try {
            assertEquals(
                    1,
                    service.findByStationAndPurpose(station.id(), FormPurpose.CONTACT)
                            .size());
            assertEquals(
                    1,
                    service.findByStationAndPurpose(station.id(), FormPurpose.POLL)
                            .size());
        } finally {
            service.delete(contactForm.id());
            service.delete(pollForm.id());
        }
    }

    @Test
    @Order(41)
    void findByPublicUid() {
        var contactForm = service.create(
                station.id(), "Lookup Form", "", false, true, false, null, null, member.id(), FormPurpose.CONTACT);
        try {
            var fetched = service.findByPublicUid(contactForm.publicUid());
            assertTrue(fetched.isPresent());
            assertEquals(contactForm.id(), fetched.get().id());
            assertTrue(service.findByPublicUid(UUID.randomUUID()).isEmpty());
        } finally {
            service.delete(contactForm.id());
        }
    }

    @Test
    @Order(42)
    void submitAnonymousResponseAndDedupCheck() {
        var pollForm = service.create(
                station.id(), "Anon Poll", "", false, true, false, null, null, member.id(), FormPurpose.POLL);
        try {
            service.publish(pollForm.id());
            byte[] hash = new byte[32];
            for (int i = 0; i < 32; i++) hash[i] = (byte) (i + 1);

            assertFalse(service.hasAnonymousResponded(pollForm.id(), hash));
            var response = service.submitAnonymousResponse(pollForm.id(), hash, Map.of(), TEST_CONSENT);
            assertNotNull(response);
            assertNull(response.memberId());
            assertNull(response.submittedBy());
            assertTrue(service.hasAnonymousResponded(pollForm.id(), hash));
        } finally {
            service.delete(pollForm.id());
        }
    }

    @Test
    @Order(43)
    void findResponseByIdAndAcknowledge() {
        var contactForm = service.create(
                station.id(), "Ack Form", "", false, true, false, null, null, member.id(), FormPurpose.CONTACT);
        try {
            service.publish(contactForm.id());
            byte[] hash = new byte[32];
            for (int i = 0; i < 32; i++) hash[i] = (byte) (50 + i);
            var response = service.submitAnonymousResponse(contactForm.id(), hash, Map.of(), TEST_CONSENT);

            var found = service.findResponseById(response.id());
            assertTrue(found.isPresent());
            assertEquals(response.id(), found.orElseThrow().id());
            assertTrue(service.findResponseById(99999).isEmpty());

            service.acknowledgeResponse(response.id(), member.id());
            var acked = service.findResponseById(response.id()).orElseThrow();
            assertNotNull(acked.acknowledgedAt());
        } finally {
            service.delete(contactForm.id());
        }
    }

    @Test
    @Order(44)
    void findForcedPending() {
        var pending = service.findForcedPending(station.id(), member.id());
        assertNotNull(pending);
    }

    @Test
    @Order(45)
    void aPublicFormIsSentByALinkThatCanBeWithdrawn() {
        var poll = service.create(
                station.id(),
                "Umfrage zum Versenden",
                "",
                false,
                false,
                false,
                null,
                null,
                member.id(),
                FormPurpose.POLL);

        assertTrue(
                service.shareLink(poll.id()).isEmpty(),
                "asking does not make one: a form nobody means to send should not get a link because"
                        + " somebody opened its editor");

        var link = service.replaceShareLink(poll.id(), null).orElseThrow();
        assertEquals(
                link,
                service.shareLink(poll.id()).orElseThrow(),
                "asking twice hands out the same link rather than ending the first one");
        assertEquals(poll.id(), service.findByShareToken(link).orElseThrow().id());

        var replacement = service.replaceShareLink(poll.id(), link).orElseThrow();
        assertNotEquals(link, replacement);
        assertTrue(service.findByShareToken(link).isEmpty(), "the link that was replaced opens nothing");
        assertEquals(
                poll.id(), service.findByShareToken(replacement).orElseThrow().id());

        assertTrue(
                service.replaceShareLink(poll.id(), link).isEmpty(),
                "whoever holds the old link is told it changed rather than ending somebody else's");

        service.delete(poll.id());
    }

    /**
     * A form on a public page answers at its own address, because that is what the page fetches it
     * by. A form that is only sent to the people it is meant for answers at its link and nowhere
     * else, so replacing the link really does end every way in that was given out.
     */
    @Test
    @Order(45)
    void aFormSentByLinkAloneIsNotAnsweredAtItsOwnAddress() {
        var poll = service.create(
                station.id(), "Nur per Link", "", false, false, false, null, null, member.id(), FormPurpose.POLL);
        assertEquals(FormVisibility.PUBLIC, poll.visibility(), "a form is openly addressed until somebody says not");

        assertTrue(service.setVisibility(poll.id(), FormVisibility.UNLISTED));
        assertFalse(
                service.findById(poll.id()).orElseThrow().visibility().openlyAddressed(),
                "and then its own address answers nothing");

        var internal = service.create(
                station.id(), "Intern", "", false, false, false, null, null, member.id(), FormPurpose.INTERNAL);
        assertThrows(
                BadRequestResponse.class,
                () -> service.setVisibility(internal.id(), FormVisibility.UNLISTED),
                "a form for the station's own members is not reached from outside at all");

        service.delete(poll.id());
        service.delete(internal.id());
    }

    @Test
    @Order(46)
    void anInternalFormIsNotSentByALink() {
        var internal = service.create(
                station.id(),
                "Nur für Mitglieder",
                "",
                false,
                false,
                false,
                null,
                null,
                member.id(),
                FormPurpose.INTERNAL);

        assertTrue(service.shareLink(internal.id()).isEmpty(), "an internal form is reached from inside the station");
        assertThrows(
                BadRequestResponse.class,
                () -> service.replaceShareLink(internal.id(), null),
                "and one cannot be given a link either, which is the half that would have let it out");
        assertTrue(service.shareLink(999999).isEmpty());
        assertTrue(service.replaceShareLink(999999, null).isEmpty());

        service.delete(internal.id());
    }

    /**
     * Opening a form for the public tells nobody. The notification says a new form is there to fill
     * in and points at the internal page for filling one in, which refuses anybody who is not a
     * member: wrong audience, wrong destination.
     */
    @Test
    @Order(47)
    void onlyAnInternalFormTellsTheStationItHasOpened() {
        var announced = new ArrayList<String>();
        var listening = new FormService(
                formRepo,
                mock(StationMemberService.class),
                mock(MemberGroupService.class),
                mock(UserTagService.class),
                restrictionService,
                new DomainEventBus(Set.of(new DomainEventHandler<FormPublished>() {
                    @Override
                    public Class<FormPublished> eventType() {
                        return FormPublished.class;
                    }

                    @Override
                    public void handle(FormPublished event) {
                        announced.add(event.formTitle());
                    }
                })),
                new ShareTokens());

        var poll = listening.create(
                station.id(),
                "Öffentliche Umfrage",
                "",
                false,
                false,
                false,
                null,
                null,
                member.id(),
                FormPurpose.POLL);
        var internal = listening.create(
                station.id(),
                "Interne Umfrage",
                "",
                false,
                false,
                false,
                null,
                null,
                member.id(),
                FormPurpose.INTERNAL);

        listening.publish(poll.id());
        listening.publish(internal.id());

        assertEquals(List.of("Interne Umfrage"), announced);

        listening.delete(poll.id());
        listening.delete(internal.id());
    }

    // -- Delete --

    @Test
    @Order(99)
    void delete() {
        assertTrue(service.delete(formId));
        assertTrue(service.findById(formId).isEmpty());
    }
}
