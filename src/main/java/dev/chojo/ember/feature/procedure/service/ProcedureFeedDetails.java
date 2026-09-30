/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.service;

import dev.chojo.ember.feature.feed.render.FeedDetails;
import dev.chojo.ember.feature.feed.render.FeedDetailsContributor;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.procedure.entity.ProcedureItem;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * What a feed entry says about a procedure: its name, who acted on it, and from the procedure itself
 * how far along it is.
 */
@Singleton
public class ProcedureFeedDetails implements FeedDetailsContributor {
    private final ProcedureService procedureService;

    @Inject
    public ProcedureFeedDetails(ProcedureService procedureService) {
        this.procedureService = procedureService;
    }

    @Override
    public void contribute(NotificationParams params, Notification notification, FeedDetails details) {
        switch (params) {
            case NotificationParams.ProcedureAssigned(String procedureName, String assignedByName) -> {
                details.putIfPresent(details.label("procedure", "Procedure"), procedureName);
                details.putIfPresent(details.label("by"), assignedByName);
                addProgress(details);
            }
            case NotificationParams.ProcedureResolvedParams(String procedureName) -> {
                details.putIfPresent(details.label("procedure", "Procedure"), procedureName);
                addProgress(details);
            }
            case NotificationParams.ProcedureReopenedParams(String procedureName) -> {
                details.putIfPresent(details.label("procedure", "Procedure"), procedureName);
                addProgress(details);
            }
            case NotificationParams.ProcedureItemCheckedParams(
                    String procedureName,
                    String itemTitle,
                    String checkedByName) -> {
                details.putIfPresent(details.label("procedure", "Procedure"), procedureName);
                details.putIfPresent(details.label("item"), itemTitle);
                details.putIfPresent(details.label("by"), checkedByName);
                addProgress(details);
            }
            default -> {}
        }
    }

    @Override
    public String author(NotificationParams params) {
        return switch (params) {
            case NotificationParams.ProcedureAssigned p -> p.assignedByName();
            case NotificationParams.ProcedureItemCheckedParams p -> p.checkedByName();
            default -> null;
        };
    }

    /**
     * Adds how many of the linked procedure's items are checked, as one row. A procedure without items
     * has no progress to report, and a missing link or a failed lookup adds nothing either.
     */
    private void addProgress(FeedDetails details) {
        Integer procedureId = details.linkId();
        if (procedureId == null) return;
        try {
            var items = procedureService.findItems(procedureId);
            if (items.isEmpty()) return;
            long checked = items.stream().filter(ProcedureItem::checked).count();
            String value = details.localized(
                    "feedLabel",
                    "progressFormat",
                    Map.of("checked", String.valueOf(checked), "total", String.valueOf(items.size())));
            details.put(details.label("progress", "Progress"), value);
        } catch (Exception ignored) {
        }
    }
}
