/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import EventGroupDateSettingsCard from './EventGroupDateSettingsCard.vue'
import type {AttendanceTemplate, AttendanceTemplateField, EventCategory} from '@/api/generated/schema'

const i18n = createI18n({legacy: false, locale: 'de-DE', missingWarn: false, fallbackWarn: false, messages: {'de-DE': {}, en: {}}})

const field = {id: 11, templateId: 4, name: 'Ausbilder', fieldType: 'TEXT', config: {}, position: 0} as unknown as AttendanceTemplateField

function mountCard(templateId: string) {
  return mount(EventGroupDateSettingsCard, {
    props: {
      categories: [{id: 2, name: 'Lehrgang'}] as EventCategory[],
      templates: [{id: 4, name: 'Übungsbogen'}] as AttendanceTemplate[],
      templateFields: [field],
      fieldDefaults: new Map(),
      categoryId: '',
      templateId,
      reminders: [],
    },
    global: {plugins: [i18n]},
  })
}

describe('EventGroupDateSettingsCard', () => {
  it('offers the pre-fills only once a template is chosen', () => {
    expect(mountCard('').find('[data-testid="event-group-field-defaults"]').exists()).toBe(false)
    expect(mountCard('4').find('[data-testid="event-group-field-defaults"]').text()).toContain('Ausbilder')
  })

  it('hands the chosen category and template back to the editor', async () => {
    const wrapper = mountCard('')

    await wrapper.find('[data-testid="event-group-category"]').setValue('2')
    await wrapper.find('[data-testid="event-group-attendance-template"]').setValue('4')

    expect(wrapper.emitted('update:categoryId')?.[0]).toEqual(['2'])
    expect(wrapper.emitted('update:templateId')?.[0]).toEqual(['4'])
  })
})

/**
 * Inside the event group's form the card shows its row, then what the form puts after it, then the
 * template's pre-fills, and keeps its explanation behind an icon by the category.
 */
describe('EventGroupDateSettingsCard compact', () => {
  it('puts the form\'s fields before the pre-fills and the hint behind an icon', () => {
    const wrapper = mount(EventGroupDateSettingsCard, {
      props: {
        categories: [{id: 2, name: 'Lehrgang'}] as EventCategory[],
        templates: [{id: 4, name: 'Übungsbogen'}] as AttendanceTemplate[],
        templateFields: [field],
        fieldDefaults: new Map(),
        categoryId: '',
        templateId: '4',
        reminders: [],
        compact: true,
      },
      slots: {'after-row': '<div data-testid="form-fields"></div>'},
      global: {plugins: [i18n], stubs: {'font-awesome-icon': true}},
    })
    const html = wrapper.html()

    expect(html.indexOf('form-fields')).toBeLessThan(html.indexOf('event-group-field-defaults'))
    expect(wrapper.get('[data-testid="hint-icon"]').attributes('aria-label')).toContain('dateSettingsHint')
    expect(wrapper.find('[data-testid="event-reminders"]').exists()).toBe(false)
  })
})
