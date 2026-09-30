/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {queryTokens, scoreItemText, type ItemSearchText} from './itemRanking'

function text(over: Partial<ItemSearchText> = {}): ItemSearchText {
    return {id: 'p-11', name: 'handschuhe', size: 'l', inventory: 'schutzausrüstung', location: 'regal 3', ...over}
}

/**
 * How a search over the gear orders what it finds.
 *
 * @vitest-environment node
 */
describe('scoreItemText', () => {
    it('puts the printed number above a name that starts with the same letters', () => {
        const byNumber = scoreItemText(text({id: 'hand'}), ['hand'])
        const byName = scoreItemText(text(), ['hand'])

        expect(byNumber).toBeGreaterThan(byName)
    })

    it('drops a piece that one word of the query does not match', () => {
        expect(scoreItemText(text(), ['handschuhe', 'helm'])).toBe(-1)
    })

    it('adds up every word that matches', () => {
        expect(scoreItemText(text(), ['handschuhe', 'regal'])).toBe(100 + 20)
    })
})

/**
 * @vitest-environment node
 */
describe('queryTokens', () => {
    it('splits on blanks and lower-cases', () => {
        expect(queryTokens('  Hand  Schuhe ')).toEqual(['hand', 'schuhe'])
    })
})
