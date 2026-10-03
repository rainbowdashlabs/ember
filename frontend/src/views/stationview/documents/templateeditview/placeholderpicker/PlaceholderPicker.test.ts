/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {defineComponent, h, ref} from 'vue'
import {mount, type VueWrapper} from '@vue/test-utils'
import {DocumentLanguage, PlaceholderCategory} from '@/api/generated/schema'
import {APPOINTMENT_START, BIRTH_DATE, CATALOGUE, DATE_FORMATS} from './fixtures'
import {providePlaceholderDates} from './placeholderDates'
import PlaceholderPicker from './PlaceholderPicker.vue'
import {placeholdersOfTemplate} from './placeholderTree'

/**
 * The picker walks a placeholder's path from the categories at the top, goes back along the steps it
 * took, searches across every step, and starts over at the top once something was picked.
 */
describe('PlaceholderPicker', () => {
    function picker(props: {legal?: boolean} = {}) {
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
        expect(wrapper.find('[data-testid="placeholder-picker-level"]').exists()).toBe(false)
    })

    it('shows the appointment only where the template is for appointments', () => {
        const appointment = `[data-testid="placeholder-category-${PlaceholderCategory.APPOINTMENT}"]`
        expect(picker().find(appointment).text()).toBe('Termin')
        const forMembers = mount(PlaceholderPicker, {
            props: {placeholders: placeholdersOfTemplate(CATALOGUE, false), legal: false},
        })
        expect(forMembers.find(appointment).exists()).toBe(false)
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

        expect(wrapper.emitted('pick')?.[0]).toEqual([{key: 'member.firstName', label: 'Vorname'}])
        expect(wrapper.find('[data-testid="placeholder-picker-level"]').exists()).toBe(false)
    })

    it('leaves the called name out of a legal template', async () => {
        const wrapper = picker({legal: true})
        await wrapper.find('[data-testid="placeholder-category-MEMBER"]').trigger('click')
        await choose(wrapper, 'Stammdaten')

        expect(entries(wrapper)).toEqual(['Vorname'])
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
        expect(wrapper.emitted('pick')?.[0]).toEqual([{key: 'guardian1.profile.2', label: 'Erziehungsberechtigte 1: Allergien'}])
        expect((wrapper.find('[data-testid="placeholder-picker-search"] input').element as HTMLInputElement).value).toBe('')
    })

    it('says when a search finds nothing', async () => {
        const wrapper = picker()
        await wrapper.find('[data-testid="placeholder-picker-search"] input').setValue('Feuerwehrauto')

        expect(wrapper.text()).toContain('Kein Platzhalter passt zur Suche.')
    })

    it('hands a date on as it is where no editor above offers formats', async () => {
        const wrapper = mount(PlaceholderPicker, {props: {placeholders: [BIRTH_DATE], legal: false}})
        await wrapper.find('[data-testid="placeholder-picker-search"] input').setValue('geburtsdatum')
        await wrapper.find('[data-testid="placeholder-member.birthDate"]').trigger('click')

        expect(wrapper.emitted('pick')?.[0]).toEqual([{key: 'member.birthDate', label: 'Geburtsdatum'}])
    })
})

/**
 * A date takes a last step: the ready-made formats on the example day in the template's language, and an
 * own format, checked and shown while it is typed. The date is handed on with its format in the key and
 * the example in the label.
 */
describe('PlaceholderPicker, the format of a date', () => {
    function datedPicker(language: DocumentLanguage = DocumentLanguage.DE) {
        const host = defineComponent({
            setup() {
                providePlaceholderDates(ref({formats: DATE_FORMATS, language}))
                return () => h(PlaceholderPicker, {placeholders: [BIRTH_DATE, APPOINTMENT_START], legal: false})
            },
        })
        return mount(host)
    }

    async function chooseDate(wrapper: VueWrapper, words: string, key: string) {
        await wrapper.find('[data-testid="placeholder-picker-search"] input').setValue(words)
        await wrapper.find(`[data-testid="placeholder-${key}"]`).trigger('click')
    }

    function examples(wrapper: VueWrapper) {
        return wrapper.findAll('[data-testid="placeholder-date-format"] li .flex-1').map(example => example.text())
    }

    function picked(wrapper: VueWrapper) {
        return wrapper.findComponent(PlaceholderPicker).emitted('pick')?.[0]
    }

    it('offers a day every format without a time of day, on the example day', async () => {
        const wrapper = datedPicker()
        await chooseDate(wrapper, 'geburtsdatum', 'member.birthDate')

        expect(examples(wrapper)).toEqual([
            '03.10.2026', '3. Oktober 2026', '3. Okt. 2026', 'Oktober 2026', '2026', 'Samstag, 3. Oktober 2026',
        ])
        await wrapper.find('[data-testid="placeholder-date-long"]').trigger('click')
        expect(picked(wrapper)).toEqual([{key: 'member.birthDate|long', label: 'Geburtsdatum (3. Oktober 2026)'}])
    })

    it('writes the examples in the language of the template', async () => {
        const wrapper = datedPicker(DocumentLanguage.EN)
        await chooseDate(wrapper, 'geburtsdatum', 'member.birthDate')

        expect(examples(wrapper)).toEqual([
            '03.10.2026', 'October 3, 2026', 'Oct 3, 2026', 'October 2026', '2026', 'Saturday, October 3, 2026',
        ])
    })

    it('offers the start of an appointment the formats with a time of day too', async () => {
        const wrapper = datedPicker()
        await chooseDate(wrapper, 'beginn', 'event.start')

        expect(examples(wrapper).slice(-2)).toEqual(['03.10.2026 18:30', '18:30'])
        await wrapper.find('[data-testid="placeholder-date-time"]').trigger('click')
        expect(picked(wrapper)).toEqual([{key: 'event.start|time', label: 'Beginn des Termins (18:30)'}])
    })

    it('shows an own format while it is typed and inserts it once it can be printed', async () => {
        const wrapper = datedPicker()
        await chooseDate(wrapper, 'geburtsdatum', 'member.birthDate')
        const pattern = wrapper.find('[data-testid="placeholder-own-date-format"] input')
        const insert = wrapper.find('[data-testid="placeholder-own-date-insert"]')

        await pattern.setValue('TT.QQ.JJJJ')
        expect(wrapper.text()).toContain('„QQ" kennt das Format nicht.')
        expect(insert.attributes('disabled')).toBeDefined()

        await pattern.setValue('hh:mm')
        expect(wrapper.text()).toContain('Dieses Datum hat keine Uhrzeit.')

        await pattern.setValue(' TTTT, T.M. ')
        expect(wrapper.find('[data-testid="placeholder-own-date-preview"]').text()).toBe('So sieht es aus: Samstag, 3.10.')
        await insert.trigger('click')
        expect(picked(wrapper)).toEqual([{key: 'member.birthDate|TTTT, T.M.', label: 'Geburtsdatum (Samstag, 3.10.)'}])
    })

    it('leads back to where the date was picked', async () => {
        const wrapper = datedPicker()
        await chooseDate(wrapper, 'geburtsdatum', 'member.birthDate')
        await wrapper.find('[data-testid="placeholder-date-back"]').trigger('click')

        expect(wrapper.find('[data-testid="placeholder-date-format"]').exists()).toBe(false)
        expect(wrapper.find('[data-testid="placeholder-member.birthDate"]').exists()).toBe(true)
    })
})
