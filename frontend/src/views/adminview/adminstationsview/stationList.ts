/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ReminderTrigger, StationState, type AdminStationSummary, type ManagerReminderSummary} from '@/api/generated/schema'
import {formatDate} from '@/util/format'

/** Which stations the list shows, as the address carries it in `?state=`. */
export const StationFilters = {
    ALL: 'all',
    SET_UP: 'set-up',
    WAITING: 'waiting',
    NO_MANAGER: 'no-manager',
} as const

export type StationFilter = (typeof StationFilters)[keyof typeof StationFilters]

/** The filters in the order the chips show them. */
export const STATION_FILTER_ORDER: readonly StationFilter[] = [
    StationFilters.ALL,
    StationFilters.SET_UP,
    StationFilters.WAITING,
    StationFilters.NO_MANAGER,
]

/** The label of each filter chip. */
export const FILTER_LABEL_KEYS: Readonly<Record<StationFilter, string>> = {
    [StationFilters.ALL]: 'adminStations.filterAll',
    [StationFilters.SET_UP]: 'adminStations.filterSetUp',
    [StationFilters.WAITING]: 'adminStations.filterWaiting',
    [StationFilters.NO_MANAGER]: 'adminStations.filterNoManager',
}

/** The label of each state, as the badge and the table show it. */
export const STATE_LABEL_KEYS: Readonly<Record<StationState, string>> = {
    [StationState.SET_UP]: 'adminStations.stateSetUp',
    [StationState.SETTING_UP]: 'adminStations.stateSettingUp',
    [StationState.WAITING_FOR_MANAGER]: 'adminStations.stateWaitingForManager',
    [StationState.NO_MANAGER]: 'adminStations.stateNoManager',
}

/** The states in the order the table sorts them: what still needs doing first. */
export const STATE_ORDER: readonly StationState[] = [
    StationState.NO_MANAGER,
    StationState.WAITING_FOR_MANAGER,
    StationState.SETTING_UP,
    StationState.SET_UP,
]

/** How the stations are drawn, remembered per browser. */
export const StationViews = {
    TILES: 'tiles',
    TABLE: 'table',
} as const

export type StationView = (typeof StationViews)[keyof typeof StationViews]

/**
 * The filter the address asks for. Anything it does not know, or a repeated parameter, shows every
 * station rather than none.
 */
export function filterFromQuery(value: unknown): StationFilter {
    return STATION_FILTER_ORDER.find(filter => filter === value) ?? StationFilters.ALL
}

/** Whether the station waits for its manager, so that reminding them could help. */
export function waitsForManager(station: Pick<AdminStationSummary, 'state'>): boolean {
    return station.state === StationState.WAITING_FOR_MANAGER || station.state === StationState.SETTING_UP
}

/** Whether a reminder can be sent: the station waits, and is not only the copy it left when it moved away. */
export function canRemind(station: Pick<AdminStationSummary, 'state' | 'movedAway'>): boolean {
    return waitsForManager(station) && !station.movedAway
}

/**
 * Whether a station belongs under a filter. "Waiting" holds both a station whose manager cannot sign
 * in yet and one whose manager can but has not finished the setup.
 */
export function matchesFilter(station: Pick<AdminStationSummary, 'state'>, filter: StationFilter): boolean {
    switch (filter) {
        case StationFilters.SET_UP:
            return station.state === StationState.SET_UP
        case StationFilters.WAITING:
            return waitsForManager(station)
        case StationFilters.NO_MANAGER:
            return station.state === StationState.NO_MANAGER
        default:
            return true
    }
}

/** The view a stored choice names, the tiles where nothing usable was stored. */
export function viewFromStorage(value: string | null): StationView {
    return value === StationViews.TABLE ? StationViews.TABLE : StationViews.TILES
}

/** Up to two letters standing in for a station without a logo: the first of its first two words. */
export function initialsOf(name: string): string {
    return name
        .split(/\s+/)
        .filter(word => word.length > 0)
        .slice(0, 2)
        .map(word => word[0]!.toUpperCase())
        .join('')
}

/** How long after a reminder by hand the server refuses another one, so the button waits as long. */
export const HAND_GAP_MS = 10 * 60 * 1000

/** Whether the newest reminder went out by hand so recently that another one would be refused. */
export function remindedByHandJustNow(reminder: ManagerReminderSummary | null, now: number): boolean {
    if (!reminder || reminder.trigger !== ReminderTrigger.MANUAL) return false
    return now - Date.parse(reminder.sentAt) < HAND_GAP_MS
}

/** When the last reminder went out and whether by hand, or `null` where none went out in this wait. */
export function lastReminderText(
    reminder: ManagerReminderSummary | null,
    t: (key: string, values: Record<string, unknown>) => string,
): string | null {
    if (!reminder) return null
    const date = formatDate(reminder.sentAt)
    return reminder.trigger === ReminderTrigger.MANUAL
        ? t('adminStations.lastReminded', {date})
        : t('adminStations.lastRemindedAutomatically', {date})
}
