/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {eventGroups} from '@/api'
import type {EventGroupSettings, EventGroupSettingsRequest} from '@/api/generated/schema'
import {useEventFieldDefaults} from '../eventeditview/useEventFieldDefaults'

/**
 * The date settings the event group editor holds: the attendance template with its field pre-fills,
 * the category and the reminders every part without its own follows. They are saved in a request of
 * their own once the event group itself is saved, since a new event group has no id before that.
 */
export function useEventGroupSettings() {
  const templateId = ref('')
  const categoryId = ref('')
  const reminders = ref<number[]>([])
  const fieldDefaults = useEventFieldDefaults()

  function fill(settings: EventGroupSettings) {
    templateId.value = settings.templateId == null ? '' : String(settings.templateId)
    categoryId.value = settings.categoryId == null ? '' : String(settings.categoryId)
    reminders.value = [...settings.reminderDays]
    fieldDefaults.defaults.value = new Map(settings.fieldDefaults.map(entry =>
        [entry.fieldId, {source: entry.source, value: entry.value ?? ''}]))
  }

  function request(): EventGroupSettingsRequest {
    return {
      templateId: templateId.value ? Number(templateId.value) : null,
      categoryId: categoryId.value ? Number(categoryId.value) : null,
      reminderDays: reminders.value,
      fieldDefaults: templateId.value
          ? [...fieldDefaults.defaults.value.entries()]
              .filter(([, entry]) => entry.source)
              .map(([fieldId, entry]) => ({fieldId, source: entry.source, value: entry.value || null}))
          : [],
    }
  }

  async function load(eventGroupId: number) {
    fill(await eventGroups.getSettings(eventGroupId))
  }

  async function save(eventGroupId: number) {
    fill(await eventGroups.setSettings(eventGroupId, request()))
  }

  function setTemplate(id: string) {
    templateId.value = id
  }

  function setCategory(id: string) {
    categoryId.value = id
  }

  function setReminders(days: number[]) {
    reminders.value = days
  }

  return {templateId, categoryId, reminders, fieldDefaults, setTemplate, setCategory, setReminders, request, load, save}
}
