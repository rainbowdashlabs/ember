/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it, vi} from 'vitest'
import type {BeaconStatus} from '@/api/generated/schema'
import BeaconSettingsPanel from './BeaconSettingsPanel.vue'

vi.mock('@/api', () => ({beacon: {updateSettings: vi.fn()}}))

function status(forwardProblems: boolean): BeaconStatus {
    return {
        enabled: true, url: 'https://beacon.test', forwardProblems, forwardReports: false, forwardLogContext: false,
        reviewReportPictures: true, metricsEnabled: false, receiving: false, contactName: '', contactMail: '',
    }
}

/** Sending the log lines along is a part of forwarding faults, and can be switched only while that is on. */
describe('BeaconSettingsPanel', () => {
    it('offers the lines only while faults are forwarded', () => {
        const off = mount(BeaconSettingsPanel, {props: {status: status(false)}})
        const on = mount(BeaconSettingsPanel, {props: {status: status(true)}})

        expect(off.find('[data-testid="beacon-forward-log-context"]').attributes('disabled')).toBeDefined()
        expect(on.find('[data-testid="beacon-forward-log-context"]').attributes('disabled')).toBeUndefined()
        expect(on.find('[data-testid="beacon-forward-log-context"]').attributes('aria-describedby'))
            .toBe('beacon-forward-log-context-hint')
    })
})
