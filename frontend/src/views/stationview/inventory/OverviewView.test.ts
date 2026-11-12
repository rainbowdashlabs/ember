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

vi.mock('@/api', () => ({
    inventory: {
        listInventories: (...args: unknown[]) => listInventories(...args),
        listItems: vi.fn(),
        listSizes: vi.fn(),
        ownerAbove: vi.fn().mockResolvedValue(null),
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
})
