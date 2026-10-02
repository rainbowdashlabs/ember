/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, reactive, ref, watch} from 'vue'
import {events} from '@/api'
import {EventTypes, needsDayOfWeek} from '@/api/events'
import type {
    AppointmentField,
    EventFieldEntry,
    EventRequest,
    StationEvent,
    TemplateDetailResponse,
} from '@/api/generated/schema'
import {toRestriction} from '@/components/input/restriction'
import {createEventFormState, eventTypeNamed} from './eventFormState'
import {modelBindings} from './modelBindings'
import {instantToLocalInput} from '@/util/format'
import {asSaved} from '../eventshared/eventQuestions'

/**
 * Owns the event editor form: its state, the mapping onto the editor body, and
 * the translation between the form and the event endpoints.
 */
export function useEventForm() {
  const state = reactive(createEventFormState())

  const {props, handlers} = modelBindings(state)

  /**
   * The appointment template last applied, which the server copies the registration questions from
   * when the appointment is created. Kept apart from the form state, whose keys are the editor's
   * props, and apart from `templateId`, which names the attendance sheet.
   */
  const appliedTemplateId = ref<number | null>(null)

  watch(() => state.startTime, (val) => {
    if (val && !state.endTime) {
      state.endTime = val
    }
  })

  /**
   * Whether the appointment finishes before it begins.
   *
   * <p>An appointment may run past midnight and over several days, so only the one order that
   * cannot happen is refused. It reaches further than the appointment itself: an attendance sheet
   * takes its times from here, and a sheet running backwards counts everybody on it for nothing.
   */
  const endsBeforeItStarts = computed(() =>
      !!state.startTime && !!state.endTime && new Date(state.endTime) < new Date(state.startTime))

  /**
   * Fills the form from an event template.
   *
   * <p>The attendance template comes with it. A template that says which sheet the attendance is
   * taken on and then leaves the appointment without one makes whoever applied it set the same thing
   * again by hand, which is the one thing a template is for.
   */
  function applyTemplate(detail: TemplateDetailResponse) {
    const tpl = detail.template
    appliedTemplateId.value = tpl.id
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
        fieldType: f.fieldType,
        config: f.config,
        value: f.defaultValue ?? '',
        overview: f.overview,
        attendanceFieldId: f.attendanceFieldId,
        isPublic: f.isPublic,
      }))
      state.fields = [...state.fields, ...newFields]
    }
    if (detail.reminderDays?.length) {
      state.reminders = [...new Set([...state.reminders, ...detail.reminderDays])]
    }
  }

  function applyEventFields(fields: AppointmentField[]) {
    state.fields = fields.map(f => ({
      id: f.id,
      name: f.name,
      fieldType: f.fieldType,
      config: f.config,
      value: f.value,
      overview: f.overview,
      attendanceFieldId: f.attendanceFieldId,
      isPublic: f.isPublic,
    }))
  }

  function applyEvent(ev: StationEvent) {
    state.name = ev.name ?? ''
    state.description = ev.description ?? ''
    state.eventType = ev.eventType ?? EventTypes.RECURRING
    state.dayOfWeek = ev.dayOfWeek != null ? String(ev.dayOfWeek) : '1'
    state.startTime = instantToLocalInput(ev.startTime)
    state.endTime = instantToLocalInput(ev.endTime)
    state.templateId = ev.templateId != null ? String(ev.templateId) : ''
    state.categoryId = ev.categoryId != null ? String(ev.categoryId) : ''
    state.requiresRegistration = ev.requiresRegistration ?? false
    state.hasDeadline = !!ev.registrationDeadline
    state.registrationDeadline = instantToLocalInput(ev.registrationDeadline)
    state.requiresConfirmation = ev.requiresConfirmation ?? false
    state.registrationLimit = ev.registrationLimit ?? undefined
    state.minRegistrations = ev.minRegistrations ?? undefined
    state.thresholdDays = ev.thresholdDays ?? undefined
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

  function buildPayload(): EventRequest {
    return {
      name: state.name,
      description: state.description || null,
      eventType: eventTypeNamed(state.eventType) ?? EventTypes.ONE_TIME,
      dayOfWeek: needsDayOfWeek(state.eventType) ? Number(state.dayOfWeek) : null,
      startTime: state.startTime ? new Date(state.startTime).toISOString() : undefined,
      endTime: state.endTime ? new Date(state.endTime).toISOString() : undefined,
      templateId: state.templateId ? Number(state.templateId) : null,
      eventTemplateId: appliedTemplateId.value,
      categoryId: state.categoryId ? Number(state.categoryId) : null,
      requiresRegistration: state.requiresRegistration,
      registrationDeadline: state.hasDeadline && state.registrationDeadline
          ? new Date(state.registrationDeadline).toISOString() : null,
      requiresConfirmation: state.requiresConfirmation,
      registrationLimit: state.registrationLimit ?? null,
      minRegistrations: state.minRegistrations ?? null,
      thresholdDays: state.minRegistrations ? state.thresholdDays ?? null : null,
      restriction: state.restriction,
      viewRestriction: state.viewRestriction,
      registrationCloseDays: state.registrationCloseDays ?? null,
      repeatUntil: repeats() && state.repeatUntil ? state.repeatUntil : null,
      repeatCount: repeats() && !state.repeatUntil ? state.repeatCount ?? null : null,
    }
  }

  /** Only a repeating appointment has an end to its repetition, so a one-off never sends one. */
  function repeats(): boolean {
    return state.eventType !== EventTypes.ONE_TIME
  }

  function namedFields(): EventFieldEntry[] {
    return state.fields.filter(f => f.name?.trim()).map(asSaved)
  }

  return {state, props, handlers, applyTemplate, loadEvent, buildPayload, namedFields, endsBeforeItStarts}
}
