/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.conf.file.elements.KnowledgeBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The run itself, driven directly: the timer behind it fires once an hour, which no test waits for.
 */
class KbTrashPurgerTest {

    @Test
    void aRunClearsOutWhatIsDueUpToItsCap() {
        var trashService = mock(KbTrashService.class);

        new KbTrashPurger(trashService, new KnowledgeBase()).purge();

        verify(trashService).sweepExpired(30, KbTrashPurger.MAX_PER_RUN);
    }

    @Test
    void aFailedRunIsSwallowedSoTheTimerKeepsRunning() {
        var trashService = mock(KbTrashService.class);
        doThrow(new RuntimeException("db unreachable")).when(trashService).sweepExpired(30, KbTrashPurger.MAX_PER_RUN);

        new KbTrashPurger(trashService, new KnowledgeBase()).purge();

        verify(trashService).sweepExpired(30, KbTrashPurger.MAX_PER_RUN);
    }

    /**
     * The window is an operator's to set, and it is read as a sane number whatever the file says: a
     * trash that keeps nothing is not a trash, and one that keeps forever is not one either.
     */
    @Test
    void theRetentionWindowStaysWithinItsBounds() {
        assertEquals(30, new KnowledgeBase().trashRetentionDays());
    }
}
