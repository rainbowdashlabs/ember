/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import ReportView from './ReportView.vue'
import ReportFilters from './reportview/ReportFilters.vue'
import ReportPresetList from './reportview/ReportPresetList.vue'

const YOUTH = {id: 4, name: 'Jugend'}
const ACTIVE = {id: 5, name: 'Aktive'}
const DELETED_GROUP_ID = 99

/** A preset saved last year, before one of its groups was deleted. */
const BOTH = {
    id: 1,
    stationId: 'station',
    name: 'Beide',
    userTypes: ['MEMBER', 'TEAM'],
    groupIds: [YOUTH.id, ACTIVE.id, DELETED_GROUP_ID],
    period: 'quarter',
    rounding: 'round',
}

const listPresets = vi.fn()
const createPreset = vi.fn()

vi.mock('@/api', () => ({
    attendance: {
        listPresets: (...args: unknown[]) => listPresets(...args),
        createPreset: (...args: unknown[]) => createPreset(...args),
        deletePreset: vi.fn(),
        reportPreview: vi.fn(),
    },
    memberGroups: {listGroups: () => Promise.resolve([YOUTH, ACTIVE])},
}))

vi.mock('@/composables/useSession', async () => {
    const {ref} = await import('vue')
    return {useSession: () => ({loaded: ref(true)})}
})

async function settle(wrapper: Awaited<ReturnType<typeof mountSuspended>>) {
    await new Promise(resolve => setTimeout(resolve, 0))
    await wrapper.vm.$nextTick()
}

async function mountLoaded() {
    const wrapper = await mountSuspended(ReportView)
    await settle(wrapper)
    return wrapper
}

/**
 * A saved filter is a question asked again: it has to come back with every user type and every
 * group it was saved with, and it has to reach the server in a shape the server accepts.
 */
describe('ReportView presets', () => {
    beforeEach(() => {
        listPresets.mockReset()
        createPreset.mockReset()
        listPresets.mockResolvedValue([BOTH])
        createPreset.mockResolvedValue(BOTH)
    })

    it('names every user type and every group that still exists', async () => {
        const wrapper = await mountLoaded()

        const label = wrapper.findComponent(ReportPresetList).text()
        expect(label).toContain('Mitglied')
        expect(label).toContain('Team')
        expect(label).toContain('Jugend')
        expect(label).toContain('Aktive')
    })

    it('restores both selections, leaving out a group deleted since', async () => {
        const wrapper = await mountLoaded()

        wrapper.findComponent(ReportPresetList).vm.$emit('apply', BOTH)
        await settle(wrapper)

        const filters = wrapper.findComponent(ReportFilters)
        expect(filters.props('selectedUserTypes')).toEqual(['MEMBER', 'TEAM'])
        expect(filters.props('selectedGroupIds')).toEqual([String(YOUTH.id), String(ACTIVE.id)])
        expect(filters.props('selectedPeriod')).toBe('quarter')
        expect(filters.props('selectedRounding')).toBe('round')
    })

    it('asks about now, whatever point in time was on screen', async () => {
        const wrapper = await mountLoaded()
        const filters = wrapper.findComponent(ReportFilters)
        filters.vm.$emit('update:selectedYear', new Date().getFullYear() - 3)
        await settle(wrapper)

        wrapper.findComponent(ReportPresetList).vm.$emit('apply', BOTH)
        await settle(wrapper)

        expect(filters.props('selectedYear')).toBe(new Date().getFullYear())
    })

    it('sends every selected user type and group when saving', async () => {
        const wrapper = await mountLoaded()
        const filters = wrapper.findComponent(ReportFilters)
        filters.vm.$emit('update:selectedUserTypes', ['MEMBER', 'GUARDIAN'])
        filters.vm.$emit('update:selectedGroupIds', [String(YOUTH.id), String(ACTIVE.id)])
        filters.vm.$emit('update:presetName', 'Mein Filter')
        await settle(wrapper)

        await (filters.props('savePreset') as () => Promise<void>)()

        expect(createPreset).toHaveBeenCalledWith({
            name: 'Mein Filter',
            userTypes: ['MEMBER', 'GUARDIAN'],
            groupIds: [YOUTH.id, ACTIVE.id],
            period: 'month',
            rounding: 'exact',
        })
    })
})
