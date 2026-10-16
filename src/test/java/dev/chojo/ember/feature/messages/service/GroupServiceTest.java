/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.Membership;
import dev.chojo.ember.feature.messages.entity.Message;
import dev.chojo.ember.feature.messages.entity.MessageEventKind;
import dev.chojo.ember.feature.messages.entity.SystemKind;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Groups: starting one, its size, who adds, removes and renames, the system lines, and ownership
 * passing to the longest member.
 */
class GroupServiceTest extends MessageTestBase {
    private MessageFixtures services;
    private Station station;
    private Account owner;
    private Account ben;
    private Account cora;

    @BeforeEach
    void people() {
        services = MessageFixtures.build();
        station = station();
        owner = memberOf(station, "Olga");
        ben = memberOf(station, "Ben");
        cora = memberOf(station, "Cora");
    }

    private Conversation group(String name) {
        return services.groups().create(owner.id(), List.of(ben.uid(), cora.uid()), name);
    }

    private List<SystemKind> systemLines(Conversation group) {
        return services.messages().page(owner.id(), group.uid(), null, null).messages().stream()
                .map(view -> view.message().systemKind())
                .filter(kind -> kind != null)
                .toList();
    }

    @Test
    void theStarterOwnsTheGroupAndEverybodyIsIn() {
        var group = group("  Jugend  ");

        var detail = services.conversations().detail(ben.id(), group.uid());
        assertEquals("Jugend", detail.conversation().name());
        assertEquals(owner.id(), detail.conversation().ownerAccountId());
        assertEquals(3, detail.members().size());
    }

    @Test
    void aGroupNeedsSomebodyElse() {
        assertRefused(MessageRefusal.GROUP_NEEDS_SOMEBODY, () -> services.groups()
                .create(owner.id(), List.of(owner.uid()), null));
    }

    @Test
    void aGroupNameHasALimit() {
        assertRefused(MessageRefusal.GROUP_NAME_TOO_LONG, () -> services.groups()
                .create(owner.id(), List.of(ben.uid()), "x".repeat(101)));
    }

    @Test
    void aGroupHoldsTwentyFivePeople() {
        List<UUID> many = new ArrayList<>();
        for (int i = 0; i < GroupService.GROUP_SIZE; i++)
            many.add(memberOf(station, "Viele" + i).uid());

        assertRefused(MessageRefusal.GROUP_TOO_LARGE, () -> services.groups().create(owner.id(), many, null));

        var full = services.groups().create(owner.id(), many.subList(0, GroupService.GROUP_SIZE - 1), null);
        var one = memberOf(station, "Einer");
        assertRefused(MessageRefusal.GROUP_TOO_LARGE, () -> services.groups()
                .add(owner.id(), full.uid(), List.of(one.uid())));
    }

    @Test
    void anyMemberAddsPeopleTheyMayWriteToAndTheNewcomerReadsTheHistory() {
        var group = group(null);
        services.messages().send(owner.id(), group.uid(), "Vorher", null);
        var dana = memberOf(station, "Dana");

        services.groups().add(ben.id(), group.uid(), List.of(dana.uid(), cora.uid()));

        var page = services.messages().page(dana.id(), group.uid(), null, null);
        assertEquals("Vorher", page.messages().getFirst().message().content());
        assertEquals(0, services.conversations().unread(dana.id()), "history from before is not unread");
        assertEquals(List.of(SystemKind.ADDED), systemLines(group));
    }

    @Test
    void somebodyOutOfReachCannotBeAdded() {
        var group = group(null);
        var stranger = memberOf(station(), "Fremd");

        assertRefused(MessageRefusal.PERSON_NOT_REACHABLE, () -> services.groups()
                .add(ben.id(), group.uid(), List.of(stranger.uid())));
    }

    @Test
    void somebodyWhoBlockedTheAdderCannotBeAdded() {
        var group = services.groups().create(owner.id(), List.of(ben.uid()), null);
        services.blocks().block(cora.id(), ben.uid());

        assertRefused(MessageRefusal.PERSON_NOT_REACHABLE, () -> services.groups()
                .add(ben.id(), group.uid(), List.of(cora.uid())));
    }

    @Test
    void onlyTheOwnerRemovesAndRenames() {
        var group = group(null);

        assertRefused(
                MessageRefusal.REMOVE_NOT_OWNER, () -> services.groups().remove(ben.id(), group.uid(), cora.uid()));
        assertRefused(MessageRefusal.RENAME_NOT_OWNER, () -> services.groups().rename(ben.id(), group.uid(), "Neu"));
        assertRefused(MessageRefusal.REMOVE_SELF, () -> services.groups().remove(owner.id(), group.uid(), owner.uid()));
        assertRefused(MessageRefusal.REMOVE_NOT_A_MEMBER, () -> services.groups()
                .remove(owner.id(), group.uid(), UUID.randomUUID()));
    }

    @Test
    void theOwnerRemovesSomebodyAndTheyAreTold() {
        var group = group(null);
        var coras = new RecordingStream();
        services.signals().join(cora.id(), coras);

        services.groups().remove(owner.id(), group.uid(), cora.uid());

        assertRefused(MessageRefusal.CONVERSATION_NOT_HERE, () -> services.conversations()
                .detail(cora.id(), group.uid()));
        assertEquals(MessageEventKind.CONVERSATION, coras.signals().getLast().kind());
        assertEquals(List.of(SystemKind.REMOVED), systemLines(group));
    }

    @Test
    void renamingLeavesALineAndAnEmptyNameCallsTheGroupAfterItsMembers() {
        var group = group("Alt");

        services.groups().rename(owner.id(), group.uid(), "Neu");
        services.groups().rename(owner.id(), group.uid(), "Neu");
        services.groups().rename(owner.id(), group.uid(), " ");

        assertNull(services.conversations()
                .detail(owner.id(), group.uid())
                .conversation()
                .name());
        assertEquals(List.of(SystemKind.RENAMED, SystemKind.RENAMED), systemLines(group));
    }

    @Test
    void theGroupPassesToTheLongestMemberWhenTheOwnerLeaves() {
        var group = group(null);

        services.groups().leave(owner.id(), group.uid());

        var detail = services.conversations().detail(ben.id(), group.uid());
        Membership heir = detail.members().getFirst();
        assertEquals(heir.accountId(), detail.conversation().ownerAccountId());
        List<Message> lines = services.messages().page(ben.id(), group.uid(), null, null).messages().stream()
                .map(view -> view.message())
                .toList();
        assertEquals(SystemKind.LEFT, lines.get(0).systemKind());
        assertEquals(SystemKind.OWNER_PASSED, lines.get(1).systemKind());
        assertEquals(heir.accountId(), lines.get(1).subject().accountId());
        assertNull(lines.get(1).author());
    }

    @Test
    void theLastOneToLeaveLeavesNoOwner() {
        var group = services.groups().create(owner.id(), List.of(ben.uid()), null);
        services.groups().leave(ben.id(), group.uid());
        services.groups().leave(owner.id(), group.uid());

        assertTrue(services.conversations().list(owner.id()).isEmpty());
        assertTrue(services.conversations().list(ben.id()).isEmpty());
    }

    @Test
    void aDirectConversationHasNoPeopleToChangeOrNameToGive() {
        var direct = services.conversations().openDirect(owner.id(), ben.uid());

        assertRefused(MessageRefusal.ADD_NOT_A_GROUP, () -> services.groups()
                .add(owner.id(), direct.uid(), List.of(cora.uid())));
        assertRefused(
                MessageRefusal.REMOVE_NOT_A_GROUP, () -> services.groups().remove(owner.id(), direct.uid(), ben.uid()));
        assertRefused(MessageRefusal.RENAME_NOT_A_GROUP, () -> services.groups().rename(owner.id(), direct.uid(), "X"));
    }

    @Test
    void addingOnlyPeopleAlreadyThereChangesNothing() {
        var group = group(null);

        services.groups().add(ben.id(), group.uid(), List.of(cora.uid(), owner.uid()));

        assertTrue(systemLines(group).isEmpty());
    }
}
