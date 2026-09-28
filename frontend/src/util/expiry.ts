/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {ProfileFieldConfig} from '@/api/profileFields'

/**
 * Where an expiry date stands. The date is the last valid day: valid until the 31st means valid all
 * of the 31st and expired from the 1st.
 */
export const ExpiryStates = {
    VALID: 'VALID',
    EXPIRING: 'EXPIRING',
    EXPIRED: 'EXPIRED',
    EMPTY: 'EMPTY',
} as const

export type ExpiryStateName = (typeof ExpiryStates)[keyof typeof ExpiryStates]

/** The states a filter can ask for. Empty is the filter's own checkbox, as for every other column. */
export const FILTERABLE_EXPIRY_STATES: readonly ExpiryStateName[] = [
    ExpiryStates.VALID, ExpiryStates.EXPIRING, ExpiryStates.EXPIRED,
]

/**
 * What an expiry date field does, every setting it leaves out filled in.
 *
 * <p>The twin of the server's settings record, with the same defaults: a month's warning, reminders a
 * month and a week ahead, to the member and not to the member management, and no repeats.
 */
export interface ExpirySettings {
    warnFromDays: number
    reminderDays: number[]
    /** How often a reminder goes out again after the date has passed, or null for once. */
    repeatEveryDays: number | null
    remindMember: boolean
    remindManagement: boolean
}

export const DEFAULT_WARN_FROM_DAYS = 30
export const DEFAULT_REMINDER_DAYS: readonly number[] = [30, 7]

function wholeDays(value: unknown): number | null {
    return typeof value === 'number' && Number.isInteger(value) ? value : null
}

/** The settings of one field, the missing ones at their defaults. */
export function expirySettingsOf(config: ProfileFieldConfig | null | undefined): ExpirySettings {
    const cfg = config ?? {}
    const days = Array.isArray(cfg.reminderDays)
        ? [...new Set(cfg.reminderDays.map(wholeDays).filter((d): d is number => d !== null))].sort((a, b) => a - b)
        : [...DEFAULT_REMINDER_DAYS]
    return {
        warnFromDays: wholeDays(cfg.warnFromDays) ?? DEFAULT_WARN_FROM_DAYS,
        reminderDays: days,
        repeatEveryDays: wholeDays(cfg.repeatEveryDays),
        remindMember: cfg.remindMember !== false,
        remindManagement: cfg.remindManagement === true,
    }
}

/** The settings as a field's configuration keeps them. */
export function expiryConfigOf(settings: ExpirySettings): ProfileFieldConfig {
    const config: ProfileFieldConfig = {
        warnFromDays: settings.warnFromDays,
        reminderDays: [...settings.reminderDays],
        remindMember: settings.remindMember,
        remindManagement: settings.remindManagement,
    }
    if (settings.repeatEveryDays !== null) config.repeatEveryDays = settings.repeatEveryDays
    return config
}

const DAY = /^(\d{4})-(\d{2})-(\d{2})/
const MILLIS_PER_DAY = 86_400_000

/**
 * How many days are left until the last valid day, on the reader's clock: 0 on the day itself,
 * negative once it has passed, null where the value is no date.
 *
 * <p>The server counts on the station's clock. The two can differ by a day for a few hours around
 * midnight, which moves a colour and nothing else: the reminders go by the server.
 */
export function daysLeft(value: string | null | undefined, today: Date = new Date()): number | null {
    const written = value ? DAY.exec(value.trim()) : null
    if (!written) return null
    const last = Date.UTC(Number(written[1]), Number(written[2]) - 1, Number(written[3]))
    const now = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate())
    return Math.round((last - now) / MILLIS_PER_DAY)
}

/**
 * Where one date stands. The one rule the member list, the member page, the profile forms and the
 * list filter all read.
 */
export function expiryStateOf(
    value: string | null | undefined, warnFromDays: number, today: Date = new Date(),
): ExpiryStateName {
    const left = daysLeft(value, today)
    if (left === null) return ExpiryStates.EMPTY
    if (left < 0) return ExpiryStates.EXPIRED
    if (left <= warnFromDays) return ExpiryStates.EXPIRING
    return ExpiryStates.VALID
}

/**
 * The sentences an expiry reminder is worded by, for one and for several, keyed by the kind the
 * server names: a date still ahead, the last valid day, a date passed, or member management's list.
 */
const REMINDER_KEYS: Record<string, readonly [one: string, other: string]> = {
    EXPIRES_IN: ['notification.expiryReminderInOne', 'notification.expiryReminderIn'],
    EXPIRES_TODAY: ['notification.expiryReminderToday', 'notification.expiryReminderToday'],
    EXPIRED: ['notification.expiryReminderExpiredOne', 'notification.expiryReminderExpired'],
    MEMBERS_DUE: ['notification.expiryReminderDueOne', 'notification.expiryReminderDue'],
}

/**
 * The message key of one expiry reminder. A member's reminder is counted in days, member
 * management's in members, and a kind this build does not know falls back to the type's own sentence.
 */
export function expiryReminderKey(params: Record<string, string>): string {
    const keys = REMINDER_KEYS[params.kind ?? '']
    if (!keys) return 'notification.expiryReminder'
    const count = Number(params.kind === 'MEMBERS_DUE' ? params.count : params.days)
    return count === 1 ? keys[0] : keys[1]
}

/** A date read for showing: where it stands, and how many days away it is either way. */
export interface ExpiryReading {
    state: ExpiryStateName
    /** Days left while it is valid, days since the last valid day once it has passed. */
    days: number
}

export function readExpiry(
    value: string | null | undefined, warnFromDays: number, today: Date = new Date(),
): ExpiryReading {
    const left = daysLeft(value, today)
    return {state: expiryStateOf(value, warnFromDays, today), days: Math.abs(left ?? 0)}
}
