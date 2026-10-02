/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.render;

import dev.chojo.ember.feature.board.service.BoardFeedDetails;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.comment.service.CommentFeedDetails;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFeedDetails;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.federation.service.LendingFeedDetails;
import dev.chojo.ember.feature.federation.service.LendingService;
import dev.chojo.ember.feature.form.service.FormFeedDetails;
import dev.chojo.ember.feature.inventory.service.InventoryFeedDetails;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundFeedDetails;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundService;
import dev.chojo.ember.feature.members.service.MemberFeedDetails;
import dev.chojo.ember.feature.news.service.NewsFeedDetails;
import dev.chojo.ember.feature.procedure.service.ProcedureFeedDetails;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import dev.chojo.ember.feature.storage.service.StorageFeedDetails;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.feature.waitinglist.service.WaitingListFeedDetails;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Every feature's feed details, built over the services a test hands in, in the order the module
 * binds them.
 */
final class FeedContributors {

    private FeedContributors() {}

    static Set<FeedDetailsContributor> all(
            EventCrudService crudService,
            EventFieldService eventFieldService,
            OccurrenceCalendar occurrenceCalendar,
            LostAndFoundService lostAndFoundService,
            LendingService lendingService,
            StorageQuotaService storageQuotaService,
            InventoryService inventoryService,
            BoardTicketService boardTicketService,
            ProcedureService procedureService) {
        return new LinkedHashSet<>(List.of(
                new NewsFeedDetails(),
                new CommentFeedDetails(),
                new EventFeedDetails(crudService, eventFieldService, occurrenceCalendar),
                new MemberFeedDetails(),
                new InventoryFeedDetails(inventoryService),
                new LostAndFoundFeedDetails(lostAndFoundService),
                new LendingFeedDetails(lendingService),
                new BoardFeedDetails(boardTicketService),
                new StorageFeedDetails(storageQuotaService),
                new WaitingListFeedDetails(),
                new FormFeedDetails(),
                new ProcedureFeedDetails(procedureService)));
    }
}
