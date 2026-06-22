/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed;

import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedFingerprintTest {

    @Test
    void identicalInputsProduceIdenticalFingerprint() {
        var ts = Instant.parse("2026-06-12T10:00:00Z");
        var a = FeedFingerprint.compute(ts, "rss", 42, "de", true);
        var b = FeedFingerprint.compute(ts, "rss", 42, "de", true);
        assertEquals(a.etag(), b.etag());
    }

    @Test
    void anyDifferingInputChangesFingerprint() {
        var ts = Instant.parse("2026-06-12T10:00:00Z");
        var base = FeedFingerprint.compute(ts, "rss", 42, "de", true);
        assertNotEquals(
                base.etag(), FeedFingerprint.compute(ts, "rss", 42, "de", false).etag(), "verbose");
        assertNotEquals(
                base.etag(), FeedFingerprint.compute(ts, "rss", 42, "en", true).etag(), "locale");
        assertNotEquals(
                base.etag(), FeedFingerprint.compute(ts, "rss", 43, "de", true).etag(), "id");
        assertNotEquals(
                base.etag(), FeedFingerprint.compute(ts, "atom", 42, "de", true).etag(), "feed type");
        assertNotEquals(
                base.etag(),
                FeedFingerprint.compute(ts.plusSeconds(1), "rss", 42, "de", true)
                        .etag(),
                "timestamp");
    }

    @Test
    void emitsQuotedShortHash() {
        var fp = FeedFingerprint.compute(Instant.EPOCH, "x");
        assertTrue(fp.etag().startsWith("\""));
        assertTrue(fp.etag().endsWith("\""));
        // 16 hex chars inside the quotes (8 bytes truncated SHA-256)
        assertEquals(18, fp.etag().length());
    }

    @Test
    void handleConditionalReturns304WhenIfNoneMatchMatches() {
        var ctx = mock(Context.class);
        var fp = FeedFingerprint.compute(Instant.EPOCH, "x");
        when(ctx.header("If-None-Match")).thenReturn(fp.etag());
        when(ctx.header("If-Modified-Since")).thenReturn(null);

        assertTrue(FeedFingerprint.handleConditional(ctx, fp));
        verify(ctx).status(304);
        verify(ctx).header("ETag", fp.etag());
        verify(ctx).header("Vary", "If-None-Match, If-Modified-Since");
    }

    @Test
    void handleConditionalReturns304WhenIfModifiedSinceCoversLastModified() {
        var ctx = mock(Context.class);
        var lastModified = Instant.parse("2026-06-12T10:00:00Z");
        var fp = FeedFingerprint.compute(lastModified, "x");
        // Client says "I saw it after the fingerprint last changed" → 304.
        when(ctx.header("If-None-Match")).thenReturn(null);
        when(ctx.header("If-Modified-Since")).thenReturn("Fri, 12 Jun 2026 11:00:00 GMT");

        assertTrue(FeedFingerprint.handleConditional(ctx, fp));
        verify(ctx).status(304);
    }

    @Test
    void handleConditionalSetsHeadersAndReturnsFalseOnMiss() {
        var ctx = mock(Context.class);
        var fp = FeedFingerprint.compute(Instant.EPOCH, "x");
        when(ctx.header(anyString())).thenReturn(null);

        assertFalse(FeedFingerprint.handleConditional(ctx, fp));
        verify(ctx, never()).status(anyInt());
        verify(ctx).header("ETag", fp.etag());
        verify(ctx).header("Vary", "If-None-Match, If-Modified-Since");
        verify(ctx).header(eq("Last-Modified"), any(String.class));
    }

    private static String eq(String s) {
        return ArgumentMatchers.eq(s);
    }
}
