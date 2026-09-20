/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {downloadAuthed, parseContentDispositionFilename} from './downloadAuthed'

const get = vi.fn()
const presented = vi.fn()

vi.mock('@/api/client', () => ({default: {get: (url: string, options: unknown) => get(url, options)}}))
vi.mock('@/util/documentView', () => ({
    presentDocument: (blob: Blob, filename: string, mimeType?: string) => presented(blob, filename, mimeType),
}))

function answers(blob: Blob, headers: Record<string, string> = {}) {
    get.mockResolvedValue({data: blob, headers})
}

/**
 * What a download does with what it fetched.
 *
 * <p>It hands the bytes over rather than saving them itself, which is what lets a slow one work on a
 * phone: the share sheet is granted only to a press the reader has just made, and a large file
 * outlives the press that started it. Whoever it hands to decides between showing and saving.
 */
describe('downloadAuthed', () => {
    beforeEach(() => {
        get.mockReset()
        presented.mockReset()
    })

    it('hands what it fetched over to be shown or saved', async () => {
        const blob = new Blob(['%PDF'])
        answers(blob)

        await downloadAuthed('/kb/files/42/original', 'handout.pdf')

        expect(presented).toHaveBeenCalledWith(blob, 'handout.pdf', undefined)
    })

    /** The bytes of a download carry no type of their own, so the response has to say what it sent. */
    it('takes the type from the response rather than from the bytes', async () => {
        answers(new Blob(['%PDF']), {'content-type': 'application/pdf;charset=utf-8'})

        await downloadAuthed('/kb/files/42/original', 'handout.pdf')

        expect(presented).toHaveBeenCalledWith(expect.anything(), 'handout.pdf', 'application/pdf')
    })

    it('falls back to the name the response gives, and then to the address', async () => {
        answers(new Blob(['x']), {'content-disposition': 'attachment; filename="Bericht 2026.pdf"'})
        await downloadAuthed('/kb/files/42/original')
        expect(presented.mock.calls[0]?.[1]).toBe('Bericht 2026.pdf')

        presented.mockReset()
        answers(new Blob(['x']))
        await downloadAuthed('/kb/files/42/original')
        expect(presented.mock.calls[0]?.[1]).toBe('original')
    })
})

describe('parseContentDispositionFilename', () => {
    it('prefers the encoded name, which is the one that carries umlauts', () => {
        expect(parseContentDispositionFilename("attachment; filename*=UTF-8''Pr%C3%BCfung.pdf"))
            .toBe('Prüfung.pdf')
    })

    it('reads the plain name where there is no encoded one', () => {
        expect(parseContentDispositionFilename('attachment; filename="report.pdf"')).toBe('report.pdf')
    })

    it('answers nothing for a header that names no file', () => {
        expect(parseContentDispositionFilename('attachment')).toBeNull()
        expect(parseContentDispositionFilename(null)).toBeNull()
    })
})
