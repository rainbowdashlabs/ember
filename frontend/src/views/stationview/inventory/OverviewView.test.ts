/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {ref} from 'vue'
import OverviewView from './OverviewView.vue'
import {StationModule} from '@/api/generated/schema'
import {sessionWriter} from '@/util/sessionState'
import {createSessionInfo} from '@/test/mocks/factories'

const listMovements = vi.fn()
const listOpen = vi.fn()
const listInventories = vi.fn()
const ownerAbove = vi.fn()

vi.mock('@/api', () => ({
    inventory: {
        listInventories: (...args: unknown[]) => listInventories(...args),
        listItems: vi.fn(),
        listSizes: vi.fn(),
        ownerAbove: (...args: unknown[]) => ownerAbove(...args),
    },
    stationMembers: {listMembers: vi.fn().mockResolvedValue([])},
    movements: {listMovements: (...args: unknown[]) => listMovements(...args)},
    procurement: {listOpen: (...args: unknown[]) => listOpen(...args)},
}))

vi.mock('@/composables/useStations', () => ({
    useStations: () => ({activeStation: ref({stationId: 'station'})}),
}))

/**
 * The inventory overview loads what the station's parts of the inventory hold, and only those that
 * are on: asking a part that is off is refused, and that refusal took the whole page with it.
 */
describe('OverviewView', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        listInventories.mockResolvedValue([])
        listMovements.mockResolvedValue([])
        listOpen.mockResolvedValue([])
        ownerAbove.mockResolvedValue(null)
    })

    afterEach(() => sessionWriter().setInfo(null))

    async function settled() {
        const view = await mountSuspended(OverviewView)
        await new Promise(resolve => setTimeout(resolve, 0))
        await view.vm.$nextTick()
        return view
    }

    it('asks for movements and procurements while both are on', async () => {
        sessionWriter().setInfo(createSessionInfo())

        await settled()

        expect(listMovements).toHaveBeenCalled()
        expect(listOpen).toHaveBeenCalled()
    })

    it('leaves out a part that is off and still loads the rest', async () => {
        sessionWriter().setInfo(createSessionInfo({
            disabledModules: [StationModule.INVENTORY_MOVEMENTS, StationModule.INVENTORY_PROCUREMENT],
        }))

        await settled()

        expect(listMovements).not.toHaveBeenCalled()
        expect(listOpen).not.toHaveBeenCalled()
        expect(listInventories).toHaveBeenCalled()
    })

    it('offers to ask the association for a piece where it keeps its gear in Ember', async () => {
        sessionWriter().setInfo(createSessionInfo())
        ownerAbove.mockResolvedValue('Kreisfeuerwehrverband')

        const view = await settled()

        const panel = view.find('[data-testid="request-from-owner"]')
        expect(panel.exists()).toBe(true)
        expect(panel.text()).toContain('Kreisfeuerwehrverband')
        expect(view.find('[data-testid="request-note-down"]').exists()).toBe(false)
    })

    it('offers to note a request down where the association is outside Ember and orders may be placed', async () => {
        sessionWriter().setInfo(createSessionInfo({permissions: ['LOGIN', 'INVENTORY_PROCUREMENT']}))

        const view = await settled()

        expect(view.find('[data-testid="request-note-down"]').exists()).toBe(true)
    })

    it('offers nothing where the association is outside Ember and no order may be placed', async () => {
        sessionWriter().setInfo(createSessionInfo())

        const view = await settled()

        expect(view.find('[data-testid="request-from-owner"]').exists()).toBe(false)
    })
})
