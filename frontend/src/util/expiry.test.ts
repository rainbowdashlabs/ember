/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {daysLeft, expiryConfigOf, expiryReminderKey, expirySettingsOf, expiryStateOf, readExpiry} from './expiry'

/**
 * Where an expiry date stands, on the examples the server's copy of the rule is tested against.
 *
 * <p>The same rows stand in the server's own test of the rule. The two copies colour the list and
 * word the reminders, and a date the screen calls expiring while its reminder calls it expired is
 * what one set of examples prevents.
 *
 * @vitest-environment happy-dom
 */
describe('expiry', () => {
    const today = new Date(2026, 2, 1)

    it.each([
        ['2026-04-15', 30, 'VALID'],
        ['2026-03-31', 30, 'EXPIRING'],
        ['2026-03-02', 30, 'EXPIRING'],
        ['2026-03-01', 30, 'EXPIRING'],
        ['2026-02-28', 30, 'EXPIRED'],
        ['2025-12-31', 30, 'EXPIRED'],
        ['2026-03-02', 0, 'VALID'],
        ['2026-03-01', 0, 'EXPIRING'],
        [null, 30, 'EMPTY'],
    ])('%s warned %i days ahead is %s on 2026-03-01', (value, warnFromDays, state) => {
        expect(expiryStateOf(value, warnFromDays, today)).toBe(state)
    })

    it.each([['2026-03-13', 12], ['2026-03-01', 0], ['2026-02-26', -3]])('%s has %i days left', (value, left) => {
        expect(daysLeft(value, today)).toBe(left)
    })

    it('counts across a change to summer time as whole days', () => {
        expect(daysLeft('2026-04-01', new Date(2026, 2, 20, 23, 30))).toBe(12)
    })

    it('reads what is no date as empty', () => {
        expect(expiryStateOf('bald', 30, today)).toBe('EMPTY')
        expect(expiryStateOf('', 30, today)).toBe('EMPTY')
    })

    it('reads the days either way of the date as a count', () => {
        expect(readExpiry('2026-03-13', 30, today)).toEqual({state: 'EXPIRING', days: 12})
        expect(readExpiry('2026-02-26', 30, today)).toEqual({state: 'EXPIRED', days: 3})
    })

    it('fills in what a field leaves out', () => {
        expect(expirySettingsOf({})).toEqual({
            warnFromDays: 30, reminderDays: [30, 7], repeatEveryDays: null, remindMember: true, remindManagement: false,
        })
        expect(expirySettingsOf({warnFromDays: 90, reminderDays: [30, 90, 30], repeatEveryDays: 7, remindMember: false}))
            .toEqual({warnFromDays: 90, reminderDays: [30, 90], repeatEveryDays: 7, remindMember: false, remindManagement: false})
    })

    it('words a reminder by its kind and its count', () => {
        expect(expiryReminderKey({kind: 'EXPIRES_IN', days: '1'})).toBe('notification.expiryReminderInOne')
        expect(expiryReminderKey({kind: 'EXPIRES_IN', days: '12'})).toBe('notification.expiryReminderIn')
        expect(expiryReminderKey({kind: 'EXPIRES_TODAY', days: '0'})).toBe('notification.expiryReminderToday')
        expect(expiryReminderKey({kind: 'EXPIRED', days: '1'})).toBe('notification.expiryReminderExpiredOne')
        expect(expiryReminderKey({kind: 'MEMBERS_DUE', count: '4', days: '1'})).toBe('notification.expiryReminderDue')
        expect(expiryReminderKey({kind: 'LATER'})).toBe('notification.expiryReminder')
    })

    it('writes the settings back without a repeat it does not have', () => {
        const settings = expirySettingsOf({})
        expect(expiryConfigOf(settings)).toEqual({
            warnFromDays: 30, reminderDays: [30, 7], remindMember: true, remindManagement: false,
        })
        expect(expiryConfigOf({...settings, repeatEveryDays: 14}).repeatEveryDays).toBe(14)
    })
})
