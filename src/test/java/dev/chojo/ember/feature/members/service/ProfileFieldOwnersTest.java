/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.refusal.ClusterRefusal;
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
                profileFieldCore,
                mock(DocumentService.class),
                selfCheckService);
    }

    /** An association manager without a membership used to be recorded as member 0, which no row is. */
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

    /** Archiving used to clear the station's answers only. */
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

    /** The association used to refuse a spacer for having no name. */
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

    /** Trial members used to be accepted though the screen never offered them. */
    @Test
    void anAssociationDoesNotAskTrialMembers() {
        int field = associationAsks("Probezeit", false);

        assertThrows(
                RefusalResponse.class,
                () -> clusterProfileFieldService.assignToRole(
                        scene.association().clusterId(), field, ProfileFieldScope.TRIAL, 0, null, null, null));
    }

    /** A station could put its question to another station's group. */
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

    /** A station's question created to be kept on archive used to lose that on the way in. */
    @Test
    void aStationsQuestionIsKeptOnArchiveFromTheStart() {
        var field = profileFieldService.create(
                scene.station().stationId(),
                "Ehrennadel",
                FieldType.TEXT,
                ProfileFieldConfig.empty(),
                false,
                false,
                null,
                true);

        assertTrue(field.keepOnArchive());
    }

    /** Two spacers of one association are numbered apart, and renaming a spacer to nothing keeps its name. */
    @Test
    void associationSpacersAreNumberedApartAndKeepTheirName() {
        int clusterId = scene.association().clusterId();
        var first = clusterProfileFieldService.create(
                clusterId, null, FieldType.SPACER, ProfileFieldConfig.empty(), false, false, null, false, false, null);
        var second = clusterProfileFieldService.create(
                clusterId, " ", FieldType.SPACER, ProfileFieldConfig.empty(), false, false, null, false, false, null);

        clusterProfileFieldService.update(
                clusterId,
                second.id(),
                "",
                FieldType.SPACER,
                ProfileFieldConfig.empty(),
                false,
                false,
                null,
                false,
                false,
                null);

        assertEquals("Abstand 1", first.name());
        assertEquals("Abstand 2", second.name());
        assertEquals(
                "Abstand 2",
                clusterProfileFieldRepo.findById(second.id()).orElseThrow().name());
    }

    /** Any other question still needs its name. */
    @Test
    void anAssociationQuestionOtherThanASpacerNeedsAName() {
        var refusal = assertThrows(
                RefusalResponse.class,
                () -> clusterProfileFieldService.create(
                        scene.association().clusterId(),
                        " ",
                        FieldType.TEXT,
                        ProfileFieldConfig.empty(),
                        false,
                        false,
                        null,
                        false,
                        false,
                        null));

        assertEquals(ClusterRefusal.CLUSTER_PROFILE_FIELD_NEEDS_A_NAME, refusal.refusal());
    }

    /** A station's question name is trimmed like the association's. */
    @Test
    void aStationsQuestionNameIsTrimmed() {
        var field = profileFieldService.create(
                scene.station().stationId(),
                "  Spind  ",
                FieldType.TEXT,
                ProfileFieldConfig.empty(),
                false,
                false,
                null);

        assertEquals("Spind", field.name());
    }
}
