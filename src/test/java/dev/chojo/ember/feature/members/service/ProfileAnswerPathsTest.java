/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ClusterFieldValueChanged;
import dev.chojo.ember.feature.cluster.entity.ClusterProfileField;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.ProfileAuthor;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldChange;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileWriter;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.repository.OwnerScene;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * What the two ways into a member's profile answers do today: the station writing (its own questions
 * and its association's), and the association writing through its member screen.
 *
 * <p>Pinned before the two were folded into one pipeline, on what is stored, what the change history
 * records and who is told.
 */
class ProfileAnswerPathsTest extends RepositoryTestBase {
    private OwnerScene scene;
    private StationMember member;
    private Notifier notifier;
    private List<ClusterFieldValueChanged> told;
    private ProfileFieldService answers;

    @BeforeEach
    void freshScene() {
        scene = OwnerScene.association("Antwortwege");
        member = scene.member();
        notifier = mock(Notifier.class);
        told = new ArrayList<>();
        var memberHears = new DomainEventHandler<ClusterFieldValueChanged>() {
            @Override
            public Class<ClusterFieldValueChanged> eventType() {
                return ClusterFieldValueChanged.class;
            }

            @Override
            public void handle(ClusterFieldValueChanged event) {
                told.add(event);
            }
        };
        answers = newProfileFieldService(newProfileFieldCore(notifier, new DomainEventBus(Set.of(memberHears))));
    }

    @AfterEach
    void closeScene() {
        scene.close();
    }

    private ProfileField stationAsks(String name, String config, boolean readonly) {
        var field = profileFieldService.create(
                scene.station().stationId(),
                name,
                FieldType.TEXT,
                ProfileFieldConfig.parse(config),
                false,
                readonly,
                null);
        profileFieldService.assignToRole(field.id(), ProfileFieldScope.MEMBER, 0, null, null, null);
        return field;
    }

    private ClusterProfileField associationAsks(String name, String config, boolean stationReadonly) {
        var field = clusterProfileFieldService.create(
                scene.association().clusterId(),
                name,
                FieldType.TEXT,
                ProfileFieldConfig.parse(config),
                false,
                false,
                null,
                stationReadonly,
                false,
                null);
        clusterProfileFieldService.assignToRole(
                scene.association().clusterId(), field.id(), ProfileFieldScope.MEMBER, 0, null, null, null);
        return field;
    }

    private List<ProfileFieldChange> changes() {
        return profileFieldChangeRepo.findByMember(member.id());
    }

    private void write(int author, ProfileWriter writer, FieldValueEntry... entries) {
        answers.setValues(member.id(), List.of(entries), author, writer);
    }

    private static FieldValueEntry station(int fieldId, String value) {
        return new FieldValueEntry(fieldId, value, FieldOrigin.STATION);
    }

    private static FieldValueEntry association(int fieldId, String value) {
        return new FieldValueEntry(fieldId, value, FieldOrigin.CLUSTER);
    }

    private StationMember holding(StationPermission permission) {
        var other = scene.newMember("Halter");
        var role = stationMemberRepo.findPermissionByName(permission).orElseThrow();
        stationMemberRepo.grantPermission(other.id(), role.id());
        return other;
    }

    private static NotificationData namingFields(String fieldNames) {
        return argThat(data -> data.params() instanceof NotificationParams.ProfileFieldChanged changed
                && changed.fieldName().equals(fieldNames));
    }

    @Test
    void theStationsAnswerIsKeptAndRecordedAgainstItsQuestion() {
        var field = stationAsks("Funkname", "{\"notifyOnChange\":true}", false);

        write(member.id(), ProfileWriter.station(false), station(field.id(), "\"Florian\""));

        assertEquals(
                "\"Florian\"",
                profileFieldRepo
                        .findValue(member.id(), field.id())
                        .orElseThrow()
                        .value());
        var change = changes().getFirst();
        assertEquals(field.id(), change.fieldId());
        assertNull(change.clusterFieldId());
        assertEquals("\"Florian\"", change.newValue());
        assertEquals(member.id(), change.changedBy());
        assertTrue(change.requiresAcknowledgement(), "the member's own change waits to be seen");
    }

    @Test
    void oneAuthorsChangesWithinTheWindowAreOneRecord() {
        var field = stationAsks("Spind", "{}", false);

        write(member.id(), ProfileWriter.station(false), station(field.id(), "\"12\""));
        write(member.id(), ProfileWriter.station(false), station(field.id(), "\"13\""));

        assertEquals(1, changes().size());
        assertEquals("\"13\"", changes().getFirst().newValue());
    }

    @Test
    void nothingSaidWhereNothingWasIsNoChange() {
        var field = stationAsks("Leer", "{}", false);

        write(member.id(), ProfileWriter.station(false), station(field.id(), "\"\""));

        assertTrue(changes().isEmpty());
        assertTrue(profileFieldRepo.findValue(member.id(), field.id()).isEmpty());
    }

    @Test
    void aChangeByWhoeverConfirmsChangesNeedsNoConfirmation() {
        var field = stationAsks("Bestaetigt", "{\"notifyOnChange\":true}", false);
        var confirmer = holding(StationPermission.MEMBER_CHANGES);

        write(confirmer.id(), ProfileWriter.station(true), station(field.id(), "\"x\""));

        assertFalse(changes().getFirst().requiresAcknowledgement());
    }

    @Test
    void theMemberManagersAreToldOnceNamingEveryChangedFieldButNotTheAuthor() {
        var first = stationAsks("Erstes", "{}", false);
        var second = stationAsks("Zweites", "{}", false);

        write(member.id(), ProfileWriter.station(false), station(first.id(), "\"a\""), station(second.id(), "\"b\""));

        verify(notifier)
                .notify(
                        eq(StationAudience.holders(scene.station().stationId(), StationPermission.MEMBER_MANAGER)
                                .except(member.id())),
                        eq(NotificationType.PROFILE_FIELD_CHANGED),
                        namingFields("Erstes, Zweites"),
                        eq(Delivery.ONCE_WHILE_UNREAD));
    }

    @Test
    void theStationsAnswerToAnAssociationQuestionIsRecordedAgainstThatQuestion() {
        var field = associationAsks("Verbandsnummer", "{\"notifyOnChange\":true}", false);

        write(member.id(), ProfileWriter.station(false), association(field.id(), "\"4711\""));

        var change = changes().getFirst();
        assertNull(change.fieldId());
        assertEquals(field.id(), change.clusterFieldId());
        assertEquals("\"4711\"", change.newValue());
        assertTrue(change.requiresAcknowledgement());
        verify(notifier).notify(any(), eq(NotificationType.PROFILE_FIELD_CHANGED), namingFields(field.name()), any());
    }

    /** Changed with the shared pipeline: an association's question used to get a record per save. */
    @Test
    void oneAuthorsChangesToAnAssociationQuestionWithinTheWindowAreOneRecord() {
        var field = associationAsks("Mitgliedsnummer", "{}", false);

        write(member.id(), ProfileWriter.station(false), association(field.id(), "\"1\""));
        write(member.id(), ProfileWriter.station(false), association(field.id(), "\"2\""));

        assertEquals(1, changes().size());
        assertEquals("\"2\"", changes().getFirst().newValue());
    }

    @Test
    void anAnswerTheBatchRefusesStopsTheBatch() {
        var number = profileFieldService.create(
                scene.station().stationId(), "Zahl", FieldType.NUMBER, ProfileFieldConfig.empty(), false, false, null);
        profileFieldService.assignToRole(number.id(), ProfileFieldScope.MEMBER, 0, null, null, null);
        var text = stationAsks("Text", "{}", false);

        assertThrows(
                RefusalResponse.class,
                () -> write(
                        member.id(),
                        ProfileWriter.station(false),
                        station(text.id(), "\"vorher\""),
                        station(number.id(), "\"zwei\"")));

        assertTrue(
                profileFieldRepo.findValue(member.id(), text.id()).isEmpty(),
                "one save is one write: the answer before the refused one is not kept either");
        assertTrue(changes().isEmpty());
        verify(notifier, never()).notify(any(), any(), any(), any());
    }

    @Test
    void theAssociationWritesWhatItKeepsFromTheStation() {
        var field = associationAsks("Verbandsgeheim", "{\"notifyOnChange\":true}", true);

        write(member.id(), ProfileWriter.association(), association(field.id(), "\"7\""));

        assertEquals(
                "\"7\"",
                clusterProfileFieldRepo.findValues(member.id()).getFirst().value());
        var change = changes().getFirst();
        assertEquals(field.id(), change.clusterFieldId());
        assertTrue(change.requiresAcknowledgement(), "the station has not seen what the association changed");
    }

    @Test
    void theAssociationPassesTheStationsOwnLock() {
        var field = stationAsks("Dienstgrad", "{}", true);

        write(member.id(), ProfileWriter.association(), station(field.id(), "\"Brandmeister\""));

        assertEquals(
                "\"Brandmeister\"",
                profileFieldRepo
                        .findValue(member.id(), field.id())
                        .orElseThrow()
                        .value());
    }

    @Test
    void theStationsManagersHearOfWhatTheAssociationChanged() {
        var field = associationAsks("Lehrgang", "{}", false);

        write(member.id(), ProfileWriter.association(), association(field.id(), "\"TM1\""));

        verify(notifier).notify(any(), eq(NotificationType.PROFILE_FIELD_CHANGED), namingFields("Lehrgang"), any());
    }

    /** The member hears what the association changed about them, station questions included. */
    @Test
    void theMemberHearsWhatTheAssociationChanged() {
        var asked = associationAsks("Funktion", "{}", false);
        var own = stationAsks("Spindnummer", "{}", false);

        write(
                member.id(),
                ProfileWriter.association(),
                association(asked.id(), "\"Kassenwart\""),
                station(own.id(), "\"7\""));

        assertEquals(1, told.size());
        assertEquals(member.id(), told.getFirst().memberId());
        assertEquals("Funktion, Spindnummer", told.getFirst().fieldNames());
    }

    @Test
    void theMemberHearsNothingOfWhatTheStationChanged() {
        var asked = associationAsks("Funktion", "{}", false);

        write(member.id(), ProfileWriter.station(true), association(asked.id(), "\"Kassenwart\""));

        assertTrue(told.isEmpty());
    }

    /** An association manager with no membership anywhere is recorded, and named, by their account. */
    @Test
    void anAssociationManagerWithoutAMembershipIsRecordedByAccount() {
        var field = associationAsks("Lehrgang", "{}", false);
        int manager = scene.newAccount("Vorstand");

        answers.setValues(
                member.id(),
                List.of(association(field.id(), "\"TM2\"")),
                ProfileAuthor.account(manager),
                ProfileWriter.association());

        var change = changes().getFirst();
        assertNull(change.changedBy());
        assertEquals("Vorstand Szene", change.changedByName());
    }

    /** One who is also a member at the member's station is recorded by that membership. */
    @Test
    void anAssociationManagerAtTheMembersStationIsRecordedByThatMembership() {
        var field = associationAsks("Lehrgang", "{}", false);
        var manager = scene.newMember("Vorstand");

        answers.setValues(
                member.id(),
                List.of(association(field.id(), "\"TM2\"")),
                ProfileAuthor.account(Objects.requireNonNull(manager.accountId())),
                ProfileWriter.association());

        assertEquals(manager.id(), changes().getFirst().changedBy());
    }
}
