/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {UserSettings} from '@/api/userSettings'

/**
 * The notification settings a demonstration shows. They go to the application's own settings
 * section, so the three channels and the nine kinds are the ones the screen really offers.
 */
function toggle(app: boolean, email: boolean, feed: boolean) {
    return {app, email, feed}
}

export const NOTIFICATION_SETTINGS: UserSettings = {
    emailEnabled: true,
    theme: 'ember',
    darkMode: 'system',
    mailConfigured: true,
    mailProviderName: 'Postmark',
    mailProviderUrl: 'https://postmarkapp.com/privacy-policy',
    notifications: {
        NEW_NEWS: toggle(true, true, true),
        NEWS_COMMENT: toggle(true, false, true),
        COMMENT_MENTION: toggle(true, true, true),
        NEW_EVENT: toggle(true, true, true),
        EVENT_REGISTRATION_STATUS: toggle(true, true, false),
        EXCHANGE_STATUS_CHANGE: toggle(true, false, false),
        MEMBER_ADDED_TO_GROUP: toggle(true, false, false),
        PROFILE_FIELD_CHANGED: toggle(true, false, false),
        PROCUREMENT_REQUESTED: toggle(false, false, false),
    },
}

/** The two personal feeds, with the addresses the settings page hands out. */
export const NOTIFICATION_FEEDS = [
    {
        icon: ['fas', 'calendar-days'] as [string, string],
        title: 'iCal-Kalender',
        helpRouteName: 'help-profile-ical-feed',
        hint: 'Abonniere deine Termine direkt im Kalender - neue und geänderte Termine erscheinen ohne Zutun.',
        url: 'https://musterstadt.example/api/v1/feed/ical/8f3c…a91/rich',
        recommended: true,
        recommendedLabel: 'Empfohlen',
    },
    {
        icon: ['fas', 'rss'] as [string, string],
        title: 'Atom-Feed',
        helpRouteName: 'help-profile-rss-feed',
        hint: 'Dein persönlicher Benachrichtigungsstrom für jeden Reader - mit Autor, Kategorien und Vorschaubildern.',
        url: 'https://musterstadt.example/api/v1/feed/atom/8f3c…a91/compact',
    },
]
