/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The text rules of messages and how a message reads as plain text. */
class MessageTextsTest {

    @Test
    void mentionsReadAsNamesInPlainText() {
        assertEquals(
                "Hallo @Ben und @Cora $1", MessageTexts.plain("Hallo @[account/1111:Ben] und @[account/2222:Cora] $1"));
    }

    @Test
    void aLongTextIsShortenedWithAnEllipsis() {
        assertEquals("abc", MessageTexts.shortened("abc", 3));
        assertEquals("ab…", MessageTexts.shortened("ab cd", 3));
    }

    @Test
    void aBlankGroupNameCallsTheGroupAfterItsMembers() {
        assertNull(MessageTexts.groupName(null));
        assertNull(MessageTexts.groupName("  "));
        assertEquals("Jugend", MessageTexts.groupName(" Jugend "));
    }

    @Test
    void emojiAreRecognised() {
        assertTrue(MessageEmoji.isStandard("❤️"));
        assertTrue(MessageEmoji.isStandard("©️"));
        assertFalse(MessageEmoji.isStandard(null));
        assertFalse(MessageEmoji.isStandard("#"));
        assertFalse(MessageEmoji.isStandard("👍".repeat(20)));
        assertFalse(MessageEmoji.isStandard("👍".repeat(9)));
    }
}
