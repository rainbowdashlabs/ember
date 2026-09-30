/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {notificationMessageKey} from './notificationMessageKey'

/**
 * Which sentence a notification is worded by.
 */
describe('notificationMessageKey', () => {
    it('keeps the key a type with one sentence arrives with', () => {
        expect(notificationMessageKey('EVENT_CANCELLED', 'notification.eventCancelled', {eventName: 'Dienst'}))
            .toBe('notification.eventCancelled')
    })

    it('names the next date of a moved appointment where one is left', () => {
        expect(notificationMessageKey('EVENT_DATE_DROPPED', 'notification.eventDateDropped', {nextDate: '2026-10-07'}))
            .toBe('notification.eventDateDropped')
    })

    it('says no later date follows where none is left', () => {
        expect(notificationMessageKey('EVENT_DATE_DROPPED', 'notification.eventDateDropped', {eventDate: '2026-10-06'}))
            .toBe('notification.eventDateDroppedLast')
    })

    it('counts an expiry reminder the way its kind is counted', () => {
        expect(notificationMessageKey('EXPIRY_REMINDER', 'notification.expiryReminder', {kind: 'EXPIRES_IN', days: '1'}))
            .toBe('notification.expiryReminderInOne')
    })
})
