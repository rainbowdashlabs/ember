/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {draftBase64, draftBlob} from './signatureDraft'

/** A picture drawn on the screen goes out as its bare Base64 for a signing act and as PNG bytes for an upload. */
describe('signatureDraft', () => {
    const draft = {dataUrl: 'data:image/png;base64,iVBORw0K', source: 'DRAWN' as const}

    it('drops the data address in front of the Base64', () => {
        expect(draftBase64(draft)).toBe('iVBORw0K')
    })

    it('turns the Base64 back into PNG bytes', async () => {
        const blob = draftBlob(draft)

        expect(blob.type).toBe('image/png')
        const bytes = new Uint8Array(await blob.arrayBuffer())
        expect(Array.from(bytes.slice(0, 4))).toEqual([0x89, 0x50, 0x4E, 0x47])
    })
})
