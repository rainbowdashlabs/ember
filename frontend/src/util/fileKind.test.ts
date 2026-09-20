/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {canHavePicture, fileKindIcon, fileKindOf} from './fileKind'

/**
 * The one opinion about what a file is, which four places used to hold separately and disagree on.
 *
 * <p>What hangs on it is whether a file is drawn at all and as what, so a kind answered differently
 * in two places is a file that previews in one screen and not in another.
 */

describe('fileKindOf', () => {
    it.each([
        ['image/png', 'image'],
        ['image/svg+xml', 'image'],
        ['application/pdf', 'pdf'],
        ['video/mp4', 'video'],
        ['audio/mpeg', 'audio'],
        ['text/plain', 'text'],
        ['text/csv', 'text'],
        ['application/zip', 'other'],
    ])('reads %s as %s', (mime, kind) => {
        expect(fileKindOf(mime)).toBe(kind)
    })

    it('reads a file with no type at all as one it cannot draw', () => {
        expect(fileKindOf(null)).toBe('other')
        expect(fileKindOf(undefined)).toBe('other')
    })

    it('does not care how the type was capitalised', () => {
        expect(fileKindOf('Application/PDF')).toBe('pdf')
        expect(fileKindOf('IMAGE/PNG')).toBe('image')
    })
})

describe('canHavePicture', () => {
    it('asks the server for a picture only where one exists', () => {
        expect(canHavePicture('image/png')).toBe(true)
        expect(canHavePicture('application/pdf')).toBe(true)
        expect(canHavePicture('video/mp4')).toBe(false)
        expect(canHavePicture('application/zip')).toBe(false)
    })
})

describe('fileKindIcon', () => {
    /** A spreadsheet and a log file are both text on screen, and a reader still wants them apart. */
    it('tells apart the types worth naming more finely than they are drawn', () => {
        expect(fileKindIcon('text/csv')).toEqual(['fas', 'file-csv'])
        expect(fileKindIcon('application/vnd.oasis.opendocument.presentation'))
            .toEqual(['fas', 'file-powerpoint'])
        expect(fileKindIcon('text/plain')).toEqual(['fas', 'file-lines'])
    })

    it('falls back to the kind for everything else', () => {
        expect(fileKindIcon('image/png')).toEqual(['fas', 'image'])
        expect(fileKindIcon('application/pdf')).toEqual(['fas', 'file-pdf'])
        expect(fileKindIcon(null)).toEqual(['fas', 'file'])
    })
})
