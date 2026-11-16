/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, type VueWrapper} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {ref} from 'vue'
import EventGroupSignUpsPanel from './EventGroupSignUpsPanel.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import type {DataTableApi} from '@/composables/useDataTable'
import {RegistrationStatus, type EventGroupDetail, type EventGroupSignUp} from '@/api/generated/schema'
import {createIdentity} from '@/test/mocks/factories'

const api = vi.hoisted(() => ({
    eventGroups: {listSignUps: vi.fn(), decide: vi.fn()},
    stationMembers: {listCompletions: vi.fn(async () => [])},
}))
vi.mock('@/api', () => api)
vi.mock('@/composables/useSession', () => ({
    useSession: () => ({hasPermission: () => false, sessionInfo: ref(null)}),
}))

function detail(): EventGroupDetail {
    return {
        eventGroup: {
            id: 70,
            details: {name: 'Jugendflamme 1', description: null, registrationLimit: null, registrationDeadline: null, requiresConfirmation: false, color: null, minRegistrations: null, thresholdDays: null},
            cancelled: false, cancelReason: null, partCount: 4, holding: 2,
        },
        sets: [{id: 1, eventGroupId: 70, name: 'Übung', minimum: 2, position: 0}],
        parts: [],
        registration: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
        view: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
        questions: [],
        fields: [],
        household: [],
    }
}

function signUp(memberId: number, name: string, status: RegistrationStatus, chosen: number, answersMissing = false): EventGroupSignUp {
    return {
        memberId, member: createIdentity({name}), status, registeredAt: '2026-11-01T10:00:00Z', parts: Array.from({length: chosen}, (_, i) => i + 1),
        sets: [{setId: 1, name: 'Übung', required: 2, chosen, missingRequired: []}], answers: [], answersMissing, mayRegister: true,
    }
}

let wrapper: VueWrapper | null = null

async function mountPanel() {
    wrapper = await mountSuspended(EventGroupSignUpsPanel, {
        props: {detail: detail()},
        global: {stubs: {MemberName: true, EventGroupSignUpExport: true, EventGroupSignUpDialog: true}},
    })
    await flushPromises()
    return (wrapper.getComponent(RecordTable).props() as {table: DataTableApi<EventGroupSignUp>}).table
}

/** The sign-ups as a table: sorted and filtered by any column, with the decisions on each row. */
describe('EventGroupSignUpsPanel', () => {
    beforeEach(() => {
        api.eventGroups.listSignUps.mockResolvedValue([
            signUp(11, 'Anna', RegistrationStatus.ACCEPTED, 2),
            signUp(12, 'Ben', RegistrationStatus.PENDING, 1),
            signUp(13, 'Clara', RegistrationStatus.PENDING, 2, true),
        ])
        api.eventGroups.decide.mockReset()
    })

    afterEach(() => {
        wrapper?.unmount()
        wrapper = null
    })

    it('shows a row per sign-up with what is still missing', async () => {
        await mountPanel()

        const rows = wrapper!.findAll('[data-testid="event-group-sign-up-row"]')
        expect(rows).toHaveLength(3)
        expect(rows[0]!.text()).toContain('Vollständig')
        expect(rows[1]!.find('[data-testid="event-group-short"]').exists()).toBe(true)
        expect(rows[2]!.find('[data-testid="event-group-answer-missing"]').exists()).toBe(true)
    })

    it('narrows to the sign-ups short of a set or owing an answer', async () => {
        const table = await mountPanel()

        table.setFilter('complete', new Set(['false']), false)
        await flushPromises()

        expect(table.rows.map(entry => entry.memberId)).toEqual([12, 13])
    })

    it('filters by status', async () => {
        const table = await mountPanel()

        table.setFilter('status', new Set([RegistrationStatus.ACCEPTED]), false)
        await flushPromises()

        expect(table.rows.map(entry => entry.memberId)).toEqual([11])
    })

    it('decides a sign-up from its row', async () => {
        api.eventGroups.decide.mockResolvedValue({})
        await mountPanel()

        await wrapper!.findAll('[data-testid="event-group-sign-up-row"]')[1]!.get('[data-testid="event-group-accept"]').trigger('click')
        await flushPromises()

        expect(api.eventGroups.decide).toHaveBeenCalledWith(70, 12, RegistrationStatus.ACCEPTED)
    })
})
