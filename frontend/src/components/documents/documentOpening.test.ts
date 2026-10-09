/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {documentOpeningOf} from './documentOpening'

describe('documentOpeningOf', () => {
    it('opens the document a copy mail links to', () => {
        expect(documentOpeningOf({document: '42'})).toEqual({documentId: 42, record: null})
    })

    it('opens the record of the version a copy mail links to', () => {
        expect(documentOpeningOf({document: '42', record: '3'})).toEqual({documentId: 42, record: 3})
    })

    it('opens nothing without a document', () => {
        expect(documentOpeningOf({})).toBeNull()
        expect(documentOpeningOf({record: '3'})).toBeNull()
    })

    it('ignores ids that are not positive whole numbers', () => {
        expect(documentOpeningOf({document: 'abc'})).toBeNull()
        expect(documentOpeningOf({document: '0'})).toBeNull()
        expect(documentOpeningOf({document: '1.5'})).toBeNull()
        expect(documentOpeningOf({document: '7', record: '-1'})).toEqual({documentId: 7, record: null})
    })

    it('ignores a value given more than once', () => {
        expect(documentOpeningOf({document: ['4', '5']})).toBeNull()
    })
})
