/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expiryReminderKey} from './expiry'

/**
 * The message key one notification is worded by.
 *
 * <p>Most types have one sentence and it is the key they arrive with. A few say different things
 * depending on what they carry: an expiry reminder counts days or members, and a date an appointment
 * moved off names the next date only where one is left. Every list of notifications asks here, so
 * a type that gains a second sentence gains it everywhere at once.
 *
 * @param type      the notification's type
 * @param localeKey the key the type arrives with
 * @param params    what the notification carries
 */
export function notificationMessageKey(type: string, localeKey: string, params: Record<string, string>): string {
    if (type === 'EXPIRY_REMINDER') return expiryReminderKey(params)
    if (type === 'EVENT_DATE_DROPPED' && !params.nextDate) return 'notification.eventDateDroppedLast'
    return localeKey
}
