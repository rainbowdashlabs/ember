/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SizeParserTest {

    @Test
    void parsePlainBytes() {
        assertEquals(1024, SizeParser.parseBytes("1024"));
    }

    @Test
    void parseKilobytes() {
        assertEquals(1024, SizeParser.parseBytes("1K"));
        assertEquals(5120, SizeParser.parseBytes("5K"));
    }

    @Test
    void parseMegabytes() {
        assertEquals(50 * 1024 * 1024L, SizeParser.parseBytes("50M"));
        assertEquals(1024 * 1024L, SizeParser.parseBytes("1M"));
    }

    @Test
    void parseGigabytes() {
        assertEquals(5L * 1024 * 1024 * 1024, SizeParser.parseBytes("5G"));
        assertEquals(1024L * 1024 * 1024, SizeParser.parseBytes("1G"));
    }

    @Test
    void parseTerabytes() {
        assertEquals(1024L * 1024 * 1024 * 1024, SizeParser.parseBytes("1T"));
    }

    @Test
    void parseCaseInsensitive() {
        assertEquals(SizeParser.parseBytes("5G"), SizeParser.parseBytes("5g"));
        assertEquals(SizeParser.parseBytes("50M"), SizeParser.parseBytes("50m"));
    }

    @Test
    void parseFractional() {
        assertEquals((long) (1.5 * 1024 * 1024 * 1024), SizeParser.parseBytes("1.5G"));
    }

    @Test
    void parseWithSpace() {
        assertEquals(5L * 1024 * 1024 * 1024, SizeParser.parseBytes("5 G"));
    }

    @Test
    void parseBlankThrows() {
        assertThrows(IllegalArgumentException.class, () -> SizeParser.parseBytes(""));
        assertThrows(IllegalArgumentException.class, () -> SizeParser.parseBytes(null));
    }

    @Test
    void parseInvalidThrows() {
        assertThrows(IllegalArgumentException.class, () -> SizeParser.parseBytes("abc"));
        assertThrows(IllegalArgumentException.class, () -> SizeParser.parseBytes("5X"));
    }

    @Test
    void formatBytes() {
        assertEquals("0 B", SizeParser.formatBytes(0));
        assertEquals("512 B", SizeParser.formatBytes(512));
        assertEquals("1.0 KiB", SizeParser.formatBytes(1024));
        assertEquals("1.0 MiB", SizeParser.formatBytes(1024 * 1024));
        assertEquals("1.5 GiB", SizeParser.formatBytes((long) (1.5 * 1024 * 1024 * 1024)));
        assertEquals("1.0 TiB", SizeParser.formatBytes(1024L * 1024 * 1024 * 1024));
    }
}
