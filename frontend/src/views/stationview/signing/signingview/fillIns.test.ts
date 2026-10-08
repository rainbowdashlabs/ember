/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import type {FillInResponse} from '@/api/generated/schema'
import {fillInEntries, fillInsComplete} from './fillIns'

const phone: FillInResponse = {name: 'fill-participant-0', label: 'Telefon', required: true, maxLength: 10}
const allergies: FillInResponse = {name: 'fill-participant-1', label: 'Allergien', required: false, maxLength: 500}

describe('fields to fill in when signing', () => {
    it('sends the filled-in fields exactly as typed, in the document\'s order, and leaves empty ones out', () => {
        expect(fillInEntries([phone, allergies], {[allergies.name]: ' Nüsse ', [phone.name]: '0171'}))
            .toEqual([{field: phone.name, value: '0171'}, {field: allergies.name, value: ' Nüsse '}])
        expect(fillInEntries([phone, allergies], {[phone.name]: '0171', [allergies.name]: '   '}))
            .toEqual([{field: phone.name, value: '0171'}])
    })

    it('is complete once every required field is filled in and no value is too long', () => {
        expect(fillInsComplete([], {})).toBe(true)
        expect(fillInsComplete([phone, allergies], {})).toBe(false)
        expect(fillInsComplete([phone, allergies], {[phone.name]: '  '})).toBe(false)
        expect(fillInsComplete([phone, allergies], {[phone.name]: '0171'})).toBe(true)
        expect(fillInsComplete([phone], {[phone.name]: '01234567890'})).toBe(false)
    })
})
