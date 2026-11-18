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
    eventGroups: {listSignUps: vi.fn(), listPartnerSignUps: vi.fn(), decide: vi.fn(), queueSignUps: vi.fn(), moveSignUpInQueue: vi.fn()},
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
            details: {name: 'Jugendflamme 1', description: null, registrationLimit: null, registrationDeadline: null, requiresConfirmation: false, color: null, minRegistrations: null, thresholdDays: null, partRegistrationLimit: null, queueOrder: null},
            cancelled: false, cancelReason: null, partCount: 4, holding: 2, queued: 0,
        },
        sets: [{id: 1, eventGroupId: 70, name: 'Übung', minimum: 2, maximum: null, position: 0}],
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
        sets: [{setId: 1, name: 'Übung', required: 2, chosen, missingRequired: [], maximum: null, tooMany: 0}], answers: [], answersMissing, mayRegister: true,
        queuePosition: null,
    }
}

let wrapper: VueWrapper | null = null

async function mountPanel(shown: EventGroupDetail = detail()) {
    wrapper = await mountSuspended(EventGroupSignUpsPanel, {
        props: {detail: shown},
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
        api.eventGroups.listPartnerSignUps.mockResolvedValue([])
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

        expect(api.eventGroups.decide).toHaveBeenCalledWith(70, 12, RegistrationStatus.ACCEPTED, false)
    })

    /** Denying somebody who holds a place takes it away, so it is asked first, keyed by the member. */
    it('asks before denying an accepted sign-up and sends nothing until agreed', async () => {
        api.eventGroups.decide.mockResolvedValue({})
        await mountPanel()

        await wrapper!.findAll('[data-testid="event-group-sign-up-row"]')[0]!.get('[data-testid="event-group-deny"]').trigger('click')
        await flushPromises()
        expect(api.eventGroups.decide).not.toHaveBeenCalled()

        ;(document.body.querySelector('[data-testid="deny-accepted-confirm"]') as HTMLElement).click()
        await flushPromises()

        expect(api.eventGroups.decide).toHaveBeenCalledWith(70, 11, RegistrationStatus.DENIED, undefined)
    })

    it('denies a pending sign-up without asking', async () => {
        api.eventGroups.decide.mockResolvedValue({})
        await mountPanel()

        await wrapper!.findAll('[data-testid="event-group-sign-up-row"]')[1]!.get('[data-testid="event-group-deny"]').trigger('click')
        await flushPromises()

        expect(api.eventGroups.decide).toHaveBeenCalledWith(70, 12, RegistrationStatus.DENIED, undefined)
    })

    /**
     * Where places are limited, the waiting list stands above the table in its order, partners'
     * members among the station's own, and pending sign-ups go onto it one at a time or all at once.
     */
    it('lists the waiting list in order and puts pending sign-ups on it', async () => {
        api.eventGroups.listSignUps.mockResolvedValue([
            signUp(11, 'Anna', RegistrationStatus.QUEUED, 2),
            {...signUp(12, 'Ben', RegistrationStatus.PENDING, 1), registeredAt: '2026-11-03T10:00:00Z'},
            signUp(13, 'Clara', RegistrationStatus.PENDING, 2),
        ].map(entry => (entry.memberId === 11 ? {...entry, queuePosition: 2} : entry)))
        api.eventGroups.listPartnerSignUps.mockResolvedValue([{
            id: 9, member: createIdentity({name: 'Mara'}), status: RegistrationStatus.QUEUED, registeredAt: null,
            parts: [1], sets: [], queuePosition: 1,
        }])
        api.eventGroups.queueSignUps.mockResolvedValue(undefined)
        const limited = detail()
        limited.eventGroup.details = {...limited.eventGroup.details, registrationLimit: 10}
        await mountPanel(limited)

        const rows = wrapper!.findAll('[data-testid^="queue-entry-"]').map(row => row.attributes('data-testid'))
        expect(rows).toEqual(['queue-entry-partner-9', 'queue-entry-member-11'])

        await wrapper!.get('[data-testid="event-group-queue-all-pending"]').trigger('click')
        await flushPromises()
        expect(api.eventGroups.queueSignUps).toHaveBeenLastCalledWith(70, [13, 12])

        await wrapper!.get('[data-testid="event-group-queue-12"]').trigger('click')
        await flushPromises()
        expect(api.eventGroups.queueSignUps).toHaveBeenLastCalledWith(70, [12])
    })

    it('offers no waiting list where nothing limits places', async () => {
        await mountPanel()

        expect(wrapper!.find('[data-testid="event-group-queue-all-pending"]').exists()).toBe(false)
        expect(wrapper!.find('[data-testid="event-group-queue-12"]').exists()).toBe(false)
    })

    /** One pending sign-up goes onto the list from its own row; the bulk button needs at least two. */
    it('offers only the row button while one sign-up waits for a decision', async () => {
        api.eventGroups.listSignUps.mockResolvedValue([
            signUp(11, 'Anna', RegistrationStatus.ACCEPTED, 2),
            signUp(12, 'Ben', RegistrationStatus.PENDING, 1),
        ])
        const limited = detail()
        limited.eventGroup.details = {...limited.eventGroup.details, partRegistrationLimit: 8}
        await mountPanel(limited)

        expect(wrapper!.find('[data-testid="event-group-queue-all-pending"]').exists()).toBe(false)
        expect(wrapper!.find('[data-testid="event-group-queue-12"]').exists()).toBe(true)
        expect(wrapper!.find('[data-testid="event-group-queue-11"]').exists()).toBe(false)
    })
})
