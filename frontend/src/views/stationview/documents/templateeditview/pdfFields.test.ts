/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {PdfFieldKind, SignatureRole} from '@/api/generated/schema'
import {freeSigner, movedBy, newField, resizedBy} from './pdfFields'
import type {PageGeometry} from './pdfViewport'

/** An A4 page shown at twice its size, and the same page turned a quarter clockwise. */
const upright: PageGeometry = {transform: [2, 0, 0, -2, 0, 1684], width: 1190, height: 1684}
const turned: PageGeometry = {transform: [0, 2, 2, 0, 0, 0], width: 1684, height: 1190}

describe('PDF fields in the editor', () => {
    it('places a new field in the middle of the page shown', () => {
        const field = newField(PdfFieldKind.TEXT, 2, upright, [])

        expect(field.rect).toEqual({page: 2, x: 217.5, y: 413, width: 160, height: 16})
        expect(field.text).toBe('')
        expect(field.role).toBeNull()
    })

    it('places a new field upright to the reader on a turned page', () => {
        const field = newField(PdfFieldKind.TEXT, 1, turned, [])

        expect(field.rect.width).toBe(16)
        expect(field.rect.height).toBe(160)
    })

    it('gives a new signature field the first signer without one', () => {
        const first = newField(PdfFieldKind.SIGNATURE, 1, upright, [])
        const second = newField(PdfFieldKind.SIGNATURE, 1, upright, [first])

        expect(first.role).toBe(SignatureRole.PARTICIPANT)
        expect(second.role).toBe(SignatureRole.GUARDIAN_1)
        expect(first.text).toBeNull()
        const four = [
            {...first, role: SignatureRole.PARTICIPANT},
            {...first, role: SignatureRole.GUARDIAN_1},
            {...first, role: SignatureRole.GUARDIAN_2},
            {...first, role: SignatureRole.ISSUER},
        ]
        expect(freeSigner(four)).toBe(SignatureRole.ANY_GUARDIAN)
        expect(freeSigner([...four, {...first, role: SignatureRole.ANY_GUARDIAN}])).toBeNull()
    })

    it('never offers every guardian beside the first or the second guardian', () => {
        const field = newField(PdfFieldKind.SIGNATURE, 1, upright, [])

        expect(freeSigner([
            {...field, role: SignatureRole.PARTICIPANT},
            {...field, role: SignatureRole.ISSUER},
            {...field, role: SignatureRole.ANY_GUARDIAN},
            {...field, role: SignatureRole.EACH_GUARDIAN},
        ])).toBeNull()
    })

    it('moves a field by what was dragged on the canvas and keeps it on the page', () => {
        const field = newField(PdfFieldKind.CHECK, 1, upright, [])

        const moved = movedBy(upright, field, 20, -40)
        const pushedOff = movedBy(upright, field, -5000, 0)

        expect(moved.rect.x).toBe(field.rect.x + 10)
        expect(moved.rect.y).toBe(field.rect.y + 20)
        expect(pushedOff.rect.x).toBe(0)
    })

    it('resizes a field from its bottom right, its top left staying put', () => {
        const field = newField(PdfFieldKind.TEXT, 1, upright, [])

        const resized = resizedBy(upright, field, 40, 20)

        expect(resized.rect.width).toBe(180)
        expect(resized.rect.height).toBe(26)
        expect(resized.rect.y + resized.rect.height).toBe(field.rect.y + field.rect.height)
    })
})
