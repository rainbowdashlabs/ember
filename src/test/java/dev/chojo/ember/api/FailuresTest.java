/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.http.HttpStatus;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailuresTest {

    @Test
    void anythingNobodyNamedIsAFaultAndSaysSo() {
        var refusal = Failures.describe(new IllegalStateException("the widget fell over"));

        assertEquals(Refusal.UNEXPECTED_FAULT, refusal);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, refusal.status());
        assertFalse(refusal.message().isBlank());
    }

    @Test
    void aSecondRowWithTheSameDetailsIsTheSendersProblemAndAConflict() {
        var refusal = Failures.describe(wrapped("23505"));

        assertEquals(Refusal.ALREADY_EXISTS, refusal);
        assertEquals(HttpStatus.CONFLICT, refusal.status());
    }

    @Test
    void aRowNamingSomethingGoneIsAConflictRatherThanAFault() {
        assertEquals(Refusal.STILL_LINKED, Failures.describe(wrapped("23503")));
    }

    @Test
    void aValueTooBigForItsColumnIsTheSendersProblem() {
        assertEquals(Refusal.DOES_NOT_FIT, Failures.describe(wrapped("22001")));
        assertEquals(HttpStatus.BAD_REQUEST, Refusal.DOES_NOT_FIT.status());
    }

    @Test
    void twoChangesAtOnceAskTheSenderToTryAgain() {
        assertEquals(Refusal.CHANGE_COLLIDED, Failures.describe(wrapped("40001")));
        assertEquals(Refusal.CHANGE_COLLIDED, Failures.describe(wrapped("40P01")));
    }

    @Test
    void aDatabaseOutOfReachIsNotAFaultButAWait() {
        assertEquals(Refusal.STORE_UNREACHABLE, Failures.describe(wrapped("08006")));
        assertEquals(Refusal.STORE_UNREACHABLE, Failures.describe(wrapped("53300")));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, Refusal.STORE_UNREACHABLE.status());
    }

    @Test
    void aDatabaseStateNobodyMappedIsStillAFault() {
        assertEquals(Refusal.UNEXPECTED_FAULT_FROM_UNKNOWN_STATE, Failures.describe(wrapped("XX000")));
    }

    @Test
    void aCauseChainThatLoopsDoesNotHangTheHandler() {
        var first = new IllegalStateException("first");
        var second = new IllegalStateException("second", first);
        first.initCause(second);

        assertEquals(Refusal.UNEXPECTED_FAULT, Failures.describe(second));
    }

    @Test
    void aSentenceSomebodyWroteIsPassedOn() {
        assertEquals(
                "A form needs a name", Failures.readable("A form needs a name").orElseThrow());
        assertEquals(
                "Invalid UUID string: abc",
                Failures.readable("Invalid UUID string: abc").orElseThrow());
    }

    @Test
    void aClassNameNeverReachesTheReader() {
        assertTrue(Failures.readable("No enum constant dev.chojo.ember.feature.form.FormPurpose.X")
                .isEmpty());
        assertTrue(Failures.readable("dev.chojo.ember.api.ApiServer cannot be cast")
                .isEmpty());
        assertTrue(Failures.readable("tools.jackson.databind.JsonNode").isEmpty());
    }

    @Test
    void aStackTraceNeverReachesTheReader() {
        assertTrue(Failures.readable("boom\n\tat dev.chojo.ember.Main.run(Main.java:12)")
                .isEmpty());
    }

    @Test
    void aStatementFragmentNeverReachesTheReader() {
        assertTrue(Failures.readable("ERROR: duplicate key value violates unique constraint")
                .isEmpty());
        assertTrue(Failures.readable("INSERT INTO station_member failed").isEmpty());
    }

    @Test
    void aFilePathNeverReachesTheReader() {
        assertTrue(Failures.readable("/var/lib/ember/storage is not writable").isEmpty());
        assertTrue(Failures.readable("C:\\ember\\data").isEmpty());
    }

    @Test
    void anObjectHandleNeverReachesTheReader() {
        assertTrue(Failures.readable("cannot read Station@1f3a8c9d").isEmpty());
    }

    @Test
    void anEssayIsNotASentenceAndIsDropped() {
        assertTrue(Failures.readable("word ".repeat(80)).isEmpty());
    }

    @Test
    void nothingAtAllIsDroppedRatherThanShown() {
        assertTrue(Failures.readable(null).isEmpty());
        assertTrue(Failures.readable("   ").isEmpty());
    }

    @Test
    void aReferenceIsShortEnoughToReadOut() {
        String reference = Failures.reference();

        assertEquals(8, reference.length());
        assertTrue(reference.matches("[0-9a-f]{8}"));
    }

    /**
     * Wraps a database refusal the way the query layer does, so the walk up the cause chain is what
     * is being tested rather than a bare exception.
     */
    private static Throwable wrapped(String sqlState) {
        return new RuntimeException(
                "query failed", new RuntimeException("statement failed", new SQLException("refused", sqlState)));
    }
}
