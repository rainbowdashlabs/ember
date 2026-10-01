/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentWriter;
import dev.chojo.ember.feature.comment.entity.NewComment;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.service.KbAuthorNameService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a comment on a knowledge base file does today, pinned over the real routes, comment service
 * and storage: who reads, writes, changes and removes it, how it is listed and whom it tells.
 */
class KbCommentBehaviourTest extends RepositoryTestBase {
    private static final DomainEventBus BUS = mock(DomainEventBus.class);

    private static Station station;
    private static Station elsewhere;
    private static Account authorAccount;
    private static Account otherAccount;
    private static Account strangerAccount;
    private static StationMember author;
    private static StationMember other;
    private static StationMember stranger;
    private static int fileId;
    private static CommentService commentService;
    private static RouteHarness harness;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Kb Comment Behaviour");
        elsewhere = stationRepo.create("Kb Comment Elsewhere");
        authorAccount = accountRepo.create("kb-comment-author@test.com", "Anna", "Author");
        otherAccount = accountRepo.create("kb-comment-other@test.com", "Otto", "Other");
        strangerAccount = accountRepo.create("kb-comment-stranger@test.com", "Stella", "Stranger");
        author = stationMemberRepo.create(station.id(), authorAccount.id());
        other = stationMemberRepo.create(station.id(), otherAccount.id());
        stranger = stationMemberRepo.create(elsewhere.id(), strangerAccount.id());
        fileId = file("Handbuch");

        var names = mock(KbAuthorNameService.class);
        when(names.resolveMemberName(anyInt())).thenReturn("Anna Author");
        commentService = newCommentService(BUS);
        harness = RouteHarness.serving(new KnowledgeBaseCommentRoutes(
                        commentService, names, memberIdentityFactory, memberNameResolver))
                .withStations(stationRepo);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(elsewhere.id());
        accountRepo.delete(authorAccount.id());
        accountRepo.delete(otherAccount.id());
        accountRepo.delete(strangerAccount.id());
    }

    @BeforeEach
    void forgetEvents() {
        reset(BUS);
    }

    private static int file(String name) {
        return knowledgeBaseRepo
                .createFile(station.id(), null, name, "", KbFileType.MARKDOWN, "text/markdown", 0, null, author.id())
                .id();
    }

    private static UserSession as(StationMember member, StationPermission... permissions) {
        return signedIn(member, permissions);
    }

    private static Response post(StationMember member, int file, String requestBody) {
        return harness.request(client -> client.post(
                PREFIX + "/kb/files/%d/comments".formatted(file), body(requestBody), harness.as(as(member))));
    }

    private static int write(StationMember member, int file, String content) {
        return json(post(member, file, "{\"content\": \"%s\"}".formatted(content)))
                .path("id")
                .asInt();
    }

    private static Response change(UserSession session, int commentId, String content) {
        return harness.request(client -> client.put(
                PREFIX + "/kb/comments/" + commentId,
                body("{\"content\": \"%s\"}".formatted(content)),
                harness.as(session)));
    }

    private static Response remove(UserSession session, int commentId) {
        return harness.request(
                client -> client.delete(PREFIX + "/kb/comments/" + commentId, null, harness.as(session)));
    }

    private static List<DomainEvent> published() {
        var captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(BUS, atLeast(0)).publish(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void anotherStationNeitherReadsWritesNorTouchesAComment() {
        int id = write(author, fileId, "intern");

        harness.run((server, client) -> {
            assertEquals(
                    Refusal.NOT_HERE_OR_NOT_YOURS,
                    refusalOf(
                            client.get(PREFIX + "/kb/files/%d/comments".formatted(fileId), harness.as(as(stranger)))));
            assertEquals(
                    Refusal.NOT_HERE_OR_NOT_YOURS,
                    refusalOf(client.post(
                            PREFIX + "/kb/files/%d/comments".formatted(fileId),
                            body("{\"content\": \"x\"}"),
                            harness.as(as(stranger)))));
            assertEquals(
                    Refusal.NOT_HERE_OR_NOT_YOURS,
                    refusalOf(client.delete(
                            PREFIX + "/kb/comments/" + id,
                            null,
                            harness.as(as(stranger, StationPermission.KNOWLEDGE_MANAGER)))));
        });
    }

    @Test
    void onlyTheAuthorChangesACommentAndTheEditIsShown() {
        int id = write(author, fileId, "alt");

        assertEquals(
                Refusal.KB_COMMENT_NOT_YOURS_TO_CHANGE,
                refusalOf(change(as(other, StationPermission.KNOWLEDGE_MANAGER), id, "fremd")));
        var changed = json(change(as(author), id, "neu"));

        assertEquals("neu", changed.path("content").asString());
        assertFalse(changed.path("updatedAt").isMissingNode());
    }

    @Test
    void anEmptyChangeIsRefused() {
        int id = write(author, fileId, "alt");

        assertEquals(Refusal.KB_COMMENT_EMPTY, refusalOf(change(as(author), id, " ")));
    }

    @Test
    void theAuthorOrAKnowledgeManagerRemovesAComment() {
        int own = write(author, fileId, "eigen");
        int foreign = write(author, fileId, "fremd");
        int managed = write(author, fileId, "verwaltet");

        assertEquals(Refusal.KB_COMMENT_NOT_YOURS_TO_DELETE, refusalOf(remove(as(other), foreign)));
        assertEquals(204, remove(as(author), own).code());
        assertEquals(
                204,
                remove(as(other, StationPermission.KNOWLEDGE_MANAGER), managed).code());
        assertTrue(published().stream()
                .anyMatch(event -> event instanceof CommentDeleted deleted
                        && deleted.commentId() == own
                        && deleted.entityType() == CommentEntityType.KB));
    }

    @Test
    void aCommentWithRepliesLeavesAPlaceholder() {
        int parent = write(author, fileId, "Eltern");
        json(post(other, fileId, "{\"content\": \"Antwort\", \"parentId\": %d}".formatted(parent)));

        remove(as(author), parent);

        var placeholder = commentRepo.findById(CommentEntityType.KB, parent).orElseThrow();
        assertTrue(placeholder.deleted());
        assertEquals("", placeholder.content());
    }

    @Test
    void aReplyToACommentOnAnotherFileIsRefused() {
        int parent = write(author, file("Anderes"), "anderswo");

        var answer = post(other, fileId, "{\"content\": \"quer\", \"parentId\": %d}".formatted(parent));

        assertEquals(Refusal.COMMENT_PARENT_ELSEWHERE, refusalOf(answer));
    }

    @Test
    void aReplyTellsTheParentsAuthorWithItsPreviewCutAndATopLevelCommentTellsNobody() {
        int file = file("Ankuendigung");
        int parent = write(author, file, "Frage");
        reset(BUS);

        json(post(other, file, "{\"content\": \"%s\", \"parentId\": %d}".formatted("z".repeat(120), parent)));
        write(other, file, "oben");

        var created = published().stream()
                .filter(CommentCreated.class::isInstance)
                .map(CommentCreated.class::cast)
                .toList();
        assertEquals(1, created.size());
        assertEquals(author.id(), created.getFirst().parentAuthorId());
        assertEquals("z".repeat(100) + "…", created.getFirst().preview());
    }

    @Test
    void aPartnersCommentTellsNobodyAndItsRemovalIsAnnounced() {
        int file = file("Partner");
        var partnerUid = UUID.randomUUID();
        var target = commentService.target(CommentEntityType.KB, file).orElseThrow();
        var comment = commentService.createOn(
                target,
                CommentWriter.partner(new MemberIdentity(elsewhere.uid(), partnerUid), "Pia Partner"),
                new NewComment(null, null, "von draussen @[GROUP:Crew:7]"));

        assertTrue(published().isEmpty());

        assertTrue(commentService.delete(comment));
        assertTrue(published().stream()
                .anyMatch(event -> event instanceof CommentDeleted deleted && deleted.commentId() == comment.id()));
    }

    @Test
    void theListingIsOldestFirst() {
        int file = file("Reihe");
        write(author, file, "eins");
        write(other, file, "zwei");
        write(author, file, "drei");

        var listed = json(harness.request(
                client -> client.get(PREFIX + "/kb/files/%d/comments".formatted(file), harness.as(as(author)))));

        var texts = new ArrayList<String>();
        listed.forEach(comment -> texts.add(comment.path("content").asString()));
        assertEquals(List.of("eins", "zwei", "drei"), texts);
    }
}
