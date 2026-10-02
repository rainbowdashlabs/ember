/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileWriter;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.repository.OwnerScene;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * What one owner's profile questions did that the other's did not, and now both do.
 */
class ProfileFieldOwnersTest extends RepositoryTestBase {
    private OwnerScene scene;

    @BeforeEach
    void freshScene() {
        scene = OwnerScene.association("Eigner");
    }

    @AfterEach
    void closeScene() {
        scene.close();
    }

    private int associationAsks(String name, boolean keepOnArchive) {
        var field = clusterProfileFieldService.create(
                scene.association().clusterId(),
                name,
                FieldType.TEXT,
                ProfileFieldConfig.empty(),
                false,
                false,
                null,
                false,
                keepOnArchive,
                null);
        clusterProfileFieldService.assignToRole(
                scene.association().clusterId(), field.id(), ProfileFieldScope.MEMBER, 0, null, null, null);
        return field.id();
    }

    private FormerMemberService formerMembers() {
        return new FormerMemberService(
                stationMemberRepo,
                accountRepo,
                inventoryRepo,
                itemMovementService,
                memberGroupRepo,
                userTagRepo,
                attendanceRepo,
                profileFieldRepo,
                mock(DocumentService.class),
                selfCheckService);
    }

    /** TODO enable with the fix: an author without a station membership was written as member 0. */
    @Disabled("red until the association's author is recorded by account")
    @Test
    void anAssociationManagerWithoutAMembershipCanSaveAnAnswer() {
        int field = associationAsks("Ohne Wache", false);

        profileFieldService.setValues(
                scene.member().id(),
                List.of(new FieldValueEntry(field, "\"ja\"", FieldOrigin.CLUSTER)),
                0,
                ProfileWriter.association());

        assertEquals(
                "\"ja\"",
                clusterProfileFieldRepo
                        .findValues(scene.member().id())
                        .getFirst()
                        .value());
    }

    /** TODO enable with the fix: archiving cleared the station's answers only. */
    @Disabled("red until archiving clears association answers by their keep flag")
    @Test
    void archivingClearsTheAssociationsAnswersThatAreNotKept() {
        int dropped = associationAsks("Geht", false);
        int kept = associationAsks("Bleibt", true);
        profileFieldService.setValues(
                scene.member().id(),
                List.of(
                        new FieldValueEntry(dropped, "\"a\"", FieldOrigin.CLUSTER),
                        new FieldValueEntry(kept, "\"b\"", FieldOrigin.CLUSTER)),
                scene.member().id(),
                ProfileWriter.association());

        formerMembers().markFormer(scene.member().id());

        var left = clusterProfileFieldRepo.findValues(scene.member().id());
        assertEquals(1, left.size());
        assertEquals(kept, left.getFirst().fieldId());
    }

    /** TODO enable with the fix: the association refused a spacer for having no name. */
    @Disabled("red until an association spacer is numbered like a station's")
    @Test
    void anAssociationSpacerNeedsNoName() {
        var spacer = clusterProfileFieldService.create(
                scene.association().clusterId(),
                "",
                FieldType.SPACER,
                ProfileFieldConfig.empty(),
                false,
                false,
                null,
                false,
                false,
                null);

        assertEquals("Abstand 1", spacer.name());
    }

    /** TODO enable with the fix: trial members were accepted though an association does not ask them. */
    @Disabled("red until an association refuses to ask trial members")
    @Test
    void anAssociationDoesNotAskTrialMembers() {
        int field = associationAsks("Probezeit", false);

        assertThrows(
                RefusalResponse.class,
                () -> clusterProfileFieldService.assignToRole(
                        scene.association().clusterId(), field, ProfileFieldScope.TRIAL, 0, null, null, null));
    }

    /** TODO enable with the fix: a station could put its question to another station's group. */
    @Disabled("red until a station's question is put to its own groups only")
    @Test
    void aStationPutsItsQuestionToItsOwnGroupsOnly() {
        var field = profileFieldService.create(
                scene.station().stationId(), "Gruppe", FieldType.TEXT, ProfileFieldConfig.empty(), false, false, null);
        var elsewhere = stationRepo.create("Fremde Wache Gruppen");
        try {
            var foreign = memberGroupRepo.create(elsewhere.id(), "Fremde Gruppe");

            assertThrows(
                    RefusalResponse.class,
                    () -> profileFieldService.assignToGroup(field.id(), foreign.id(), 0, null, null, null));
            assertTrue(profileFieldService.findAssignments(field.id()).isEmpty());
        } finally {
            stationRepo.delete(elsewhere.id());
        }
    }
}
