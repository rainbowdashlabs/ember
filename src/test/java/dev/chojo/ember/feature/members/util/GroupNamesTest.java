/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.util;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupNamesTest {
    private static String require(String name, String current, List<String> others) {
        return GroupNames.require(
                name,
                current,
                others,
                MemberRefusal.GROUP_NAME_MISSING_ON_CREATE,
                MemberRefusal.GROUP_NAME_TAKEN_ON_CREATE);
    }

    @Test
    void aNameIsStoredTrimmed() {
        assertEquals("Jugend", require("  Jugend ", null, List.of("Aktive")));
    }

    @Test
    void aBlankNameIsRefusedWithTheOwnersRefusal() {
        var refused = assertThrows(RefusalResponse.class, () -> require("   ", null, List.of()));
        assertEquals(MemberRefusal.GROUP_NAME_MISSING_ON_CREATE, refused.refusal());
        var missing = assertThrows(RefusalResponse.class, () -> require(null, null, List.of()));
        assertEquals(MemberRefusal.GROUP_NAME_MISSING_ON_CREATE, missing.refusal());
    }

    @Test
    void aNameAnotherGroupHasIsTakenWhateverTheCase() {
        var refused = assertThrows(RefusalResponse.class, () -> require(" jugend", null, List.of("Jugend ")));
        assertEquals(MemberRefusal.GROUP_NAME_TAKEN_ON_CREATE, refused.refusal());
        assertTrue(refused.getMessage().endsWith(": jugend"), refused.getMessage());
    }

    @Test
    void aGroupKeepingItsNameIsNeverRefusedForIt() {
        assertEquals("jugend", require("jugend", "jugend", List.of("Jugend")));
        assertEquals("JUGEND", require("JUGEND", "Jugend", List.of("Aktive")));
    }
}
