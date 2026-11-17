/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.members.entity.RuleOwner;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The demo station's tag kept by a rule: everybody whose JuLeiCa runs out within ten months. */
class DemoRuleKeptTagTest extends DemoTestBase {
    private static DemoRunContext run;

    @BeforeAll
    static void seed() {
        run = seedDemoStations();
    }

    private static UserTag juleicaTag(int stationId) {
        return userTagRepo.findByStation(stationId).stream()
                .filter(tag -> tag.name().equals("JuLeiCa läuft bald ab"))
                .findFirst()
                .orElseThrow();
    }

    /** The tag takes its members from a rule, and they are exactly whom the rule matches today. */
    @Test
    void theTagHoldsWhomItsRuleMatches() {
        int stationId = run.primaryStation().stationId();
        var owner = RuleOwner.tag(juleicaTag(stationId).id());

        var rule = memberRuleRepo.find(owner).orElseThrow();
        var members = userTagRepo.findMembers(owner.id()).stream()
                .map(StationMember::id)
                .sorted()
                .toList();

        assertEquals(memberRuleRepo.matching(stationId, rule, LocalDate.now(), List.of()), members);
        assertFalse(members.isEmpty());
        assertTrue(memberRuleRepo.hasRule(owner));
    }
}
