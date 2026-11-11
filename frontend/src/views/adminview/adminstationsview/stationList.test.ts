/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {ReminderTrigger, StationState} from '@/api/generated/schema'
import {
    StationFilters,
    StationViews,
    filterFromQuery,
    canRemind,
    initialsOf,
    lastReminderText,
    matchesFilter,
    remindedByHandJustNow,
    viewFromStorage,
    waitsForManager,
} from './stationList'

describe('stationList', () => {
    it('reads the filter from the address and falls back to every station', () => {
        expect(filterFromQuery('waiting')).toBe(StationFilters.WAITING)
        expect(filterFromQuery('no-manager')).toBe(StationFilters.NO_MANAGER)
        expect(filterFromQuery('set-up')).toBe(StationFilters.SET_UP)
        expect(filterFromQuery('nonsense')).toBe(StationFilters.ALL)
        expect(filterFromQuery(['waiting', 'set-up'])).toBe(StationFilters.ALL)
        expect(filterFromQuery(undefined)).toBe(StationFilters.ALL)
    })

    it('counts a station being set up as waiting as much as one whose manager cannot sign in yet', () => {
        const waiting = {state: StationState.WAITING_FOR_MANAGER}
        const settingUp = {state: StationState.SETTING_UP}
        const setUp = {state: StationState.SET_UP}
        const noManager = {state: StationState.NO_MANAGER}

        expect(matchesFilter(waiting, StationFilters.WAITING)).toBe(true)
        expect(matchesFilter(settingUp, StationFilters.WAITING)).toBe(true)
        expect(matchesFilter(setUp, StationFilters.WAITING)).toBe(false)
        expect(matchesFilter(setUp, StationFilters.SET_UP)).toBe(true)
        expect(matchesFilter(noManager, StationFilters.NO_MANAGER)).toBe(true)
        expect(matchesFilter(noManager, StationFilters.ALL)).toBe(true)
        expect(waitsForManager(noManager)).toBe(false)
        expect(canRemind({state: StationState.WAITING_FOR_MANAGER, movedAway: false})).toBe(true)
        expect(canRemind({state: StationState.WAITING_FOR_MANAGER, movedAway: true})).toBe(false)
    })

    it('remembers the table and opens the tiles otherwise', () => {
        expect(viewFromStorage('table')).toBe(StationViews.TABLE)
        expect(viewFromStorage(null)).toBe(StationViews.TILES)
        expect(viewFromStorage('grid')).toBe(StationViews.TILES)
    })

    it('waits with the button for ten minutes after a reminder by hand only', () => {
        const sentAt = '2026-11-11T09:00:00Z'
        const at = Date.parse(sentAt)

        expect(remindedByHandJustNow({sentAt, trigger: ReminderTrigger.MANUAL}, at + 9 * 60_000)).toBe(true)
        expect(remindedByHandJustNow({sentAt, trigger: ReminderTrigger.MANUAL}, at + 11 * 60_000)).toBe(false)
        expect(remindedByHandJustNow({sentAt, trigger: ReminderTrigger.AFTER_7_DAYS}, at)).toBe(false)
        expect(remindedByHandJustNow(null, at)).toBe(false)
    })

    it('words the last reminder by how it went out', () => {
        const t = (key: string) => key
        expect(lastReminderText(null, t)).toBeNull()
        expect(lastReminderText({sentAt: '2026-11-08T09:00:00Z', trigger: ReminderTrigger.MANUAL}, t))
            .toBe('adminStations.lastReminded')
        expect(lastReminderText({sentAt: '2026-11-08T09:00:00Z', trigger: ReminderTrigger.AFTER_21_DAYS}, t))
            .toBe('adminStations.lastRemindedAutomatically')
    })

    it('stands in for a logo with the first letters of the first two words', () => {
        expect(initialsOf('Freiwillige Feuerwehr Nord')).toBe('FF')
        expect(initialsOf('  nord ')).toBe('N')
        expect(initialsOf('')).toBe('')
    })
})
