/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {witnessSigningHref} from './witnessSigning'

/** Every way to a field signed in person leads to the one witness page, opened on that field. */
describe('witnessSigningHref', () => {
    it('opens the witness page on the field', () => {
        expect(witnessSigningHref(41)).toBe('/station/signing/witness?field=41')
    })
})
