/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {eventGroups} from '@/api'
import type {AppointmentField, EventGroupPartSettings, EventGroupPartSummary} from '@/api/generated/schema'

/** The part of the editor's form the event group's date settings and places decide. */
interface DateSettingsForm {
  templateId: string
  categoryId: string
  registrationLimit: number | undefined
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
   * Puts the part's own attendance template, category and places into the form. The appointment is
   * read with the settings as they take effect, its event group's where it has none of its own, and
   * the form writes back what it holds: without this, saving would turn the event group's settings
   * into the part's own and stop it following the event group.
   */
  function keepOwnDateSettings() {
    const own = settings.value
    if (!own) return
    form.templateId = own.ownTemplateId == null ? '' : String(own.ownTemplateId)
    form.categoryId = own.ownCategoryId == null ? '' : String(own.ownCategoryId)
    form.registrationLimit = own.ownRegistrationLimit ?? undefined
  }

  /**
   * Saves the places the part sets itself, where they changed: a number of its own, or none to take
   * its event group's again. The appointment's own update leaves them alone.
   *
   * @param savedEventId the part as saved
   * @param limit        the places entered, null for the event group's
   */
  async function saveOwnLimit(savedEventId: number, limit: number | null) {
    const own = settings.value
    if (!own || (own.ownRegistrationLimit ?? null) === limit) return
    settings.value = await eventGroups.setPartLimit(savedEventId, limit)
  }

  /** Reads the group again after the appointment joined one, which locks the settings the group decides. */
  async function reload() {
    await load(await eventGroups.eventGroupOfEvent(eventId()!))
    keepOwnDateSettings()
  }

  return {part, inheritedFields, settings, load, keepOwnDateSettings, reload, saveOwnLimit}
}
