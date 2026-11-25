/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, watch} from 'vue'
import {attendance, eventGroups} from '@/api'
import type {
  AttendanceTemplateField,
  EventGroupSettings,
  EventGroupSettingsRequest,
  TemplateDetailResponse,
} from '@/api/generated/schema'
import {useEventFieldDefaults} from '../eventeditview/useEventFieldDefaults'

/**
 * The date settings the event group editor holds: the attendance template with its field pre-fills,
 * the category and the reminders every part without its own follows. They are saved in a request of
 * their own once the event group itself is saved, since a new event group has no id before that.
 *
 * <p>It also holds the fields of the chosen template, which both the pre-fills and the event group's
 * own fields are tied to, and drops the pre-fills of fields the chosen template does not have.
 */
export function useEventGroupSettings() {
  const templateId = ref('')
  const categoryId = ref('')
  const reminders = ref<number[]>([])
  const fieldDefaults = useEventFieldDefaults()
  const templateFields = ref<AttendanceTemplateField[]>([])

  watch(templateId, async id => {
    const fields = id ? await attendance.listTemplateFields(Number(id)).catch(() => []) : []
    if (id !== templateId.value) return
    templateFields.value = fields
    fieldDefaults.keepOnly(templateFields.value.map(field => field.id))
  }, {immediate: true})

  function fill(settings: EventGroupSettings) {
    templateId.value = settings.templateId == null ? '' : String(settings.templateId)
    categoryId.value = settings.categoryId == null ? '' : String(settings.categoryId)
    reminders.value = [...settings.reminderDays]
    fieldDefaults.fromEntries(settings.fieldDefaults)
  }

  function request(): EventGroupSettingsRequest {
    return {
      templateId: templateId.value ? Number(templateId.value) : null,
      categoryId: categoryId.value ? Number(categoryId.value) : null,
      reminderDays: reminders.value,
      fieldDefaults: templateId.value ? fieldDefaults.toEntries() : [],
    }
  }

  async function load(eventGroupId: number) {
    fill(await eventGroups.getSettings(eventGroupId))
  }

  async function save(eventGroupId: number) {
    fill(await eventGroups.setSettings(eventGroupId, request()))
  }

  /**
   * Takes the category, attendance template and reminders of an appointment template, keeping what the
   * template leaves open. Its pre-fills are taken separately, through the template's own address.
   */
  function takeFrom(detail: TemplateDetailResponse) {
    const template = detail.template
    if (template.categoryId != null) categoryId.value = String(template.categoryId)
    if (template.attendanceTemplateId != null) templateId.value = String(template.attendanceTemplateId)
    reminders.value = [...new Set([...reminders.value, ...detail.reminderDays])].sort((a, b) => a - b)
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

  return {
    templateId, categoryId, reminders, fieldDefaults, templateFields,
    takeFrom, setTemplate, setCategory, setReminders, request, load, save,
  }
}
