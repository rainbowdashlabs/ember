/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.handler;

import dev.chojo.ember.event.events.MemberAnswersChanged;
import dev.chojo.ember.event.events.StationAudienceChanged;
import dev.chojo.ember.feature.members.service.MemberRuleService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** The events that make a station's rules run again. */
class MemberRuleHandlersTest {

    @Test
    void peopleChangingAtAStationRunItsRules() {
        var rules = mock(MemberRuleService.class);
        var handler = new AudienceRuleHandler(() -> rules);
        assertEquals(StationAudienceChanged.class, handler.eventType());

        handler.handle(StationAudienceChanged.at(7));

        verify(rules).stationChanged(7);
    }

    /** An account-wide change names no station, so no rules run for it. */
    @Test
    void anAccountChangeRunsNoRules() {
        var rules = mock(MemberRuleService.class);

        new AudienceRuleHandler(() -> rules).handle(StationAudienceChanged.ofAccount(3));

        verifyNoInteractions(rules);
    }

    @Test
    void changedAnswersRunTheRulesOfTheirStation() {
        var rules = mock(MemberRuleService.class);
        var handler = new AnswersRuleHandler(() -> rules);
        assertEquals(MemberAnswersChanged.class, handler.eventType());

        handler.handle(new MemberAnswersChanged(5, 11));

        verify(rules).stationChanged(5);
    }
}
