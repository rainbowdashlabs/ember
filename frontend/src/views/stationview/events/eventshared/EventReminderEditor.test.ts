/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import EventReminderEditor from './EventReminderEditor.vue'

const global = {stubs: {'font-awesome-icon': true}}

/**
 * Inside the appointment form the reminders are only the button and what it does: no heading, and no
 * line saying none are set.
 */
describe('EventReminderEditor', () => {
    it('says nothing but the button and its explanation in compact form', () => {
        const wrapper = mount(EventReminderEditor, {props: {modelValue: [], compact: true}, global})

        expect(wrapper.text()).not.toContain('Erinnerungen')
        expect(wrapper.text()).not.toContain('Keine Erinnerungen')
        const text = wrapper.text()
        expect(text.indexOf('Erinnerung hinzufügen')).toBeLessThan(text.indexOf('Tage vor dem Termin'))
    })

    it('keeps heading and empty state elsewhere', () => {
        const wrapper = mount(EventReminderEditor, {props: {modelValue: []}, global})

        expect(wrapper.text()).toContain('Keine Erinnerungen')
    })

    it('adds a reminder a day before', async () => {
        const wrapper = mount(EventReminderEditor, {props: {modelValue: [], compact: true}, global})

        await wrapper.get('button').trigger('click')

        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([[1]])
    })
})
