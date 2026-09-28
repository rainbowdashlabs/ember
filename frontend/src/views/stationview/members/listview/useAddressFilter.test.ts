/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {addressFilterOf} from './useAddressFilter'

/**
 * The filter a member list address names, which is where member management's expiry reminder leads.
 *
 * @vitest-environment happy-dom
 */
describe('addressFilterOf', () => {
    it('reads the field and the states a reminder links to', () => {
        expect(addressFilterOf({field: '12', state: 'expiring,expired'}))
            .toEqual({field: '12', states: ['EXPIRING', 'EXPIRED']})
    })

    it('reads nothing without a field or a state it knows', () => {
        expect(addressFilterOf({state: 'expired'})).toBeNull()
        expect(addressFilterOf({field: '12'})).toBeNull()
        expect(addressFilterOf({field: '12', state: 'someday'})).toBeNull()
    })
})
