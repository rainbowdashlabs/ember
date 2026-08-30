/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {reactive, watch} from 'vue'
import {events} from '@/api'
import {EventTypes, needsDayOfWeek, type EventField, type EventFieldEntry, type EventTemplateDetail, type StationEvent} from '@/api/events'
import {toRestriction} from '@/components/input/restriction'
import {createEventFormState} from './eventFormState'
import {modelBindings} from './modelBindings'

function toLocalDateTime(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/**
 * Owns the event editor form: its state, the mapping onto the editor body, and
 * the translation between the form and the event endpoints.
 */
export function useEventForm() {
  const state = reactive(createEventFormState())

  const {props, handlers} = modelBindings(state)

  watch(() => state.startTime, (val) => {
    if (val && !state.endTime) {
      state.endTime = val
    }
  })

  /**
   * Fills the form from an event template.
   *
   * <p>The attendance template comes with it. A template that says which sheet the attendance is
   * taken on and then leaves the appointment without one makes whoever applied it set the same thing
   * again by hand, which is the one thing a template is for.
   */
  function applyTemplate(detail: EventTemplateDetail) {
    const tpl = detail.template
    if (tpl.title) state.name = tpl.title
    if (tpl.description) state.description = tpl.description
    if (tpl.categoryId) state.categoryId = String(tpl.categoryId)
    if (tpl.attendanceTemplateId) state.templateId = String(tpl.attendanceTemplateId)
    if (tpl.eventType) state.eventType = tpl.eventType
    if (detail.restriction) {
      state.restriction = toRestriction(detail.restriction.register)
      state.viewRestriction = toRestriction(detail.restriction.view)
    }
    if (tpl.requiresRegistration != null) state.requiresRegistration = tpl.requiresRegistration
    if (tpl.requiresConfirmation != null) state.requiresConfirmation = tpl.requiresConfirmation
    if (detail.fields.length > 0) {
      const newFields: EventFieldEntry[] = detail.fields.map(f => ({
        name: f.name,
        fieldType: f.fieldType ?? 'STRING',
        config: typeof f.config === 'string' ? (f.config ? JSON.parse(f.config) : {}) : (f.config ?? {}),
        value: f.defaultValue ?? '',
        overview: f.overview ?? false,
        attendanceFieldId: f.attendanceFieldId ?? null,
        isPublic: f.isPublic ?? false,
      }))
      state.fields = [...state.fields, ...newFields]
    }
    if (detail.reminderDays?.length) {
      state.reminders = [...new Set([...state.reminders, ...detail.reminderDays])]
    }
  }

  function applyEventFields(fields: EventField[]) {
    state.fields = fields.map(f => ({
      name: f.name ?? '',
      fieldType: f.fieldType ?? 'STRING',
      config: f.config ?? {},
      value: f.value ?? '',
      overview: f.overview ?? false,
      attendanceFieldId: f.attendanceFieldId ?? null,
    }))
  }

  function applyEvent(ev: StationEvent) {
    state.name = ev.name ?? ''
    state.description = ev.description ?? ''
    state.eventType = ev.eventType ?? EventTypes.RECURRING
    state.dayOfWeek = ev.dayOfWeek != null ? String(ev.dayOfWeek) : '1'
    state.startTime = ev.startTime ? toLocalDateTime(ev.startTime) : ''
    state.endTime = ev.endTime ? toLocalDateTime(ev.endTime) : ''
    state.templateId = ev.templateId != null ? String(ev.templateId) : ''
    state.categoryId = ev.categoryId != null ? String(ev.categoryId) : ''
    state.requiresRegistration = ev.requiresRegistration ?? false
    state.hasDeadline = !!ev.registrationDeadline
    state.registrationDeadline = ev.registrationDeadline ? toLocalDateTime(ev.registrationDeadline) : ''
    state.requiresConfirmation = ev.requiresConfirmation ?? false
    state.registrationLimit = ev.registrationLimit ?? undefined
    state.minRegistrations = ev.minRegistrations ?? undefined
    state.hasThreshold = !!ev.thresholdDate
    state.thresholdDate = ev.thresholdDate ? toLocalDateTime(ev.thresholdDate) : ''
    state.registrationCloseDays = ev.registrationCloseDays ?? undefined
    state.repeatUntil = ev.repeatUntil ?? ''
    state.repeatCount = ev.repeatCount ?? undefined
  }

  async function loadEvent(id: number) {
    const [ev, restrictions, fields] = await Promise.all([
      events.getEvent(id),
      events.getRestrictions(id),
      events.getEventFields(id),
    ])

    applyEventFields(fields)
    applyEvent(ev)

    state.restriction = toRestriction(restrictions.register)
    state.viewRestriction = toRestriction(restrictions.view)

    try {
      state.reminders = await events.getEventReminders(id)
    } catch { state.reminders = [] }
  }

  function buildPayload() {
    return {
      name: state.name,
      description: state.description || undefined,
      eventType: state.eventType,
      dayOfWeek: needsDayOfWeek(state.eventType) ? Number(state.dayOfWeek) : null,
      startTime: state.startTime ? new Date(state.startTime).toISOString() : undefined,
      endTime: state.endTime ? new Date(state.endTime).toISOString() : undefined,
      templateId: state.templateId ? Number(state.templateId) : undefined,
      categoryId: state.categoryId ? Number(state.categoryId) : undefined,
      requiresRegistration: state.requiresRegistration,
      registrationDeadline: state.hasDeadline && state.registrationDeadline
          ? new Date(state.registrationDeadline).toISOString() : undefined,
      requiresConfirmation: state.requiresConfirmation,
      registrationLimit: state.registrationLimit ?? undefined,
      minRegistrations: state.minRegistrations ?? undefined,
      thresholdDate: state.hasThreshold && state.thresholdDate
          ? new Date(state.thresholdDate).toISOString() : undefined,
      restriction: state.restriction,
      viewRestriction: state.viewRestriction,
      registrationCloseDays: state.registrationCloseDays ?? undefined,
      repeatUntil: repeats() && state.repeatUntil ? state.repeatUntil : null,
      repeatCount: repeats() && !state.repeatUntil ? state.repeatCount ?? null : null,
    }
  }

  /** Only a repeating appointment has an end to its repetition, so a one-off never sends one. */
  function repeats(): boolean {
    return state.eventType !== EventTypes.ONE_TIME
  }

  function namedFields(): EventFieldEntry[] {
    return state.fields.filter(f => f.name.trim())
  }

  return {state, props, handlers, applyTemplate, loadEvent, buildPayload, namedFields}
}
