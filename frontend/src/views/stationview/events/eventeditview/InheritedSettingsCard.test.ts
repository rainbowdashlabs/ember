/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import InheritedSettingsCard from './InheritedSettingsCard.vue'
import type {AttendanceTemplate, EventCategory, EventGroupSettings} from '@/api/generated/schema'

const i18n = createI18n({
  legacy: false, locale: 'de-DE', missingWarn: false, fallbackWarn: false,
  messages: {'de-DE': {eventGroups: {templateWithDefaults: '{name} ({count})'}}, en: {}},
})

const settings: EventGroupSettings = {
  templateId: 4,
  categoryId: 2,
  reminderDays: [3, 1],
  fieldDefaults: [{eventId: 9, fieldId: 11, source: 'VALUE', value: 'Herr Krause'}],
}
const categories = [{id: 2, name: 'Lehrgang'}] as EventCategory[]
const templates = [{id: 4, name: 'Übungsbogen'}] as AttendanceTemplate[]

function mountCard(own: {category?: string; template?: string; reminders?: number[]}, handed = settings) {
  return mount(InheritedSettingsCard, {
    props: {
      settings: handed,
      categories,
      templates,
      ownCategoryId: own.category ?? '',
      ownTemplateId: own.template ?? '',
      ownReminders: own.reminders ?? [],
    },
    global: {plugins: [i18n]},
  })
}

describe('InheritedSettingsCard', () => {
  it('lists what the group hands down and offers to override what the part follows', async () => {
    const wrapper = mountCard({category: '5'})

    const rows = wrapper.findAll('[data-testid="inherited-setting"]')
    expect(rows).toHaveLength(3)
    expect(rows[0]!.text()).toContain('Lehrgang')
    expect(rows[0]!.find('[data-testid="override-setting"]').exists()).toBe(false)
    expect(rows[1]!.text()).toContain('Übungsbogen')

    await rows[1]!.find('[data-testid="override-setting"]').trigger('click')
    await rows[2]!.find('[data-testid="override-setting"]').trigger('click')

    expect(wrapper.emitted('override')).toEqual([['template'], ['reminders']])
  })

  it('marks the template and reminders the part already sets itself', () => {
    const wrapper = mountCard({template: '8', reminders: [2]})

    expect(wrapper.findAll('[data-testid="override-setting"]')).toHaveLength(1)
  })

  it('stays away while the group hands down nothing', () => {
    const wrapper = mountCard({}, {templateId: null, categoryId: null, reminderDays: [], fieldDefaults: []})

    expect(wrapper.find('[data-testid="inherited-settings"]').exists()).toBe(false)
  })
})
