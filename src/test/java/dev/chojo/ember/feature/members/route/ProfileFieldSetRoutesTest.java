/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.FieldSet;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.question.FieldType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A station's groups of questions over HTTP, and the questions written with their group and the yes/no
 * question they depend on.
 */
class ProfileFieldSetRoutesTest {
    private static final int STATION = 3;

    private final ProfileFieldService fields = mock(ProfileFieldService.class);

    @Test
    void groupsAreListedWrittenRenamedOrderedAndDissolved() {
        var set = new FieldSet(4, STATION, "Adresse", true);
        when(fields.findSets(STATION)).thenReturn(List.of(set));
        when(fields.createSet(STATION, "Adresse", true)).thenReturn(set);
        var harness = RouteHarness.serving(new ProfileFieldSetRoutes(fields));
        var editor = harness.as(TestSessions.member(STATION, StationPermission.MEMBER_FIELDS, StationPermission.USER));

        harness.run((server, client) -> {
            assertEquals(
                    "Adresse",
                    json(client.get(PREFIX + "/profile-field-sets", editor))
                            .path(0)
                            .path("name")
                            .asString());
            assertEquals(
                    201,
                    client.post(
                                    PREFIX + "/profile-field-sets",
                                    body("{\"name\": \"Adresse\", \"nameAsSection\": true}"),
                                    editor)
                            .code());
            assertEquals(
                    204,
                    client.put(
                                    PREFIX + "/profile-field-sets/4",
                                    body("{\"name\": \"Anschrift\", \"nameAsSection\": false}"),
                                    editor)
                            .code());
            assertEquals(
                    204,
                    client.put(PREFIX + "/profile-field-sets/4/order", body("{\"fieldIds\": [2, 1]}"), editor)
                            .code());
            assertEquals(
                    204,
                    client.put(PREFIX + "/profile-field-sets/4/order", body("{}"), editor)
                            .code());
            assertEquals(
                    204,
                    client.delete(PREFIX + "/profile-field-sets/4", null, editor)
                            .code());
        });

        verify(fields).updateSet(STATION, 4, "Anschrift", false);
        verify(fields).orderSet(STATION, 4, List.of(2, 1));
        verify(fields).orderSet(STATION, 4, List.of());
        verify(fields).dissolveSet(STATION, 4);
    }

    @Test
    void aQuestionIsWrittenWithItsGroupAndTheQuestionItDependsOn() {
        var written = new ProfileField(
                9,
                STATION,
                "Ablaufdatum",
                FieldType.DATE,
                ProfileFieldConfig.empty(),
                false,
                false,
                null,
                false,
                8,
                4,
                0);
        when(fields.create(anyInt(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), anyBoolean()))
                .thenReturn(written);
        when(fields.findById(9)).thenReturn(Optional.of(written));
        when(fields.update(anyInt(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), anyBoolean()))
                .thenReturn(Optional.of(written));
        var harness = RouteHarness.serving(new ProfileFieldRoutes(
                fields, mock(StationMemberService.class), new GuardianPolicy(mock(StationMemberRepository.class))));
        var editor = harness.as(TestSessions.member(STATION, StationPermission.MEMBER_FIELDS));
        String request = """
                {"name": "Ablaufdatum", "fieldType": "DATE", "config": {}, "shownIfFieldId": 8, "fieldSetId": 4}""";

        harness.run((server, client) -> {
            var created = json(client.post(PREFIX + "/profile-fields", body(request), editor));
            assertEquals(4, created.path("fieldSetId").asInt());
            assertEquals(8, created.path("shownIfFieldId").asInt());
            assertEquals(
                    200,
                    client.put(PREFIX + "/profile-fields/9", body(request), editor)
                            .code());
        });

        verify(fields, times(2)).requireSetAllowed(STATION, 4);
        verify(fields, times(2)).joinSet(9, 4);
        verify(fields, times(2)).dependOn(9, 8);
    }
}
