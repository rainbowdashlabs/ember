/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import MailReplyToField from './MailReplyToField.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'

const api = vi.hoisted(() => ({
    getStationReplyTo: vi.fn(),
    saveStationReplyTo: vi.fn(),
}))

vi.mock('@/api/instanceMail', () => api)

/** Where replies to a station's mail go, whichever provider carried the mail. */
describe('MailReplyToField', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        api.getStationReplyTo.mockResolvedValue({replyTo: 'kontakt@nord.test'})
    })

    it('saves the reply address as typed, without the spaces around it', async () => {
        api.saveStationReplyTo.mockResolvedValue({replyTo: 'neu@nord.test'})
        const view = mount(MailReplyToField)
        await flushPromises()

        expect(view.findComponent(TextInput).props('modelValue')).toBe('kontakt@nord.test')
        view.findComponent(TextInput).vm.$emit('update:modelValue', ' neu@nord.test ')
        await view.findComponent(SaveButton).props('action')()

        expect(api.saveStationReplyTo).toHaveBeenCalledWith('neu@nord.test')
    })
})
