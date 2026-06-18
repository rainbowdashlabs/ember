/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.entity.QuestionEntry;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FormServiceTest extends RepositoryTestBase {
    private static final dev.chojo.ember.feature.legal.entity.ConsentProof TEST_CONSENT =
            new dev.chojo.ember.feature.legal.entity.ConsentProof(
                    "c", "p", "t", "127.0.0.1", "DE", "test-agent", java.time.Instant.now());

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
        var restrictionRepo = new RestrictionRepository(stationMemberRepo, memberGroupRepo, userTagRepo);

        service = new FormService(formRepo, memberService, groupService, tagService, restrictionRepo, eventBus);
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
        assertTrue(service.update(formId, "Updated Survey", "Updated desc", true, false, start, end));
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
        service.update(formId, "Updated Survey", "", false, true, null, past);
        var form = service.findById(formId).orElseThrow();
        assertFalse(service.isAcceptingResponses(form));
        // Reset
        service.update(formId, "Updated Survey", "", false, true, null, null);
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
    void findAllAnswersForQuestion() {
        var qs = service.findQuestions(formId);
        var answers = service.findAllAnswersForQuestion(qs.getFirst().id());
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
        service.setRestrictions(formId, List.of(StationUserType.MEMBER), List.of(), List.of(), List.of());
        var rs = service.findRestrictions(formId);
        assertTrue(rs.hasRestrictions());
        // Clear
        service.setRestrictions(formId, List.of(), List.of(), List.of(), List.of());
        assertFalse(service.findRestrictions(formId).hasRestrictions());
    }

    @Test
    @Order(32)
    void setRestrictionsNullLists() {
        service.setRestrictions(formId, null, null, null, null);
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
                station.id(), "Restricted Form", "", false, true, null, null, member.id(), FormPurpose.INTERNAL);

        // Set restrictions to specific member
        service.setRestrictions(form.id(), List.of(), List.of(), List.of(), List.of(member.id()));

        // Need to set up mocks for memberService, groupService, tagService
        var memberService = mock(StationMemberService.class);
        var groupService = mock(MemberGroupService.class);
        var tagService = mock(UserTagService.class);

        when(memberService.findById(member.id())).thenReturn(Optional.of(member));
        when(groupService.findGroupsForMember(member.id())).thenReturn(List.of());
        when(tagService.findTagsForMember(member.id())).thenReturn(List.of());

        var restrictionRepo = new RestrictionRepository(stationMemberRepo, memberGroupRepo, userTagRepo);
        var eventBus = new DomainEventBus(Set.of());
        var restrictedService =
                new FormService(formRepo, memberService, groupService, tagService, restrictionRepo, eventBus);

        // Member is in the restriction list — should have access
        assertTrue(restrictedService.canMemberAccess(form.id(), member.id()));

        // A different member ID not in the list — should NOT have access
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
        // Form is OPEN but startAt is in the future — should not accept responses
        var form = service.create(
                station.id(), "Future Form", "", false, true, null, null, member.id(), FormPurpose.INTERNAL);
        service.publish(form.id());
        Instant future = Instant.now().plus(365, ChronoUnit.DAYS);
        service.update(form.id(), "Future Form", "", false, true, future, null);
        var updated = service.findById(form.id()).orElseThrow();
        assertFalse(service.isAcceptingResponses(updated));
        service.delete(form.id());
    }

    @Test
    @Order(36)
    void isAcceptingResponsesClosedStatus() {
        var form = service.create(
                station.id(), "Closed Form", "", false, true, null, null, member.id(), FormPurpose.INTERNAL);
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
                station.id(), "Contact Form", "", false, true, null, null, member.id(), FormPurpose.CONTACT);
        var pollForm =
                service.create(station.id(), "Poll Form", "", false, true, null, null, member.id(), FormPurpose.POLL);
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
                station.id(), "Lookup Form", "", false, true, null, null, member.id(), FormPurpose.CONTACT);
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
        var pollForm =
                service.create(station.id(), "Anon Poll", "", false, true, null, null, member.id(), FormPurpose.POLL);
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

    // -- Delete --

    @Test
    @Order(99)
    void delete() {
        assertTrue(service.delete(formId));
        assertTrue(service.findById(formId).isEmpty());
    }
}
