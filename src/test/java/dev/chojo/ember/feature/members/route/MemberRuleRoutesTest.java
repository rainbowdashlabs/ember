/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.RuleField;
import dev.chojo.ember.feature.members.entity.RuleMember;
import dev.chojo.ember.feature.members.entity.RuleNode;
import dev.chojo.ember.feature.members.entity.RuleOperator;
import dev.chojo.ember.feature.members.entity.RuleOwner;
import dev.chojo.ember.feature.members.entity.RuleOwners;
import dev.chojo.ember.feature.members.entity.RulePreview;
import dev.chojo.ember.feature.members.entity.RuleSubject;
import dev.chojo.ember.feature.members.entity.TagVisibility;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.MemberRuleEditService;
import dev.chojo.ember.feature.members.service.PrivateTags;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.question.FieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reading, saving, trying out and removing the rule of a group or tag over HTTP. */
class MemberRuleRoutesTest {
    private static final int STATION_ID = 3;
    private static final String RULE = """
            {"rule": {"kind": "ALL", "children": [
              {"kind": "CHECK", "children": [], "subject": "FIELD", "operator": "IS_NO", "fieldId": 12}
            ]}}""";
    private static final RuleNode PARSED =
            RuleNode.all(RuleNode.check(RuleSubject.FIELD, RuleOperator.IS_NO, 12, null, null, null, null, null));

    private MemberRuleEditService edits;
    private PrivateTags privateTags;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        edits = mock(MemberRuleEditService.class);
        var groups = mock(MemberGroupService.class);
        var tags = mock(UserTagService.class);
        privateTags = mock(PrivateTags.class);
        when(groups.findById(4))
                .thenReturn(Optional.of(new MemberGroup(4, STATION_ID, "Jugend", null, 0, null, List.of())));
        when(groups.findById(9)).thenReturn(Optional.of(new MemberGroup(9, 99, "Fremd", null, 0, null, List.of())));
        when(tags.findById(5))
                .thenReturn(Optional.of(new UserTag(5, STATION_ID, "Leitung", null, TagVisibility.PRIVATE, 0)));
        when(privateTags.visibleTo(any(), any(UserTag.class))).thenReturn(true);
        harness = RouteHarness.serving(new MemberRuleRoutes(edits, groups, tags, privateTags));
    }

    private static UserSession groupManager() {
        return TestSessions.member(STATION_ID, StationPermission.MEMBER_MANAGE_GROUP);
    }

    @Test
    void theOperatorTableIsServed() {
        harness.run((server, client) -> {
            var table = json(client.get(PREFIX + "/member-rules/operators", harness.as(groupManager())));
            assertEquals(
                    MemberRuleEditService.operators().size(),
                    table.get("operators").size());
            assertEquals(MemberRuleEditService.MAX_DEPTH, table.get("maxDepth").asInt());
            assertEquals(
                    MemberRuleEditService.MAX_CHECKS, table.get("maxChecks").asInt());
        });
    }

    @Test
    void theRuleKeptGroupsAndTagsAreListedForAnyMember() {
        when(edits.owners(any())).thenReturn(new RuleOwners(List.of(4), List.of(5)));

        harness.run((server, client) -> {
            var owners = json(client.get(
                    PREFIX + "/member-rules", harness.as(TestSessions.member(STATION_ID, StationPermission.USER))));
            assertEquals(4, owners.path("groupIds").get(0).asInt());
            assertEquals(5, owners.path("tagIds").get(0).asInt());
        });
    }

    @Test
    void theReadableFieldsTakeTheRightToReadMembers() {
        when(edits.fields(any()))
                .thenReturn(List.of(new RuleField(FieldOrigin.STATION, 12, "JuLeiCa", FieldType.BOOLEAN, List.of())));

        harness.run((server, client) -> {
            var reader = TestSessions.member(STATION_ID, StationPermission.MEMBER_READ);
            assertEquals(
                    "JuLeiCa",
                    json(client.get(PREFIX + "/member-rules/fields", harness.as(reader)))
                            .get(0)
                            .path("name")
                            .asString());
            assertEquals(
                    403,
                    client.get(PREFIX + "/member-rules/fields", harness.as(groupManager()))
                            .code());
        });
    }

    @Test
    void aGroupWithoutARuleAnswersNone() {
        when(edits.find(RuleOwner.group(4))).thenReturn(Optional.empty());

        harness.run((server, client) -> {
            var response = json(client.get(PREFIX + "/groups/4/rule", harness.as(groupManager())));
            assertTrue(response.path("rule").isNull());
        });
    }

    @Test
    void aSavedRuleComesBack() {
        when(edits.save(any(), eq(RuleOwner.group(4)), eq(PARSED))).thenReturn(PARSED);

        harness.run((server, client) -> {
            var response = json(client.put(PREFIX + "/groups/4/rule", body(RULE), harness.as(groupManager())));
            assertEquals(
                    "IS_NO",
                    response.path("rule")
                            .path("children")
                            .get(0)
                            .path("operator")
                            .asString());
        });
    }

    @Test
    void aRequestWithoutARuleIsRefused() {
        harness.run((server, client) -> assertEquals(
                MemberRefusal.RULE_NEEDS_A_BLOCK,
                refusalOf(client.put(PREFIX + "/groups/4/rule", body("{}"), harness.as(groupManager())))));
        verify(edits, never()).save(any(), any(), any());
    }

    /** A body that names no kind, leaves out the children or holds an empty place answers 400, never 500. */
    @Test
    void aMalformedRuleIsRefusedAsSuch() {
        when(edits.save(any(), any(), any())).thenCallRealMethod();
        harness.run((server, client) -> {
            for (var malformed : List.of(
                    "{\"rule\": {\"kind\": \"ALL\"}}",
                    "{\"rule\": {\"kind\": \"ALL\", \"children\": [null]}}",
                    "{\"rule\": {\"kind\": null, \"children\": []}}")) {
                var response = client.put(PREFIX + "/groups/4/rule", body(malformed), harness.as(groupManager()));
                assertEquals(400, response.code(), malformed);
            }
        });
    }

    @Test
    void aRuleIsRemoved() {
        harness.run((server, client) -> assertEquals(
                204,
                client.delete(PREFIX + "/groups/4/rule", null, harness.as(groupManager()))
                        .code()));
        verify(edits).remove(RuleOwner.group(4));
    }

    @Test
    void aPreviewNamesWhoWouldJoinAndLeave() {
        var anna = new RuleMember(7, "Anna");
        when(edits.preview(any(), eq(RuleOwner.group(4)), eq(PARSED)))
                .thenReturn(new RulePreview(List.of(anna), List.of(anna), List.of()));

        harness.run((server, client) -> {
            var preview = json(client.post(PREFIX + "/groups/4/rule/preview", body(RULE), harness.as(groupManager())));
            assertEquals("Anna", preview.path("joining").get(0).path("name").asString());
            assertEquals(0, preview.path("leaving").size());
        });
    }

    /** A group of another station answers as if it were not there, and nothing is read or saved. */
    @Test
    void aGroupOfAnotherStationIsNotHere() {
        harness.run((server, client) -> {
            assertEquals(
                    GeneralRefusal.NOT_HERE_OR_NOT_YOURS,
                    refusalOf(client.get(PREFIX + "/groups/9/rule", harness.as(groupManager()))));
            assertEquals(
                    GeneralRefusal.NOT_HERE_OR_NOT_YOURS,
                    refusalOf(client.put(PREFIX + "/groups/9/rule", body(RULE), harness.as(groupManager()))));
        });
        verify(edits, never()).save(any(), any(), any());
    }

    @Test
    void tagRulesTakeTheTagRight() {
        var tagManager = TestSessions.member(STATION_ID, StationPermission.MEMBER_MANAGE_TAGS);
        when(edits.find(RuleOwner.tag(5))).thenReturn(Optional.of(PARSED));
        when(edits.save(any(), eq(RuleOwner.tag(5)), eq(PARSED))).thenReturn(PARSED);
        when(edits.preview(any(), eq(RuleOwner.tag(5)), eq(PARSED)))
                .thenReturn(new RulePreview(List.of(), List.of(), List.of()));

        harness.run((server, client) -> {
            assertEquals(
                    "ALL",
                    json(client.get(PREFIX + "/tags/5/rule", harness.as(tagManager)))
                            .path("rule")
                            .path("kind")
                            .asString());
            assertEquals(
                    200,
                    client.put(PREFIX + "/tags/5/rule", body(RULE), harness.as(tagManager))
                            .code());
            assertEquals(
                    200,
                    client.post(PREFIX + "/tags/5/rule/preview", body(RULE), harness.as(tagManager))
                            .code());
            assertEquals(
                    204,
                    client.delete(PREFIX + "/tags/5/rule", null, harness.as(tagManager))
                            .code());
            assertEquals(
                    403,
                    client.get(PREFIX + "/groups/4/rule", harness.as(tagManager))
                            .code());
        });
        verify(edits).remove(RuleOwner.tag(5));
    }

    /** A private tag the caller may not see answers as if it were not there. */
    @Test
    void aHiddenTagIsNotHere() {
        when(privateTags.visibleTo(any(), any(UserTag.class))).thenReturn(false);
        var tagManager = TestSessions.member(STATION_ID, StationPermission.MEMBER_MANAGE_TAGS);

        harness.run((server, client) -> assertEquals(
                GeneralRefusal.NOT_HERE_OR_NOT_YOURS,
                refusalOf(client.get(PREFIX + "/tags/5/rule", harness.as(tagManager)))));
    }
}
