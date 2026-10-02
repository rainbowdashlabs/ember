/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.TestProtocolRefusal;
import dev.chojo.ember.feature.protocol.entity.TestProtocol;
import dev.chojo.ember.feature.protocol.entity.TestProtocolItem;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRun;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunCheck;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunMember;
import dev.chojo.ember.feature.protocol.entity.TestProtocolSection;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestProtocolEvaluationServiceTest {
    static final LocalDate DAY = LocalDate.of(2026, 9, 1);
    static final TestProtocolRun RUN =
            new TestProtocolRun(5, 1, 3, "Herbst", DAY, TestProtocolRun.RunStatus.values()[0], 11, null);

    private final TestProtocolService protocols = mock(TestProtocolService.class);
    private final TestProtocolEvaluationService service = new TestProtocolEvaluationService(protocols);

    @Test
    void eachMemberScoresThePointsOfTheItemsCheckedForThemPerSection() {
        when(protocols.findProtocol(1)).thenReturn(Optional.of(new TestProtocol(1, 3, "Knoten", "", 70, null, null)));
        when(protocols.findSections(1))
                .thenReturn(List.of(
                        new TestProtocolSection(10, 1, null, "A", "", null, null, 0),
                        new TestProtocolSection(20, 1, null, "B", "", null, null, 1)));
        when(protocols.findAllItemsByProtocol(1))
                .thenReturn(List.of(
                        new TestProtocolItem(100, 10, "a1", "", 2.0, 0),
                        new TestProtocolItem(101, 10, "a2", "", 3.0, 1),
                        new TestProtocolItem(200, 20, "b1", "", 1.5, 0)));
        when(protocols.findRunMembers(5))
                .thenReturn(List.of(new TestProtocolRunMember(1, 5, 11, null, null, true, 3.5)));
        when(protocols.findChecks(5, 11))
                .thenReturn(List.of(
                        new TestProtocolRunCheck(1, 101, true, null, null),
                        new TestProtocolRunCheck(1, 100, false, null, null),
                        new TestProtocolRunCheck(1, 200, true, null, null)));

        var evaluation = service.evaluate(RUN);

        assertEquals("Knoten", evaluation.protocolName());
        assertEquals(DAY, evaluation.testDate());
        assertEquals(70, evaluation.passThreshold());
        assertEquals(Map.of(10, 5.0, 20, 1.5), evaluation.sectionMaxPoints());
        assertEquals(Map.of(10, 3.0, 20, 1.5), evaluation.members().getFirst().sectionScores());
        assertEquals(3.5, evaluation.members().getFirst().totalScore());
    }

    @Test
    void aRunWhoseProtocolIsGoneCannotBeEvaluated() {
        when(protocols.findProtocol(1)).thenReturn(Optional.empty());

        var refused = assertThrows(RefusalResponse.class, () -> service.evaluate(RUN));

        assertEquals(TestProtocolRefusal.PROTOCOL_NOT_HERE_BEHIND_RUN_TO_EVALUATE, refused.refusal());
    }
}
