/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {hydrated} from './fixtures/hydrated'

/**
 * A stranger checking a PDF on the public page. No session: the page is there for whoever received a
 * document and wants to know where it came from.
 */
test.describe('Checking a document', () => {
    test('a PDF without a seal is answered as having none', async ({page}) => {
        await page.goto('/verify')
        await hydrated(page)

        await page.locator('input[type="file"]').setInputFiles({
            name: 'aushang.pdf',
            mimeType: 'application/pdf',
            buffer: onePagePdf(),
        })

        await expect(page.getByTestId('seal-none')).toBeVisible()
        await expect(page.getByTestId('seal-check')).toHaveCount(0)
        await expect(page.getByTestId('seal-held-copy')).toHaveAttribute('data-held', 'false')
    })
})

/**
 * One empty page with a cross-reference table whose offsets are right, so the file is read as it is
 * rather than repaired, whatever the reader on the other end does with a broken one.
 */
function onePagePdf(): Buffer {
    const objects = [
        '<</Type/Catalog/Pages 2 0 R>>',
        '<</Type/Pages/Kids[3 0 R]/Count 1>>',
        '<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>',
    ]
    let file = '%PDF-1.4\n'
    const offsets: number[] = []
    objects.forEach((object, index) => {
        offsets.push(file.length)
        file += `${index + 1} 0 obj${object}endobj\n`
    })
    const crossReference = file.length
    file += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`
    file += offsets.map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('')
    file += `trailer<</Size ${objects.length + 1}/Root 1 0 R>>\nstartxref\n${crossReference}\n%%EOF\n`
    return Buffer.from(file, 'latin1')
}
