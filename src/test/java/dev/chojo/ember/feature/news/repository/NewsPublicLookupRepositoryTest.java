/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.repository;

import dev.chojo.ember.feature.content.BlockReferenceTestBase;
import dev.chojo.ember.feature.content.entity.BlockAudience;
import dev.chojo.ember.feature.news.entity.News;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a news block may name and show: for the public, published entries of the station on its
 * public blog and kept to nobody in particular; for the station's members the same without the
 * blog. A draft, a restricted entry and one of another station are found by neither the lookup nor
 * the search.
 */
class NewsPublicLookupRepositoryTest extends BlockReferenceTestBase {

    private static List<Integer> ids(List<News> found) {
        return found.stream().map(News::id).toList();
    }

    @Test
    void thePublicFindsOnlyTheEntryOnThePublicBlog() {
        assertEquals(
                publicNews.id(),
                newsRepo.findOpenByUid(station.id(), BlockAudience.PUBLIC, publicNews.publicUid())
                        .orElseThrow()
                        .id());
        assertTrue(newsRepo.findOpenByUid(station.id(), BlockAudience.PUBLIC, internalNews.publicUid())
                .isEmpty());
    }

    @Test
    void membersFindTheInternalEntryToo() {
        assertTrue(newsRepo.findOpenByUid(station.id(), BlockAudience.MEMBERS, publicNews.publicUid())
                .isPresent());
        assertTrue(newsRepo.findOpenByUid(station.id(), BlockAudience.MEMBERS, internalNews.publicUid())
                .isPresent());
    }

    @Test
    void nobodyFindsWhatNotEveryReaderMayRead() {
        newsNobodyMayName().forEach((why, news) -> {
            for (var audience : BlockAudience.values()) {
                assertTrue(
                        newsRepo.findOpenByUid(station.id(), audience, news.publicUid())
                                .isEmpty(),
                        why + " for " + audience);
            }
        });
        assertTrue(newsRepo.findOpenByUid(station.id(), BlockAudience.MEMBERS, UUID.randomUUID())
                .isEmpty());
    }

    @Test
    void theSearchMatchesTheTitleWhateverItsCaseNewestFirst() {
        assertEquals(
                List.of(publicNews.id()),
                ids(newsRepo.findOpenEntries(station.id(), BlockAudience.PUBLIC, "DREHLEITER", 0, 10)));
        assertEquals(
                List.of(internalNews.id(), publicNews.id()),
                ids(newsRepo.findOpenEntries(station.id(), BlockAudience.MEMBERS, "drehleiter", 0, 10)));
    }

    @Test
    void theSearchLooksAtTheTitleOnly() {
        assertTrue(newsRepo.findOpenEntries(station.id(), BlockAudience.MEMBERS, "im Text", 0, 10)
                .isEmpty());
    }

    @Test
    void theSearchIsLimitedAndPaged() {
        assertEquals(
                List.of(internalNews.id()),
                ids(newsRepo.findOpenEntries(station.id(), BlockAudience.MEMBERS, null, 0, 1)));
        assertEquals(
                List.of(publicNews.id()),
                ids(newsRepo.findOpenEntries(station.id(), BlockAudience.MEMBERS, null, 1, 1)));
    }
}
