/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {PlaceholderCategory} from '@/api/generated/schema'
import {CATALOGUE} from './fixtures'
import PlaceholderPicker from './PlaceholderPicker.vue'

/**
 * The picker walks a placeholder's path from the categories at the top, goes back along the steps it
 * took, searches across every step, and starts over at the top once something was picked.
 */
describe('PlaceholderPicker', () => {
    function picker(props: {legal?: boolean, signatures?: boolean} = {}) {
        return mount(PlaceholderPicker, {props: {placeholders: CATALOGUE, legal: false, ...props}})
    }

    function entries(wrapper: ReturnType<typeof picker>) {
        return wrapper.findAll('[data-testid="placeholder-picker-level"] li').map(entry => entry.text())
    }

    function steps(wrapper: ReturnType<typeof picker>) {
        return wrapper.findAll('[data-testid="placeholder-picker-path"] button').map(step => step.text())
    }

    async function choose(wrapper: ReturnType<typeof picker>, name: string) {
        const entry = wrapper.findAll('[data-testid="placeholder-picker-level"] button').find(button => button.text() === name)
        await entry?.trigger('click')
    }

    it('shows the categories at the top and nothing below them yet', () => {
        const wrapper = picker()

        expect(wrapper.find(`[data-testid="placeholder-category-${PlaceholderCategory.MEMBER}"]`).text()).toBe('Mitglied')
        expect(wrapper.find('[data-testid="placeholder-category-APPOINTMENT"]').exists()).toBe(false)
        expect(wrapper.find('[data-testid="placeholder-category-SIGNATURE"]').exists()).toBe(false)
        expect(wrapper.find('[data-testid="placeholder-picker-level"]').exists()).toBe(false)
    })

    it('walks down a path and back along its steps', async () => {
        const wrapper = picker()
        await wrapper.find('[data-testid="placeholder-category-MEMBER"]').trigger('click')
        expect(entries(wrapper)).toEqual(['Stammdaten', 'Profil'])

        await choose(wrapper, 'Profil')
        await choose(wrapper, 'Medizinisches')
        expect(steps(wrapper)).toEqual(['Mitglied', 'Profil', 'Medizinisches'])
        expect(entries(wrapper)).toEqual(['Allergien'])

        await wrapper.find('[data-testid="placeholder-picker-path"] button').trigger('click')
        expect(steps(wrapper)).toEqual(['Mitglied'])
        expect(entries(wrapper)).toEqual(['Stammdaten', 'Profil'])
    })

    it('hands the placeholder on and starts over at the top', async () => {
        const wrapper = picker()
        await wrapper.find('[data-testid="placeholder-category-MEMBER"]').trigger('click')
        await choose(wrapper, 'Stammdaten')
        await wrapper.find('[data-testid="placeholder-member.firstName"]').trigger('click')

        expect(wrapper.emitted('pick')?.[0]).toEqual([CATALOGUE[0]])
        expect(wrapper.find('[data-testid="placeholder-picker-level"]').exists()).toBe(false)
    })

    it('leaves the called name out of a legal template', async () => {
        const wrapper = picker({legal: true})
        await wrapper.find('[data-testid="placeholder-category-MEMBER"]').trigger('click')
        await choose(wrapper, 'Stammdaten')

        expect(entries(wrapper)).toEqual(['Vorname'])
    })

    it('offers a signature field where one can stand', () => {
        expect(picker({signatures: true}).find('[data-testid="placeholder-category-SIGNATURE"]').exists()).toBe(true)
    })

    it('searches every step and names the path of each match', async () => {
        const wrapper = picker()
        await wrapper.find('[data-testid="placeholder-picker-search"] input').setValue('allergien')

        const matches = wrapper.findAll('[data-testid="placeholder-picker-matches"] li').map(match => match.text())
        expect(matches).toEqual([
            'AllergienMitglied › Profil › Medizinisches',
            'AllergienErziehungsberechtigte 1 › Profil › Medizinisches',
        ])

        await wrapper.find('[data-testid="placeholder-guardian1.profile.2"]').trigger('click')
        expect(wrapper.emitted('pick')?.[0]).toEqual([CATALOGUE[6]])
        expect((wrapper.find('[data-testid="placeholder-picker-search"] input').element as HTMLInputElement).value).toBe('')
    })

    it('says when a search finds nothing', async () => {
        const wrapper = picker()
        await wrapper.find('[data-testid="placeholder-picker-search"] input').setValue('Feuerwehrauto')

        expect(wrapper.text()).toContain('Kein Platzhalter passt zur Suche.')
    })
})
