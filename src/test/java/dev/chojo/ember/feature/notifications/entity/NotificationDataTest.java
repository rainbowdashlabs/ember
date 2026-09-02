/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NotificationDataTest {

    @Test
    void serializeAndDeserializeNewNews() {
        var params = new NotificationParams.NewNews("Title", "Author", "Preview text");
        var data = NotificationData.of(params, new NotificationData.NotificationLink("news-list"));

        String json = data.toJson();
        var restored = NotificationData.fromJson(json, NotificationType.NEW_NEWS);

        assertInstanceOf(NotificationParams.NewNews.class, restored.params());
        var p = (NotificationParams.NewNews) restored.params();
        assertEquals("Title", p.title());
        assertEquals("Author", p.author());
        assertEquals("Preview text", p.preview());
        assertNotNull(restored.link());
        assertEquals("news-list", restored.link().route());
    }

    @Test
    void serializeAndDeserializeWithNullFields() {
        var params = new NotificationParams.ExchangeNewRequest("Member", "Inventory", null);
        var data = NotificationData.of(params);

        String json = data.toJson();
        var restored = NotificationData.fromJson(json, NotificationType.EXCHANGE_NEW_REQUEST);

        assertInstanceOf(NotificationParams.ExchangeNewRequest.class, restored.params());
        var p = (NotificationParams.ExchangeNewRequest) restored.params();
        assertEquals("Member", p.memberName());
        assertEquals("Inventory", p.inventoryName());
        assertNull(p.reason());
        assertNull(restored.link());
    }

    @Test
    void serializeWithLink() {
        var params = new NotificationParams.NewEvent("Event", "Description");
        var link = new NotificationData.NotificationLink("event-detail", Map.of("id", 42));
        var data = NotificationData.of(params, link);

        String json = data.toJson();
        var restored = NotificationData.fromJson(json, NotificationType.NEW_EVENT);

        assertNotNull(restored.link());
        assertEquals("event-detail", restored.link().route());
        assertEquals(42, restored.link().routeParams().get("id"));
    }

    /**
     * The query travels with the link, which is what carries a notification to one comment inside a
     * page.
     */
    @Test
    void serializeWithQuery() {
        var params = new NotificationParams.NewsComment("Article", "Author", "Preview");
        var data = NotificationData.of(params, NotificationLinks.comment(CommentEntityType.NEWS, 7, null, 42));

        var restored = NotificationData.fromJson(data.toJson(), NotificationType.NEWS_COMMENT);

        assertEquals("news-detail", restored.link().route());
        assertEquals(7, restored.link().routeParams().get("id"));
        assertEquals(42, restored.link().query().get("comment"));
    }

    /**
     * A link with no query is written exactly as it was before links could carry one, which is what
     * lets a withdrawal reach the notifications already stored.
     */
    @Test
    void aLinkWithoutAQueryIsWrittenWithoutTheKey() {
        var link = new NotificationData.NotificationLink("news-detail", Map.of("id", 7));

        assertEquals("{\"route\":\"news-detail\",\"routeParams\":{\"id\":7}}", link.toJson());
    }

    @Test
    void paramsAsMapConvertsToFlatMap() {
        var params = new NotificationParams.EventRegistrationStatus(
                "Event Name", RegistrationStatus.ACCEPTED, "Description");
        var data = NotificationData.of(params);

        Map<String, String> map = data.paramsAsMap();
        assertEquals("Event Name", map.get("eventName"));
        assertEquals("ACCEPTED", map.get("status"));
        assertEquals("Description", map.get("eventDescription"));
    }

    @Test
    void paramsAsMapOmitsNullValues() {
        var params = new NotificationParams.ExchangeStatusChange("An den Träger geschickt", "Helm", null);
        var data = NotificationData.of(params);

        Map<String, String> map = data.paramsAsMap();
        assertEquals("An den Träger geschickt", map.get("stepLabel"));
        assertEquals("Helm", map.get("inventoryName"));
        assertFalse(map.containsKey("nextActor"), "a chain that has ended waits on nobody");
    }

    @Test
    void paramsAsMapReturnsEmptyForNullParams() {
        var data = new NotificationData(null, null);
        assertTrue(data.paramsAsMap().isEmpty());
    }

    @Test
    void localeKeyDerivedFromType() {
        assertEquals("notification.newNews", NotificationType.NEW_NEWS.localeKey());
        assertEquals("notification.eventRegistrationStatus", NotificationType.EVENT_REGISTRATION_STATUS.localeKey());
        assertEquals("notification.lostAndFoundClaimed", NotificationType.LOST_AND_FOUND_CLAIMED.localeKey());
    }

    @Test
    void eachTypeHasUniqueLocaleKey() {
        var keys = new HashSet<String>();
        for (var type : NotificationType.values()) {
            assertTrue(keys.add(type.localeKey()), "Duplicate locale key: " + type.localeKey());
        }
    }

    @Test
    void eachTypeHasMatchingParamsClass() {
        for (var type : NotificationType.values()) {
            assertNotNull(type.paramsType(), type + " must have a paramsType");
            assertTrue(
                    NotificationParams.class.isAssignableFrom(type.paramsType()),
                    type + " paramsType must implement NotificationParams");
        }
    }

    @Test
    void roundTripAllTypes() {
        // Ensure every type can serialize and deserialize without error
        var testParams = Map.<NotificationType, NotificationParams>of(
                NotificationType.NEW_NEWS, new NotificationParams.NewNews("t", "a", "p"),
                NotificationType.NEWS_COMMENT, new NotificationParams.NewsComment("t", "a", "p"),
                NotificationType.NEW_EVENT, new NotificationParams.NewEvent("t", "d"),
                NotificationType.EVENT_REGISTRATION_STATUS,
                        new NotificationParams.EventRegistrationStatus("e", RegistrationStatus.ACCEPTED, "d"),
                NotificationType.EXCHANGE_NEW_REQUEST, new NotificationParams.ExchangeNewRequest("m", "i", "r"),
                NotificationType.EXCHANGE_STATUS_CHANGE,
                        new NotificationParams.ExchangeStatusChange("An den Träger geschickt", "i", StepActor.STATION),
                NotificationType.MEMBER_ADDED_TO_GROUP, new NotificationParams.MemberAddedToGroup("g", null),
                NotificationType.PROFILE_FIELD_CHANGED, new NotificationParams.ProfileFieldChanged("m", "f"),
                NotificationType.PROCUREMENT_REQUESTED, new NotificationParams.ProcurementRequested("i"),
                NotificationType.PROCUREMENT_FULFILLED, new NotificationParams.ProcurementFulfilled("i"));
        // Map.of only supports 10 entries, add remaining separately
        var remaining = Map.<NotificationType, NotificationParams>of(
                NotificationType.NEW_FORM, new NotificationParams.NewForm("t"),
                NotificationType.LOST_AND_FOUND_NEW, new NotificationParams.LostAndFoundNew("d"),
                NotificationType.LOST_AND_FOUND_CLAIMED, new NotificationParams.LostAndFoundClaimed("n", "d"),
                NotificationType.LENDING_NEW_REQUEST, new NotificationParams.LendingNewRequest("s", "items"),
                NotificationType.LENDING_STATUS_CHANGE,
                        new NotificationParams.LendingStatusChange("s", LendingStatus.APPROVED),
                NotificationType.LENDING_NEW_MESSAGE, new NotificationParams.LendingNewMessage("s", "sender"));

        for (var entry : testParams.entrySet()) {
            var data = NotificationData.of(entry.getValue());
            String json = data.toJson();
            var restored = NotificationData.fromJson(json, entry.getKey());
            assertNotNull(restored.params(), "Failed to deserialize params for " + entry.getKey());
            assertEquals(entry.getKey().paramsType(), restored.params().getClass());
        }
        for (var entry : remaining.entrySet()) {
            var data = NotificationData.of(entry.getValue());
            String json = data.toJson();
            var restored = NotificationData.fromJson(json, entry.getKey());
            assertNotNull(restored.params(), "Failed to deserialize params for " + entry.getKey());
            assertEquals(entry.getKey().paramsType(), restored.params().getClass());
        }
    }
}
