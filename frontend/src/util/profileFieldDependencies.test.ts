/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {hiddenByDependency, shownFields, type DependentField} from './profileFields'

const JULEICA: DependentField = {id: 1, origin: 'STATION'}
const EXPIRY: DependentField = {id: 2, origin: 'STATION', shownIfFieldId: 1}

/**
 * A question behind a yes/no question shows only once that one is answered yes, and only where both are on
 * the same form.
 */
describe('profile field dependencies', () => {
    it('hides the dependent question until the yes/no question is answered yes', () => {
        const fields = [JULEICA, EXPIRY]

        expect(hiddenByDependency(EXPIRY, fields, () => '')).toBe(true)
        expect(hiddenByDependency(EXPIRY, fields, () => 'false')).toBe(true)
        expect(hiddenByDependency(EXPIRY, fields, field => field.id === 1 ? 'true' : '')).toBe(false)
        expect(hiddenByDependency(EXPIRY, fields, field => field.id === 1 ? true : '')).toBe(false)
    })

    it('shows the dependent question as usual where the yes/no question is not on the form', () => {
        expect(hiddenByDependency(EXPIRY, [EXPIRY], () => '')).toBe(false)
    })

    it('only counts a yes/no question of the same owner', () => {
        const associationsOwn: DependentField = {id: 1, origin: 'CLUSTER'}

        expect(hiddenByDependency(EXPIRY, [associationsOwn, EXPIRY], () => '')).toBe(false)
    })

    it('leaves the hidden questions out of the form', () => {
        expect(shownFields([JULEICA, EXPIRY], () => 'false')).toEqual([JULEICA])
    })
})
