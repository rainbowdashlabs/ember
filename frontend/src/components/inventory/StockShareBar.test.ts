/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import StockShareBar from './StockShareBar.vue'
import de from '@/i18n/de-DE'

/**
 * The strip beside a size, which answers "is there one free" before any number is read.
 *
 * <p>Two things have to hold for it to be worth looking at: the three shares are the whole line and
 * nothing more, and a size nobody keeps draws nothing rather than a full or an empty line.
 */
describe('StockShareBar', () => {
    const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': de}})

    function bar(props: {total: number; free: number; assigned: number}) {
        return mount(StockShareBar, {props, global: {plugins: [i18n]}})
    }

    function widthOf(strip: ReturnType<typeof bar>, part: string): string {
        const found = strip.find(`[data-testid="size-share-${part}"]`)
        return found.exists() ? (found.element as HTMLElement).style.width : ''
    }

    it('divides the line into free, assigned and what is neither', () => {
        const strip = bar({total: 10, free: 5, assigned: 3})

        expect(widthOf(strip, 'free')).toBe('50%')
        expect(widthOf(strip, 'assigned')).toBe('30%')
        expect(widthOf(strip, 'away')).toBe('20%')
    })

    it('gives what neither share covers to the third, so the strip is the whole line', () => {
        const strip = bar({total: 4, free: 4, assigned: 0})

        expect(widthOf(strip, 'free')).toBe('100%')
        expect(strip.find('[data-testid="size-share-assigned"]').exists()).toBe(false)
        expect(strip.find('[data-testid="size-share-away"]').exists()).toBe(false)
    })

    it('draws nothing for a size nobody keeps rather than a full or an empty line', () => {
        const strip = bar({total: 0, free: 0, assigned: 0})

        expect(strip.find('[data-testid="size-share-bar"]').exists()).toBe(true)
        expect(strip.find('[data-testid="size-share-free"]').exists()).toBe(false)
        expect(strip.find('[data-testid="size-share-assigned"]').exists()).toBe(false)
        expect(strip.find('[data-testid="size-share-away"]').exists()).toBe(false)
        expect(strip.get('[data-testid="size-share-bar"]').attributes('aria-label'))
            .toBe(de.inventory.detail.shareBarEmpty)
    })

    it('carries the three counts in words, so colour is not the only thing that says them', () => {
        const strip = bar({total: 10, free: 5, assigned: 3})
        const label = strip.get('[data-testid="size-share-bar"]').attributes('aria-label')

        expect(label).toContain('5')
        expect(label).toContain('3')
        expect(label).toContain('2')
        expect(strip.get('[data-testid="size-share-bar"]').attributes('title')).toBe(label)
    })
})
