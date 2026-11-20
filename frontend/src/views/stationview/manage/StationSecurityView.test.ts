/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import type {MemberStatus} from '@/api/generated/schema'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {followModifierKeys} from '@/util/modifierKeys'
import StationSecurityView from './StationSecurityView.vue'
import MembersPanel from './stationsecurityview/MembersPanel.vue'

const resetSecondFactor = vi.fn()
const listStationMemberStatus = vi.fn()

vi.mock('@/api', () => ({
    twoFactorAdmin: {
        listAssignableUserTypes: async () => [],
        listStationPolicies: async () => [],
        listStationMemberStatus: (...args: unknown[]) => listStationMemberStatus(...args),
        resetAccount2FAByStationAdmin: (...args: unknown[]) => resetSecondFactor(...args),
    },
}))

const LENA = {accountId: 42, firstName: 'Lena', lastName: 'Weber'} as MemberStatus

function holdShift(held: boolean) {
    window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

async function securityView() {
    const wrapper = await mountSuspended(StationSecurityView)
    await flushPromises()
    return wrapper
}

/** Resetting a member's second factor asks first, and shift skips the question. */
describe('StationSecurityView', () => {
    beforeAll(() => followModifierKeys())
    beforeEach(() => {
        resetSecondFactor.mockReset().mockResolvedValue(undefined)
        listStationMemberStatus.mockReset().mockResolvedValue([LENA])
    })
    afterEach(() => holdShift(false))

    it('asks before resetting a member\'s second factor', async () => {
        const wrapper = await securityView()

        wrapper.findComponent(MembersPanel).vm.$emit('reset', LENA)
        await flushPromises()

        expect(wrapper.findComponent(ConfirmDeleteModal).props('modelValue')).toBe(true)
        expect(resetSecondFactor).not.toHaveBeenCalled()

        wrapper.findComponent(ConfirmDeleteModal).vm.$emit('confirm')
        await flushPromises()
        expect(resetSecondFactor).toHaveBeenCalledWith(42)
    })

    it('resets at once while shift is held and reads the page back', async () => {
        const wrapper = await securityView()
        listStationMemberStatus.mockClear()

        holdShift(true)
        wrapper.findComponent(MembersPanel).vm.$emit('reset', LENA)
        await flushPromises()

        expect(resetSecondFactor).toHaveBeenCalledWith(42)
        expect(listStationMemberStatus).toHaveBeenCalled()
        expect(wrapper.findComponent(ConfirmDeleteModal).props('modelValue')).toBe(false)
    })
})
