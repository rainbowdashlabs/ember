/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import {describe, expect, it} from 'vitest'
import LinkButton from '@/components/button/LinkButton.vue'
import OverviewBlock from './OverviewBlock.vue'

const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}, en: {}}, missingWarn: false, fallbackWarn: false})

function block(count: number, whole = false) {
    return mount(OverviewBlock, {
        props: {title: 'Zu beantworten', count, empty: 'leer', whole},
        slots: {default: `<template #default="{limit}"><li v-for="n in Math.min(limit, ${count})" :key="n">x</li></template>`},
        global: {plugins: [i18n]},
    })
}

/** A block shows five rows and opens to all of them in place. */
describe('OverviewBlock', () => {
    it('shows five rows of a longer block and all of them once opened', async () => {
        const wrapper = block(7)
        expect(wrapper.findAll('li')).toHaveLength(5)

        await wrapper.findComponent(LinkButton).trigger('click')
        expect(wrapper.findAll('li')).toHaveLength(7)
    })

    it('shows a whole block at once, and offers nothing to open on a short one', () => {
        expect(block(7, true).findAll('li')).toHaveLength(7)
        expect(block(3).findComponent(LinkButton).exists()).toBe(false)
        expect(block(0).text()).toContain('leer')
    })
})
