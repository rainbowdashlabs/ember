/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import CatalogCreateImportHelp from './CatalogCreateImportHelp.vue'

/**
 * Two texts reach the reader as one run-on sentence when both of the elements carrying them are
 * inline and nothing at all is left standing between them. Vue removes the whitespace between two
 * elements when it holds a line break, so lines written one under the other in the template are
 * exactly the ones this happens to, and the vertical spacing of the surrounding container never
 * makes up for it: it does not apply to inline boxes.
 */
function textsRunTogether(first: Element, second: Element): boolean {
    return first.tagName === 'SPAN' && second.tagName === 'SPAN' && first.nextSibling === second
}

describe('CatalogCreateImportHelp', () => {
    it('shows the two rejected questions as two lines rather than as one sentence', async () => {
        const article = await mountSuspended(CatalogCreateImportHelp)

        const rejected = article.get('[data-testid="quiz-import-rejected"]').element
        const lines = [...rejected.children].filter(line => line.textContent?.startsWith('questions['))

        expect(lines).toHaveLength(2)
        expect(textsRunTogether(lines[0]!, lines[1]!)).toBe(false)
    })
})
