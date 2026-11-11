/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {flushPromises, mount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import AdminStatisticsView from './AdminStatisticsView.vue'
import {FederationShareKind, StatisticsWindow} from '@/api/generated/schema'
import type {AdminStatistics} from '@/api/generated/schema'

const get = vi.fn()

vi.mock('@/api/client', () => ({
    default: {get: (...args: unknown[]) => get(...args)},
}))

const route = {hash: ''}

vi.mock('vue-router', async (original) => ({
    ...await original<typeof import('vue-router')>(),
    useRoute: () => route,
}))

vi.mock('vue-echarts', () => ({default: {name: 'VChart', template: '<div class="chart"/>'}}))

const people = {people: 72, trial: 5, member: 50, guardian: 10, team: 18, manager: 3, memberships: 81}

function statistics(window: StatisticsWindow): AdminStatistics {
    return {
        emailPending: 0, emailSending: 0, emailSentToday: 0, emailFailed: 0, emailSent: 0, mailProviderBlocks: 0,
        totalAccounts: 40, totalStations: 3, totalClusters: 1, totalMembers: 81, activeSessions: 7,
        pendingApplications: 0, sessionsThisMonth: 2, totalInventoryItems: 10, totalEvents: 4,
        totalAttendanceSessions: 2, totalAttendanceEntries: 20, totalProfileFields: 3, totalGroups: 2,
        accountsVerified: 30, accountsUnverified: 10, stationsSetupComplete: 2, stationsSetupPending: 1,
        accountsWith2fa: 5, eventsUpcoming: 1, totalEventRegistrations: 6,
        window,
        emailByDay: [],
        emailByStatus: [],
        signUpsByDay: [{day: '2026-11-11', count: 2}],
        topStationsByMembers: [],
        activeByDay: {today: 14, average30: 11.3, recordedSince: '2026-11-01', days: [{day: '2026-11-11', count: 14}]},
        growth: {recordedSince: '2026-11-01', days: []},
        attendanceByMonth: [],
        federation: {
            partnersByStatus: [], localPartners: 1, remotePartners: 2, stationsWithPartner: 1, partnerInstances: 1,
            sharesByKind: [{kind: FederationShareKind.BOARD, count: 3}], lendingByMonth: [], peersKnown: 4,
            peersReachable: 3, associations: 1, stationsInAssociations: 2,
        },
        peopleByType: {current: people, byDay: []},
    }
}

/**
 * The instance statistics ask for the last thirty days first and ask again over the window chosen,
 * and show the people by member type with the hint that types add up to more than the total.
 *
 * @vitest-environment happy-dom
 */
describe('AdminStatisticsView', () => {
    beforeEach(() => {
        route.hash = ''
        document.body.innerHTML = ''
        get.mockReset()
        get.mockImplementation((_url: string, config: {params: {window: StatisticsWindow}}) =>
            Promise.resolve({data: statistics(config.params.window)}))
    })

    function page() {
        return mount(AdminStatisticsView, {
            attachTo: document.body,
            global: {stubs: {ViewContent: {template: '<div><slot/></div>'}}},
        })
    }

    it('loads the last thirty days first', async () => {
        const view = page()
        await flushPromises()

        expect(get).toHaveBeenCalledWith('/admin/statistics', {params: {window: StatisticsWindow.DAYS_30}})
        expect(view.text()).toContain('Erziehungsberechtigte')
        expect(view.text()).toContain('Personen insgesamt')
        expect(view.text()).toContain('mehr als die Personen insgesamt')
        expect(view.text()).not.toContain('Neue Sitzungen pro Tag')
    })

    it('asks again when another window is chosen', async () => {
        const view = page()
        await flushPromises()

        await view.find('select').setValue(StatisticsWindow.MONTHS_12)
        await flushPromises()

        expect(get).toHaveBeenLastCalledWith('/admin/statistics', {params: {window: StatisticsWindow.MONTHS_12}})
        expect(get).toHaveBeenCalledTimes(2)
    })

    it('drops an answer for a window no longer chosen and stays loading until the last one is back', async () => {
        const answers: Array<(value: unknown) => void> = []
        const view = page()
        await flushPromises()
        get.mockImplementation(() => new Promise(resolve => answers.push(resolve)))

        await view.find('select').setValue(StatisticsWindow.MONTHS_12)
        await view.find('select').setValue(StatisticsWindow.ALL)
        expect(answers).toHaveLength(2)

        answers[1]!({data: statistics(StatisticsWindow.ALL)})
        await flushPromises()
        answers[0]!({data: statistics(StatisticsWindow.MONTHS_12)})
        await flushPromises()

        const weekly = view.findComponent({name: 'UsageStatsSection'})
        expect(weekly.props('byWeek')).toBe(true)
        expect(view.findComponent({name: 'WindowedStatsSections'}).props('stats').window).toBe(StatisticsWindow.ALL)
        expect(view.findComponent({name: 'WindowedStatsSections'}).props('loading')).toBe(false)
    })

    it('stays loading while the newest request is open, even after an older one answered', async () => {
        const answers: Array<(value: unknown) => void> = []
        const view = page()
        await flushPromises()
        get.mockImplementation(() => new Promise(resolve => answers.push(resolve)))

        await view.find('select').setValue(StatisticsWindow.MONTHS_12)
        await view.find('select').setValue(StatisticsWindow.ALL)
        answers[0]!({data: statistics(StatisticsWindow.MONTHS_12)})
        await flushPromises()

        const sections = view.findComponent({name: 'WindowedStatsSections'})
        expect(sections.props('loading')).toBe(true)
        expect(sections.props('stats').window).toBe(StatisticsWindow.DAYS_30)
    })

    it('scrolls to the federation section when opened at its anchor', async () => {
        route.hash = '#federation'
        const scrolled = vi.fn()
        Element.prototype.scrollIntoView = scrolled
        page()
        await flushPromises()

        expect(scrolled).toHaveBeenCalledTimes(1)
    })
})
