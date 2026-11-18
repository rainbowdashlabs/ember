/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, reactive, ref, toRef, watch} from 'vue'
import {events} from '@/api'
import {needsDayOfWeek} from '@/api/events'
import {namesMembers} from '@/api/fieldTypes'
import {
    EventType,
    QueueOrder,
    type AppointmentField,
    type EventFieldEntry,
    type EventRequest,
    type EventRestrictions,
    type StationEvent,
    type TemplateDetailResponse,
} from '@/api/generated/schema'
import {toRestriction} from '@/components/input/restriction'
import {createEventFormState, eventTypeNamed} from './eventFormState'
import {modelBindings} from './modelBindings'
import {instantToLocalInput} from '@/util/format'
import {useEndFollowsStart} from '@/composables/useEndFollowsStart'
import {entryOf, namedEntries} from '../eventshared/eventQuestions'
import {offsetParts} from '../templateeditview/signUpRules'
import {copyStartDay, deadlineHoursBefore, lengthOf, placedOn} from './copyDates'

/**
 * What the editor reads of a saved appointment to fill its form: the appointment, both audiences, its
 * own fields with their values and its reminders.
 */
export interface EventEditDetail {
  event: StationEvent
  restrictions: EventRestrictions
  fields: AppointmentField[]
  reminders: number[]
}

/** Reads what the editor fills its form from. */
export async function fetchEditDetail(id: number): Promise<EventEditDetail> {
  const [event, restrictions, fields, reminders] = await Promise.all([
    events.getEvent(id),
    events.getRestrictions(id),
    events.getEventFields(id, null, true),
    events.getEventReminders(id),
  ])
  return {event, restrictions, fields, reminders}
}

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

  /**
   * The appointment this one is a copy of, which the server hands its files, documents to bring and
   * equipment from when the copy is created.
   */
  const copiedFromId = ref<number | null>(null)

  /**
   * How long the copied appointment ran, for a copy that starts without a date: the end follows the
   * start entered first by that much rather than landing on the start itself.
   */
  const copiedLength = ref<number | null>(null)

  /**
   * The waiting list order the appointment had when the editor opened, null for a new one. Kept apart
   * from the form state because the order is saved on its own after the appointment.
   */
  const savedQueueOrder = ref<QueueOrder | null>(null)

  watch(() => state.startTime, (val) => {
    if (val && !state.endTime) {
      state.endTime = endAfter(val)
    }
  })
  useEndFollowsStart(toRef(state, 'startTime'), toRef(state, 'endTime'))

  /** The end a start entered into an empty end gives: the copied length after it, or the start itself. */
  function endAfter(start: string): string {
    if (copiedLength.value === null) return start
    const begins = new Date(start).getTime()
    if (Number.isNaN(begins)) return start
    return instantToLocalInput(new Date(begins + copiedLength.value).toISOString())
  }

  /**
   * Whether the weekday of a series still follows its start. It does until a weekday is picked by
   * hand, which is any change to the weekday this form did not make itself.
   */
  let weekdayFollowsStart = true
  let followingStart = false

  /** Sets the weekday without counting it as picked by hand. */
  function setWeekdayFromStart(day: string) {
    followingStart = true
    state.dayOfWeek = day
    followingStart = false
  }

  watch(() => state.startTime, start => {
    const day = isoWeekday(start)
    if (weekdayFollowsStart && day !== null) setWeekdayFromStart(String(day))
  }, {flush: 'sync'})

  watch(() => state.dayOfWeek, () => {
    if (!followingStart) weekdayFollowsStart = false
  }, {flush: 'sync'})

  /**
   * Takes the weekday a saved appointment comes with. A series keeps following its start only when
   * it runs on the start's weekday; one without a weekday, a single appointment, takes its start's.
   */
  function applyWeekday(saved: number | null | undefined) {
    const startDay = isoWeekday(state.startTime)
    if (saved != null) {
      setWeekdayFromStart(String(saved))
      weekdayFollowsStart = saved === startDay
      return
    }
    setWeekdayFromStart(startDay !== null ? String(startDay) : '1')
    weekdayFollowsStart = true
  }

  /**
   * How many hours before the start the applied template closes registration, for as long as the
   * deadline still follows the start. Null once nothing follows: no template offset was applied, or
   * the deadline was changed by hand.
   */
  const deadlineOffsetHours = ref<number | null>(null)

  /** The deadline last worked out from the start, which tells a hand edit apart from one of ours. */
  let derivedDeadline = ''

  /**
   * Sets the deadline the template's offset gives for the start now entered. Without a start there
   * is nothing to count back from, so the deadline waits for one.
   *
   * <p>Whole days are counted back on the calendar and only the hours left over by the clock, so
   * "three days before 18:00" stays at 18:00 across a change to or from summer time.
   */
  function followStart() {
    if (deadlineOffsetHours.value === null || !state.startTime) return
    const deadline = new Date(state.startTime)
    if (Number.isNaN(deadline.getTime())) return
    const {days, hours} = offsetParts(deadlineOffsetHours.value)
    deadline.setDate(deadline.getDate() - days)
    deadline.setTime(deadline.getTime() - hours * 3_600_000)
    derivedDeadline = instantToLocalInput(deadline.toISOString())
    state.hasDeadline = true
    state.registrationDeadline = derivedDeadline
  }

  watch(() => state.startTime, followStart, {flush: 'sync'})

  watch(() => [state.registrationDeadline, state.hasDeadline] as const, ([deadline, hasDeadline]) => {
    if (deadlineOffsetHours.value === null) return
    if (!hasDeadline || deadline !== derivedDeadline) deadlineOffsetHours.value = null
  }, {flush: 'sync'})

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
   *
   * <p>A template knows no date, so its deadline is a number of hours before the start. The deadline
   * is switched on and counted back from the start once one is entered, and it keeps following the
   * start until it is changed by hand.
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
    if (tpl.registrationLimit != null) state.registrationLimit = tpl.registrationLimit
    if (tpl.queueOrder != null) state.queueOrder = tpl.queueOrder
    if (tpl.minRegistrations != null) state.minRegistrations = tpl.minRegistrations
    if (tpl.thresholdDays != null) state.thresholdDays = tpl.thresholdDays
    if (tpl.registrationCloseDays != null) state.registrationCloseDays = tpl.registrationCloseDays
    if (tpl.registrationDeadlineOffsetHours != null) {
      derivedDeadline = state.registrationDeadline
      deadlineOffsetHours.value = tpl.registrationDeadlineOffsetHours
      state.hasDeadline = true
      followStart()
    }
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
    state.fields = fields.map(f => ({id: f.id, ...entryOf(f)}))
  }

  /**
   * Fills the form from an appointment it is a copy of.
   *
   * <p>Everything the editor shows comes over, so what is saved is what was seen. Fields keep their
   * values except the ones naming people: a copy signs nobody up and puts nobody in charge.
   *
   * <p>The dates do not come over as they were. A one-time copy starts without a date, so it cannot
   * land on the original's day by accident, and the end follows the start entered by the original's
   * length. A series keeps its rule, its times and its end and starts on the original's next date,
   * or on the next day from today its pattern falls on where the original has ended. A last day that
   * then lies before the new start is dropped, since the copy would have no date at all; a number of
   * times is kept and counts from the new start. A deadline is kept as the hours it lay before the
   * start and follows the new start.
   *
   * @param detail   what the editor reads of the original
   * @param today    today, as `yyyy-MM-dd`
   * @param nextDate the date the server names for the original, null where it named none
   */
  function applyCopy(detail: EventEditDetail, today: string, nextDate: string | null) {
    applyEvent(detail.event)
    state.fields = detail.fields.map(field => {
      const entry = entryOf(field)
      return namesMembers(entry.fieldType) ? {...entry, value: ''} : entry
    })
    state.restriction = toRestriction(detail.restrictions.register)
    state.viewRestriction = toRestriction(detail.restrictions.view)
    state.reminders = [...detail.reminders]
    copiedFromId.value = detail.event.id
    placeCopy(detail.event, today, nextDate)
  }

  /** Moves the copy off the original's dates, as {@link applyCopy} describes. */
  function placeCopy(ev: StationEvent, today: string, nextDate: string | null) {
    const offset = deadlineHoursBefore(ev.startTime, ev.registrationDeadline)
    state.hasDeadline = false
    state.registrationDeadline = ''
    if (eventTypeNamed(state.eventType) === EventType.ONE_TIME) {
      copiedLength.value = lengthOf(state.startTime, state.endTime)
      state.startTime = ''
      state.endTime = ''
    } else {
      const dayOfWeek = needsDayOfWeek(state.eventType) ? Number(state.dayOfWeek) : null
      const day = copyStartDay(state.eventType, dayOfWeek, state.startTime.slice(0, 10), today, nextDate)
      const placed = placedOn(state.startTime, state.endTime, day)
      state.startTime = placed.startTime
      state.endTime = placed.endTime
      if (state.repeatUntil && state.repeatUntil < day) state.repeatUntil = ''
    }
    if (offset === null) return
    derivedDeadline = ''
    deadlineOffsetHours.value = offset
    state.hasDeadline = true
    followStart()
  }

  function applyEvent(ev: StationEvent) {
    state.name = ev.name ?? ''
    state.description = ev.description ?? ''
    state.eventType = ev.eventType ?? EventType.RECURRING
    state.startTime = instantToLocalInput(ev.startTime)
    state.endTime = instantToLocalInput(ev.endTime)
    applyWeekday(ev.dayOfWeek)
    state.templateId = ev.templateId != null ? String(ev.templateId) : ''
    state.categoryId = ev.categoryId != null ? String(ev.categoryId) : ''
    state.requiresRegistration = ev.requiresRegistration ?? false
    state.hasDeadline = !!ev.registrationDeadline
    state.registrationDeadline = instantToLocalInput(ev.registrationDeadline)
    state.requiresConfirmation = ev.requiresConfirmation ?? false
    state.registrationLimit = ev.registrationLimit ?? undefined
    state.queueOrder = ev.queueOrder ?? QueueOrder.TIME
    savedQueueOrder.value = state.queueOrder
    state.minRegistrations = ev.minRegistrations ?? undefined
    state.thresholdDays = ev.thresholdDays ?? undefined
    state.registrationCloseDays = ev.registrationCloseDays ?? undefined
    state.repeatUntil = ev.repeatUntil ?? ''
    state.repeatCount = ev.repeatCount ?? undefined
  }

  async function loadEvent(id: number) {
    const detail = await fetchEditDetail(id)
    applyEventFields(detail.fields)
    applyEvent(detail.event)
    state.restriction = toRestriction(detail.restrictions.register)
    state.viewRestriction = toRestriction(detail.restrictions.view)
    state.reminders = detail.reminders
  }

  function buildPayload(): EventRequest {
    return {
      name: state.name,
      description: state.description || null,
      eventType: eventTypeNamed(state.eventType) ?? EventType.ONE_TIME,
      dayOfWeek: needsDayOfWeek(state.eventType) ? Number(state.dayOfWeek) : null,
      startTime: state.startTime ? new Date(state.startTime).toISOString() : undefined,
      endTime: state.endTime ? new Date(state.endTime).toISOString() : undefined,
      templateId: state.templateId ? Number(state.templateId) : null,
      eventTemplateId: appliedTemplateId.value,
      copiedFromEventId: copiedFromId.value,
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
    return state.eventType !== EventType.ONE_TIME
  }

  function namedFields(): EventFieldEntry[] {
    return namedEntries(state.fields)
  }

  /**
   * Whether the waiting list order has to be saved after the appointment: where it has a participant
   * limit, which is what a waiting list needs, and where the order was changed. An appointment
   * without a limit keeps no waiting list, and saving an order nobody touched would be a request for
   * nothing.
   */
  function queueOrderToSave(): QueueOrder | null {
    if (state.registrationLimit || state.queueOrder !== (savedQueueOrder.value ?? QueueOrder.TIME)) {
      return state.queueOrder
    }
    return null
  }

  return {
    state,
    props,
    handlers,
    appliedTemplateId,
    copiedFromId,
    applyTemplate,
    applyCopy,
    loadEvent,
    buildPayload,
    namedFields,
    queueOrderToSave,
    endsBeforeItStarts,
  }
}

/** The weekday a `datetime-local` value falls on, 1 for Monday through 7 for Sunday, or nothing. */
function isoWeekday(moment?: string): number | null {
  if (!moment) return null
  const day = new Date(moment)
  if (Number.isNaN(day.getTime())) return null
  return ((day.getDay() + 6) % 7) + 1
}
