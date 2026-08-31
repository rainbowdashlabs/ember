/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.SelfCheck;
import dev.chojo.ember.feature.inventory.entity.SelfCheckAnswer;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRaisedKind;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRow;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.repository.SelfCheckRepository;
import dev.chojo.ember.feature.inventory.service.ItemCustodyService;
import dev.chojo.ember.feature.members.entity.StationMember;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;

/**
 * Three members asked to answer for their own gear, each at a different point of the cycle.
 *
 * <p>One task has just gone out and nobody has touched it, one is waiting to be read, and one has
 * come back to its member with a single answer refused. Between them they show every state the
 * screens have to draw, so the feature can be walked through without anybody first building the
 * world by hand.
 */
@Singleton
public class DemoSelfCheckSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoSelfCheckSeeder.class);

    private final SelfCheckRepository repository;
    private final InventoryRepository inventoryRepository;
    private final ItemCustodyService custodyService;

    @Inject
    public DemoSelfCheckSeeder(
            SelfCheckRepository repository,
            InventoryRepository inventoryRepository,
            ItemCustodyService custodyService) {
        this.repository = repository;
        this.inventoryRepository = inventoryRepository;
        this.custodyService = custodyService;
    }

    /**
     * After the gear has been handed out, because every answer here points at a piece somebody holds.
     */
    @Override
    public int order() {
        return FEDERATED_MODULES;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        var members = station.members();
        List<StationMember> asked = members.anfaenger();
        if (asked.size() < 3) return;
        StationMember checker = station.adminMember();

        untouched(station.stationId(), asked.get(0), checker);
        waitingToBeRead(station.stationId(), asked.get(1), checker);
        partlySentBack(station.stationId(), asked.get(2), checker);
        log.info("Demo: Created self-check data");
    }

    /**
     * A task that went out this week and that nobody has opened yet.
     */
    private void untouched(int stationId, StationMember member, StationMember checker) {
        repository.create(stationId, member.id(), checker.id(), LocalDate.now().plusWeeks(4));
    }

    /**
     * A task the member has answered and handed in, with a loss raised beside it: the loss took
     * effect the moment it was given and waits for nobody, which is exactly what the reviewer's
     * screen has to be able to show.
     */
    private void waitingToBeRead(int stationId, StationMember member, StationMember checker) {
        SelfCheck task = repository.create(
                stationId, member.id(), checker.id(), LocalDate.now().plusWeeks(2));
        List<InventoryItem> gear = ownGear(member);
        if (gear.size() < 2) return;
        InventoryItem missing = gear.getLast();
        custodyService.markLost(missing.id(), "Nach dem Zeltlager nicht mehr aufgetaucht", member.id());
        repository.recordRaised(task.id(), SelfCheckRaisedKind.LOSS, missing.id(), null, member.id());

        for (InventoryItem item : gear) {
            if (item.id() == missing.id()) continue;
            repository.answerForItem(
                    task.id(), item.id(), item.inventoryId(), SelfCheckAnswer.HAVE_IT, "", null, member.id());
        }
        InventoryItem odd = gear.getFirst();
        repository.answerForItem(
                task.id(),
                odd.id(),
                odd.inventoryId(),
                SelfCheckAnswer.WRONG_RECORD,
                "Auf meinem steht eine andere Nummer",
                null,
                member.id());
        repository.submit(task.id(), member.id());
    }

    /**
     * The pieces the station itself owns and this member holds, which are the ones every answer in
     * this seeder is about.
     */
    private List<InventoryItem> ownGear(StationMember member) {
        return inventoryRepository.findItemsByMember(member.id()).stream()
                .filter(item -> !item.borrowed())
                .filter(item -> item.custody() != ItemCustody.LOST)
                .toList();
    }

    /**
     * A task that was handed in, read, and sent back holding one answer.
     *
     * <p>What was taken is gone from it, because nothing that has been settled is asked a second
     * time, and what came back carries the reviewer's words.
     */
    private void partlySentBack(int stationId, StationMember member, StationMember checker) {
        SelfCheck task = repository.create(
                stationId, member.id(), checker.id(), LocalDate.now().minusDays(3));
        List<InventoryItem> gear = ownGear(member);
        if (gear.size() < 2) return;
        InventoryItem taken = gear.get(0);
        InventoryItem sentBack = gear.get(1);
        SelfCheckRow settled = repository.answerForItem(
                task.id(), taken.id(), taken.inventoryId(), SelfCheckAnswer.HAVE_IT, "", null, member.id());
        SelfCheckRow refused = repository.answerForItem(
                task.id(),
                sentBack.id(),
                sentBack.inventoryId(),
                SelfCheckAnswer.HAVE_IT,
                "Liegt bei mir im Spind",
                null,
                member.id());
        repository.submit(task.id(), member.id());
        repository.take(settled.id(), checker.id());
        repository.refuse(
                refused.id(),
                "Das Teil ist laut unseren Unterlagen seit dem Frühjahr im Lager. Bitte noch einmal nachsehen.",
                checker.id());
        repository.reopen(task.id());
        repository.deleteSettledRows(task.id());
    }
}
