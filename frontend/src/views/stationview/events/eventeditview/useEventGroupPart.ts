/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {eventGroups} from '@/api'
import type {AppointmentField, EventGroupPartSettings, EventGroupPartSummary} from '@/api/generated/schema'

/** The part of the editor's form the event group's date settings decide. */
interface DateSettingsForm {
  templateId: string
  categoryId: string
}

/**
 * The event group an appointment in the editor is a part of, with what the group hands down: its
 * fields as the group stores them, and its date settings beside the part's own ones.
 */
export function useEventGroupPart(eventId: () => number | null, form: DateSettingsForm) {
  const part = ref<EventGroupPartSummary | null>(null)
  const inheritedFields = ref<AppointmentField[]>([])
  const settings = ref<EventGroupPartSettings | null>(null)

  /**
   * Notes which event group the appointment belongs to and reads the fields that group hands down, as
   * the group stores them: an own field of the same name replaces one on the appointment's page, but
   * the editor still shows it, struck through, so removing the own field visibly brings it back.
   */
  async function load(found: EventGroupPartSummary | null) {
    part.value = found
    if (!found) return
    const [group, partSettings] = await Promise.all([
      eventGroups.getEventGroup(found.eventGroupId),
      eventGroups.partSettings(eventId()!),
    ])
    inheritedFields.value = group.fields
    settings.value = partSettings
  }

  /**
   * Puts the part's own attendance template and category into the form. The appointment is read with
   * the settings as they take effect, its event group's where it has none of its own, and the form
   * writes back what it holds: without this, saving would turn the event group's settings into the
   * part's own and stop it following the event group.
   */
  function keepOwnDateSettings() {
    const own = settings.value
    if (!own) return
    form.templateId = own.ownTemplateId == null ? '' : String(own.ownTemplateId)
    form.categoryId = own.ownCategoryId == null ? '' : String(own.ownCategoryId)
  }

  /** Reads the group again after the appointment joined one, which locks the settings the group decides. */
  async function reload() {
    await load(await eventGroups.eventGroupOfEvent(eventId()!))
    keepOwnDateSettings()
  }

  return {part, inheritedFields, settings, load, keepOwnDateSettings, reload}
}
