/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.messages.service.MessagePushes;
import dev.chojo.ember.feature.notifications.entity.Recipient;
import dev.chojo.ember.feature.notifications.repository.NotificationRepository;
import dev.chojo.ember.feature.notifications.service.NotificationText;
import dev.chojo.ember.feature.notifications.service.NotificationVisibility;
import dev.chojo.ember.feature.push.repository.PushOutboxRepository;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;

/**
 * What a queued push says, written when it goes out rather than when it was queued, so it is in the
 * language and with the text that hold at that moment.
 */
@Singleton
public class PushMessages {
    private static final String SECTION = "push";

    private final NotificationText text;
    private final ApplicationSettingRepository settings;
    private final NotificationRepository notifications;
    private final NotificationVisibility visibility;
    private final MessagePushes messagePushes;
    private final Api api;

    @Inject
    public PushMessages(
            NotificationText text,
            ApplicationSettingRepository settings,
            NotificationRepository notifications,
            NotificationVisibility visibility,
            MessagePushes messagePushes,
            Api api) {
        this.text = text;
        this.settings = settings;
        this.notifications = notifications;
        this.visibility = visibility;
        this.messagePushes = messagePushes;
        this.api = api;
    }

    /**
     * The push a queued row stands for.
     *
     * @param readerAccountId the account whose device it goes to, which a message push is written for
     * @return the push, empty when what it was about is gone and there is nothing left to say
     */
    public Optional<PushMessage> render(PushOutboxRepository.Due due, int readerAccountId) {
        if ("TEST".equals(due.kind())) {
            String locale = instanceLocale();
            return Optional.of(new PushMessage(
                    text.resolveLocalized(locale, SECTION, "testTitle", null),
                    text.resolveLocalized(locale, SECTION, "testBody", null),
                    api.baseUrl(),
                    "test",
                    PushMessage.DEFAULT_TTL));
        }
        if ("MESSAGE".equals(due.kind()) && due.messageId() != null) {
            return messagePushes
                    .forReader(due.messageId(), readerAccountId)
                    .map(found -> new PushMessage(
                            found.title(), found.body(), found.url(), found.topicKey(), PushMessage.DEFAULT_TTL));
        }
        if (due.notificationId() == null) return Optional.empty();
        return notifications
                .findUnreadForPush(due.notificationId())
                .filter(this::shown)
                .map(this::notification);
    }

    /**
     * Whether the reader's inbox shows the notification. One a switched-off module hides there, or a
     * mention on such a module's page, is kept but never shown, so it is not pushed either.
     */
    private boolean shown(NotificationRepository.PushNotification found) {
        var notification = found.notification();
        var reader = notification.memberId() != null
                ? Recipient.stationMember(notification.memberId())
                : Recipient.clusterMember(notification.clusterMemberId());
        return !visibility
                .hiddenFor(reader)
                .hides(notification.type(), notification.data().link());
    }

    /**
     * A notification as the inbox writes it: its kind as the heading, its full text as the body,
     * and a tap that opens what it is about and marks it read. Pushes about the same thing replace
     * each other on the device.
     */
    private PushMessage notification(NotificationRepository.PushNotification found) {
        var notification = found.notification();
        String locale = text.resolveLocale(found.locale());
        String baseUrl = api.baseUrl();
        String url = text.resolveNotificationUrl(baseUrl, found.home(), notification.data());
        if (url == null) url = NotificationText.landingUrl(baseUrl, found.home());
        var link = notification.data().link();
        String topic = notification.type().name() + "|" + (link == null ? notification.id() : link.toString());
        return new PushMessage(
                text.resolveCategory(locale, notification.type()),
                text.resolveMessage(locale, notification),
                NotificationText.withReadMarker(url, notification.id()),
                topic,
                PushMessage.DEFAULT_TTL);
    }

    /**
     * The one push that stands in for several the rate limit held back.
     *
     * @param count how many it stands for
     */
    public PushMessage summary(int count) {
        String locale = instanceLocale();
        return new PushMessage(
                text.resolveLocalized(locale, SECTION, "summaryTitle", null),
                text.resolveLocalized(locale, SECTION, "summaryBody", Map.of("count", String.valueOf(count))),
                api.baseUrl(),
                "summary",
                PushMessage.DEFAULT_TTL);
    }

    private String instanceLocale() {
        return text.resolveLocale(settings.defaultMailLocale());
    }
}
