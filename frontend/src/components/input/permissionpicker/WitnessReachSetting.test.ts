/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import WitnessReachSetting from './WitnessReachSetting.vue'

const getWitnessReach = vi.hoisted(() => vi.fn())
const setWitnessReach = vi.hoisted(() => vi.fn())
const held = vi.hoisted(() => new Set<string>())

vi.mock('@/api/witnessSigning', () => ({getWitnessReach, setWitnessReach}))
vi.mock('@/components/documents/generation', () => ({audienceLists: async () => ({groups: [], tags: []})}))
vi.mock('@/composables/useSession', () => ({
    useSession: () => ({hasPermission: (permission: string) => held.has(permission)}),
}))

const NOBODY = {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND' as const}
const TRIALS = {...NOBODY, userTypes: ['TRIAL' as const]}

async function open() {
    const view = await mountSuspended(WitnessReachSetting)
    await view.get('[data-testid="witness-reach-toggle"]').trigger('click')
    await flushPromises()
    return view
}

/**
 * The station's audience for its witnesses, under the permission: read when opened, said in one line,
 * editable and saved only for whoever manages members.
 */
describe('WitnessReachSetting', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        held.clear()
    })

    it('says that nobody is left out where no audience is set, and offers no editing to a reader', async () => {
        getWitnessReach.mockResolvedValue(NOBODY)

        const view = await open()

        expect(view.get('[data-testid="witness-reach-summary"]').text()).toContain('von allen Mitgliedern und Probemitgliedern')
        expect(view.find('[data-testid="witness-reach-save"]').exists()).toBe(false)
    })

    it('saves the audience a member manager sets', async () => {
        held.add('MEMBER_MANAGER')
        getWitnessReach.mockResolvedValue(TRIALS)
        setWitnessReach.mockResolvedValue(TRIALS)

        const view = await open()
        expect(view.get('[data-testid="witness-reach-summary"]').text()).toContain('nur von den Mitgliedern')
        await view.get('[data-testid="witness-reach-save"]').trigger('click')
        await flushPromises()

        expect(setWitnessReach).toHaveBeenCalledWith(TRIALS)
    })
})
