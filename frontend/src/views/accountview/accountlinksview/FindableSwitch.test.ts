/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import FindableSwitch from './FindableSwitch.vue'
import * as session from '@/api/session'

vi.mock('@/api/session', () => ({getFindable: vi.fn(async () => false), setFindable: vi.fn(async () => undefined)}))

/** The reader's own choice whether every station of the instance can find them by name. */
describe('FindableSwitch', () => {
    it('shows the stored choice and stores a new one', async () => {
        const wrapper = mount(FindableSwitch)
        await flushPromises()

        const toggle = wrapper.get('[role="switch"]')
        expect(toggle.attributes('aria-checked')).toBe('false')
        await toggle.trigger('click')
        await flushPromises()

        expect(session.setFindable).toHaveBeenCalledWith(true)
        expect(wrapper.get('[role="switch"]').attributes('aria-checked')).toBe('true')
    })
})
