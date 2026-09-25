/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {isConstantTitle, viewContentTitle, writesItsOwnHead} from './lint-context-titles.mjs'

/**
 * Telling a title that can name what a page shows from one that never could.
 *
 * @vitest-environment happy-dom
 *
 * <p>The whole rule rests on this one judgement, and both halves of it are easy to get wrong in a
 * way that only shows much later: too eager and it refuses the constant a page rightly falls back
 * to while its record is on its way, too shy and every catalogue in the product goes on being
 * called "Fragenkatalog".
 */
describe('a title that says which thing', () => {
    it('reads a bound title off the opening tag, however many lines it is written over', () => {
        const found = viewContentTitle(`
<template>
    <ViewContent
        :title="pageTitle"
        :subtitle="pageSubtitle"
    >
        <p>Hallo</p>
    </ViewContent>
</template>
`)

        expect(found).toEqual({expression: 'pageTitle', written: false})
    })

    it('reads a title written out rather than bound', () => {
        const found = viewContentTitle('<template><ViewContent title="Fragenkatalog"/></template>')

        expect(found).toEqual({expression: 'Fragenkatalog', written: true})
    })

    it('finds nothing where the page hands no title', () => {
        expect(viewContentTitle('<template><div>Hallo</div></template>')).toBe(null)
    })

    /**
     * A public page's heading and its tab are two different strings, and only the head holds the
     * second. Reading the heading there would refuse "Neuigkeiten", which is what that page should
     * be headed.
     */
    it('stands aside where the page names its own tab', () => {
        expect(writesItsOwnHead('useHead(computed(() => ({title: titleWithStation(f.name, s))))')).toBe(true)
        expect(writesItsOwnHead('const pageTitle = computed(() => file.value?.name)')).toBe(false)
    })

    function bound(expression: string) {
        return {expression, written: false}
    }

    it('calls a translated constant what it is', () => {
        expect(isConstantTitle(bound("t('pages.quiz-catalog-detail.title')"))).toBe(true)
    })

    /** A title written out rather than bound cannot change, whatever it happens to say. */
    it('calls a written out title a constant whatever is in it', () => {
        expect(isConstantTitle({expression: 'Fragenkatalog', written: true})).toBe(true)
    })

    /** The shape every converted page uses: the record where there is one, the constant until then. */
    it('accepts a title falling back to the constant while the record is on its way', () => {
        expect(isConstantTitle(bound("catalog?.name || t('pages.quiz-catalog-detail.title')"))).toBe(false)
    })

    it('accepts a title built out of more than one thing the page loaded', () => {
        expect(isConstantTitle(bound('`${board.shortKey}-${ticket.ticketNumber} ${ticket.title}`'))).toBe(false)
    })

    /** A constant is a constant however it is spelled, so a joined one must not pass for a name. */
    it('is not fooled by a constant spelled as two strings', () => {
        expect(isConstantTitle(bound("'Frage' + 'nkatalog'"))).toBe(true)
    })
})
