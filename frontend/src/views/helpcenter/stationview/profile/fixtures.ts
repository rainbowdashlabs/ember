/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {ActiveSession, SettingsResponse} from '@/api/generated/schema'

const HOUR = 3_600_000

/** A moment the given number of hours before now, so the "last active" texts stay plausible. */
function hoursAgo(hours: number): string {
  return new Date(Date.now() - hours * HOUR).toISOString()
}

/** Three sessions on different devices, the first one being the reader's own. */
export function demoSessions(): ActiveSession[] {
  return [
    {
      id: 1,
      isCurrent: true,
      userAgent: 'Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0',
      location: 'Berlin',
      createdAt: '2026-06-07T08:15:00Z',
      lastUsedAt: hoursAgo(0),
      expiresAt: '2026-12-07T08:15:00Z',
    },
    {
      id: 2,
      isCurrent: false,
      userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 Version/17.5 Safari/605.1.15',
      location: 'Hamburg',
      createdAt: '2026-06-05T14:30:00Z',
      lastUsedAt: hoursAgo(3),
      expiresAt: '2026-12-05T14:30:00Z',
    },
    {
      id: 3,
      isCurrent: false,
      userAgent: 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36',
      location: null,
      createdAt: '2026-06-01T09:00:00Z',
      lastUsedAt: hoursAgo(50),
      expiresAt: '2026-12-01T09:00:00Z',
    },
  ]
}

/** Notification settings with mail set up and a mix of channels switched on per kind of notification. */
export const DEMO_SETTINGS: SettingsResponse = {
  darkMode: 'system',
  feel: 'default',
  theme: 'default',
  emailEnabled: true,
  mailConfigured: true,
  mailProviderName: 'Beispiel Mail GmbH',
  mailProviderUrl: 'https://mail.beispiel.de/datenschutz',
  notifications: {
    NEW_NEWS: {app: true, email: true, feed: true},
    NEWS_COMMENT: {app: true, email: false, feed: true},
    COMMENT_MENTION: {app: true, email: true, feed: false},
    NEW_EVENT: {app: true, email: false, feed: false},
    EVENT_REGISTRATION_STATUS: {app: true, email: true, feed: true},
    MEMBER_ADDED_TO_GROUP: {app: true, email: false, feed: true},
    PROFILE_FIELD_CHANGED: {app: true, email: false, feed: false},
    PROCUREMENT_REQUESTED: {app: false, email: false, feed: false},
    EXPIRY_REMINDER: {app: true, email: true, feed: true},
  },
}
