/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import Alert from './Alert.vue'
import FailureAlert from './FailureAlert.vue'

/**
 * What a screen reader is told a banner is: an error interrupts, anything else waits its turn.
 *
 * @vitest-environment happy-dom
 */
describe('Alert', () => {
    it('is an alert when it reports an error', () => {
        expect(mount(Alert, {props: {variant: 'error'}}).attributes('role')).toBe('alert')
    })

    it.each(['info', 'success'] as const)('is a status when it is %s', (variant) => {
        expect(mount(Alert, {props: {variant}}).attributes('role')).toBe('status')
    })

    it('makes a failure an alert', () => {
        const wrapper = mount(FailureAlert, {props: {message: 'Die Datei ist zu groß.', expected: true}})

        expect(wrapper.find('[role="alert"]').text()).toContain('Die Datei ist zu groß.')
    })
})
