/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {genericErrorLines} from './lint-generic-errors.mjs'

/**
 * Catching a screen that answers a failure with nothing.
 *
 * @vitest-environment happy-dom
 *
 * <p>The rule is worth a test of its own because it is the only thing standing between this sweep
 * and the sentence growing straight back: two hundred and seventeen files reached for it before
 * anybody looked, and every one of them was written by somebody solving a different problem.
 */
describe('a failure answered with nothing', () => {
    it('reports a catch that writes the generic sentence', () => {
        const found = genericErrorLines(`
async function save() {
    try {
        await api.save()
    } catch {
        error.value = t('common.error')
    }
}
`)

        expect(found).toHaveLength(1)
        expect(found[0].line).toBe(6)
    })

    it('reads it however the quotes are written', () => {
        expect(genericErrorLines(`error.value = t("common.error")`)).toHaveLength(1)
        expect(genericErrorLines(`error.value = t( 'common.error' )`)).toHaveLength(1)
    })

    it('reports every line, because a file usually has more than one', () => {
        expect(genericErrorLines(`
error.value = t('common.error')
other.value = t('common.error')
`)).toHaveLength(2)
    })

    /** The whole point is that the described failure replaces it, so that shape must pass. */
    it('leaves a described failure alone', () => {
        expect(genericErrorLines(`failure.value = describeFailure(e, t)`)).toEqual([])
    })

    /** A key that merely starts the same way is a different key. */
    it('is not fooled by a neighbouring key', () => {
        expect(genericErrorLines(`error.value = t('common.errorTitle')`)).toEqual([])
    })
})
