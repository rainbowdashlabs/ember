/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentOrigin;
import dev.chojo.ember.feature.comment.entity.CreatedAudience;
import dev.chojo.ember.feature.comment.entity.Moderation;
import dev.chojo.ember.feature.comment.entity.TargetInfo;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Comments on a knowledge base file belong to the station owning the file, are removed by others
 * only with the knowledge manager right, and tell the thread only when written here.
 */
class KbCommentTargetTest {
    private static final int STATION = 4;
    private static final int FILE = 30;
    private static final TargetInfo TARGET = TargetInfo.of(CommentEntityType.KB, FILE, STATION, "Handbuch");

    private KbCommentTarget target;

    @BeforeEach
    void setup() {
        var files = mock(KnowledgeBaseRepository.class);
        var file = mock(KbFile.class);
        when(file.id()).thenReturn(FILE);
        when(file.stationId()).thenReturn(STATION);
        when(file.name()).thenReturn("Handbuch");
        when(files.findFileById(FILE)).thenReturn(Optional.of(file));
        target = new KbCommentTarget(files);
    }

    private static StationSession memberAt(int stationId, StationPermission... permissions) {
        return StationSession.of(TestSessions.member(stationId, permissions));
    }

    @Test
    void aFileIsFoundWithItsStationAndName() {
        assertEquals(Optional.of(TARGET), target.find(FILE));
        assertTrue(target.find(FILE + 1).isEmpty());
        assertEquals(Refusal.NOT_HERE_OR_NOT_YOURS, target.missing());
    }

    @Test
    void onlyTheOwningStationReadsAndWrites() {
        var own = memberAt(STATION);
        var foreign = memberAt(STATION + 1);

        assertDoesNotThrow(() -> target.requireReadable(own, TARGET));
        assertDoesNotThrow(() -> target.requireWritable(own, TARGET));
        assertEquals(
                Refusal.NOT_HERE_OR_NOT_YOURS,
                assertThrows(RefusalResponse.class, () -> target.requireReadable(foreign, TARGET))
                        .refusal());
        assertEquals(
                Refusal.NOT_HERE_OR_NOT_YOURS,
                assertThrows(RefusalResponse.class, () -> target.requireWritable(foreign, TARGET))
                        .refusal());
    }

    @Test
    void aKnowledgeManagerRemovesButNeverRewritesAnotherMembersComment() {
        var manager = memberAt(STATION, StationPermission.KNOWLEDGE_MANAGER);

        assertTrue(target.mayModerate(manager, TARGET, Moderation.DELETE));
        assertFalse(target.mayModerate(manager, TARGET, Moderation.EDIT));
        assertFalse(target.mayModerate(memberAt(STATION), TARGET, Moderation.DELETE));
    }

    @Test
    void aCommentWrittenHereTellsTheThreadAndOneFromAPartnerNobody() {
        assertEquals(CreatedAudience.THREAD, target.audienceFor(TARGET, CommentOrigin.LOCAL));
        assertEquals(CreatedAudience.NOBODY, target.audienceFor(TARGET, CommentOrigin.PARTNER));
    }

    @Test
    void aNotificationOpensTheFileAtTheComment() {
        assertEquals(NotificationLinks.comment(NotificationLinks.kbFile(FILE), 7), target.link(TARGET, 7));
    }
}
