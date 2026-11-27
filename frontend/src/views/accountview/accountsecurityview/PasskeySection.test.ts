/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, mount} from '@vue/test-utils'
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest'
import type {PasskeyEntryResponse, PasskeysStatusResponse} from '@/api/generated/schema'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {followModifierKeys} from '@/util/modifierKeys'
import PasskeyList from './PasskeyList.vue'
import PasskeySection from './PasskeySection.vue'

const removePasskey = vi.fn()
vi.mock('@/api/passkeys', () => ({
    getPasskeysStatus: async () => STATUS,
    removePasskey: (id: number) => removePasskey(id),
    renamePasskey: vi.fn(),
    setAskWithPassword: vi.fn(),
    setPasswordLogin: vi.fn(),
}))
vi.mock('@/util/webauthn', () => ({
    isWebAuthnSupported: () => true,
    signalAcceptedCredentials: async () => {},
}))

const KEY = {id: 3, label: 'Laptop'} as PasskeyEntryResponse
const STATUS = {mode: 'OPTIONAL', passkeys: [KEY]} as unknown as PasskeysStatusResponse

async function section() {
    const wrapper = mount(PasskeySection, {
        global: {stubs: {RouterLink: true, PasskeyCreateModal: true, PasskeyRenameModal: true, PasskeySwitches: true}},
    })
    await flushPromises()
    return wrapper
}

function holdShift(held: boolean) {
    window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

/** Removing a passkey asks first, shift skips the question, and the last way in is refused in words. */
describe('PasskeySection', () => {
    beforeAll(() => followModifierKeys())
    beforeEach(() => {
        removePasskey.mockReset().mockResolvedValue({passwordLoginReenabled: false})
    })
    afterEach(() => holdShift(false))

    it('asks before removing a passkey', async () => {
        const wrapper = await section()

        wrapper.findComponent(PasskeyList).vm.$emit('remove', KEY)
        await wrapper.vm.$nextTick()

        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(true)
        expect(removePasskey).not.toHaveBeenCalled()

        wrapper.findComponent({name: 'ConfirmDeleteModal'}).vm.$emit('confirm')
        await vi.waitFor(() => expect(removePasskey).toHaveBeenCalledWith(3))
    })

    it('removes at once while shift is held', async () => {
        const wrapper = await section()

        holdShift(true)
        wrapper.findComponent(PasskeyList).vm.$emit('remove', KEY)

        await vi.waitFor(() => expect(removePasskey).toHaveBeenCalledWith(3))
        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(false)
    })

    /** The last passkey of an account without a password stays, and the alert shows what to do first. */
    it('names the refusal of the last way into the account', async () => {
        removePasskey.mockRejectedValue({response: {status: 409, data: {code: 'PK-031'}}})
        const wrapper = await section()

        holdShift(true)
        wrapper.findComponent(PasskeyList).vm.$emit('remove', KEY)

        await vi.waitFor(() => expect(wrapper.findComponent(FailureAlert).props('failure')).not.toBeNull())
        const failure = wrapper.findComponent(FailureAlert).props('failure')
        expect(failure?.message).toContain('einzige Weg in dieses Konto')
        expect(failure?.reportable).toBe(false)
    })
})
