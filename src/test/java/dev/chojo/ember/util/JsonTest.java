/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    record Sample(String name, int count) {}

    private static final String NEWER = "{\"name\":\"a\",\"count\":null,\"addedLater\":true}";

    @Test
    void theDefaultMapperRefusesWhatItDoesNotKnow() {
        assertThrows(JacksonException.class, () -> Json.MAPPER.readValue(NEWER, Sample.class));
    }

    @Test
    void theLenientMapperSkipsUnknownFieldsAndMissingPrimitives() {
        assertEquals(new Sample("a", 0), Json.LENIENT.readValue(NEWER, Sample.class));
    }

    @Test
    void thePrettyMapperIndentsAndReadsLeniently() {
        assertTrue(Json.PRETTY.writeValueAsString(new Sample("a", 1)).contains("\n  \"name\""));
        assertEquals(new Sample("a", 0), Json.PRETTY.readValue(NEWER, Sample.class));
    }

    @Test
    void aTextThatIsNoDocumentIsABadRequest() {
        var refused = assertThrows(
                RefusalResponse.class, () -> Json.document("{", MemberRefusal.PROFILE_ANSWER_NOT_READABLE));

        assertEquals(MemberRefusal.PROFILE_ANSWER_NOT_READABLE, refused.refusal());
    }
}
