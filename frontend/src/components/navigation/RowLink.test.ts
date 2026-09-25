/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import RowLink from './RowLink.vue'

/**
 * A row that opens a page is a link, and the three things that has to mean.
 *
 * @vitest-environment happy-dom
 *
 * <p>The middle one is why this component exists rather than an anchor written out at each call
 * site: a button inside a link navigates unless somebody prevents it, and `@click.stop`, which is
 * what every row in the product already writes, does not.
 */
describe('RowLink', () => {
    const NuxtLink = {
        props: ['to'],
        template: '<a :href="typeof to === \'string\' ? to : to?.name"><slot/></a>',
    }

    function row(props: Record<string, unknown>, inner = '<span>Anhänger</span>') {
        return mount(RowLink, {
            props,
            slots: {default: inner},
            global: {stubs: {NuxtLink}},
        })
    }

    it('renders an address a reader can copy, open in a tab or reach with the keyboard', () => {
        const link = row({to: '/station/inventory/7'})

        expect(link.find('a').exists()).toBe(true)
        expect(link.find('a').attributes('href')).toBe('/station/inventory/7')
        expect(link.text()).toContain('Anhänger')
    })

    it('leaves a button inside the row to itself rather than opening the page as well', async () => {
        const link = row({to: '/station/inventory/7'}, '<span>Anhänger</span><button>Löschen</button>')

        const press = new MouseEvent('click', {bubbles: true, cancelable: true})
        link.find('button').element.dispatchEvent(press)

        expect(press.defaultPrevented).toBe(true)
    })

    /**
     * Refusing the press has to happen before the link acts on it, not merely before the browser
     * does. The link pushes the route from a handler of its own, and a handler a component is given
     * from outside is added after the one it rendered with, so a refusal written the ordinary way
     * arrives second: the row would delete and open the page anyway.
     *
     * <p>What the test watches is the button itself. A press is answered there after anything
     * listening on the way down and before anything listening on the way up, and the link listens
     * on the way up, so a press already refused by the time it reaches the button is a press the
     * link cannot act on.
     */
    it('refuses the press before the link is in a position to follow it', () => {
        const link = row({to: '/station/inventory/7'}, '<span>Anhänger</span><button>Löschen</button>')
        const button = link.find('button').element

        let refusedAlready: boolean | null = null
        button.addEventListener('click', event => {
            refusedAlready = event.defaultPrevented
        })
        button.dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true}))

        expect(refusedAlready).toBe(true)
    })

    it('opens the page when the row itself is pressed', () => {
        const link = row({to: '/station/inventory/7'})

        const press = new MouseEvent('click', {bubbles: true, cancelable: true})
        link.find('span').element.dispatchEvent(press)

        expect(press.defaultPrevented).toBe(false)
    })

    it('renders no link at all where the reader may not open the page', () => {
        const link = row({to: null})

        expect(link.find('a').exists()).toBe(false)
        expect(link.text()).toContain('Anhänger')
    })

    /**
     * The row carries the card's own look, so the global link colour and underline must not reach
     * it.
     *
     * <p>`row-link` is the one that actually holds. The rule painting links is written outside
     * Tailwind's layers and so beats a utility whatever the specificity, which left a row looking
     * like a link unless it happened to contain a button. `style.css` names this class as the
     * exception, so losing it here would repaint every row in the product.
     */
    it('does not paint itself as a link', () => {
        const link = row({to: '/station/inventory/7'})

        const classes = link.find('a').classes()
        expect(classes).toContain('row-link')
        expect(classes).toContain('no-underline')
        expect(classes).toContain('text-inherit')
    })
})
