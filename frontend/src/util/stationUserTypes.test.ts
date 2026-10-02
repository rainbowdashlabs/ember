/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {userTypesOf} from './stationUserTypes'

describe('userTypesOf', () => {
    it('keeps the user types the server knows, in their order', () => {
        expect(userTypesOf(['TEAM', 'MEMBER'])).toEqual(['TEAM', 'MEMBER'])
    })

    it('leaves out anything else', () => {
        expect(userTypesOf(['MEMBER', 'USER', ''])).toEqual(['MEMBER'])
    })
})
