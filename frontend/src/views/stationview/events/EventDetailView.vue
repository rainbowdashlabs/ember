/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {AttendanceTemplate} from '@/api/attendance'
import {isRecurringEvent, type AbsentMember, type EventCategory, type EventField, type EventRegistrationEntry, type StationEvent} from '@/api/events'
import type {StationMember} from '@/api/types'
import {attendance, events, managedMembers as managedMembersApi, stationMembers} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import EventDetailBody from './eventdetailview/EventDetailBody.vue'
import EventAnswerDialog from './eventshared/EventAnswerDialog.vue'
import {useEventAnswer} from '@/composables/useEventAnswer'
import type {AnswerablePerson} from '@/util/eventAnswers'
import {formatTime, formatWeekdayDate, instantToDate, toIsoDate} from '@/util/format'

const {t} = useI18n()
const route = useRoute()
const {canManageEvents, canManageAttendance, isGuardian, sessionInfo, hasPermission} = useSession()

const eventId = computed(() => Number(route.params.id))
const currentMemberId = computed(() => sessionInfo.value?.member?.id ?? 0)

const focusedDate = computed(() => {
  const raw = Array.isArray(route.params.date) ? route.params.date[0] : route.params.date
  if (!raw) return null
  return /^\d{4}-\d{2}-\d{2}$/.test(raw) ? raw : null
})

const event = ref<StationEvent | null>(null)
const categories = ref<EventCategory[]>([])
const templates = ref<AttendanceTemplate[]>([])
const fields = ref<EventField[]>([])
const reminders = ref<number[]>([])
const absentMembers = ref<AbsentMember[]>([])
const managedMembers = ref<StationMember[]>([])
const allMembers = ref<StationMember[]>([])
const eligibleMembers = ref<Record<number, number[]>>({})
const allMyRegistrations = ref<EventRegistrationEntry[]>([])

/** The answers given for this appointment on the date being looked at, and no other. */
const myRegistrations = computed(() => allMyRegistrations.value.filter(
    (registration: EventRegistrationEntry) =>
        registration.eventId === eventId.value && registration.eventDate === effectiveDate.value))

async function reloadMyRegistrations() {
  allMyRegistrations.value = await events.listMyRegistrations().catch(() => [])
}

/**
 * The next date this repeating appointment falls on.
 *
 * <p>Today counts as long as today's evening is still ahead. The comparison is against the clock
 * the appointment ends at rather than against the stored end of its very first evening, which lies
 * in the past for every series that has run once and used to send the reader a week forward on the
 * one day the appointment actually takes place.
 */
function nextOccurrence(dayOfWeek: number): string {
  const now = new Date()
  const todayDow = now.getDay() === 0 ? 7 : now.getDay()
  let daysAhead = dayOfWeek - todayDow
  if (daysAhead < 0) daysAhead += 7
  if (daysAhead === 0 && event.value?.endTime) {
    const end = new Date(event.value.endTime)
    const endToday = new Date(now)
    endToday.setHours(end.getHours(), end.getMinutes(), 0, 0)
    if (now > endToday) daysAhead = 7
  }
  const next = new Date(now)
  next.setDate(now.getDate() + daysAhead)
  return toIsoDate(next)
}

const nextOccurrenceDate = computed(() => {
  if (!event.value || !isRecurringEvent(event.value.eventType) || !event.value.dayOfWeek) return null
  return nextOccurrence(event.value.dayOfWeek)
})

/**
 * The single date this view is bound to. Priority:
 *   1. {@link focusedDate} from the URL path - explicit user / notification deep link.
 *   2. {@link nextOccurrenceDate} for a recurring event without a path date - sensible default.
 *   3. The event's {@code startTime} date for one-time events.
 *
 * <p>Every lookup keyed by an evening - absences, sign-ups, the gear claimed for it - reads from
 * here, so this is the name the server knows the evening by and not the day the reader sees. For a
 * one-off the server names it after the start time, and it is asked rather than guessed at.
 */
const effectiveDate = computed((): string | null => {
  if (focusedDate.value) return focusedDate.value
  if (nextOccurrenceDate.value) return nextOccurrenceDate.value
  if (event.value?.startTime) return instantToDate(event.value.startTime)
  return null
})

/**
 * The day written above a time on this page.
 *
 * <p>A repeating appointment is shown on the occurrence the page is bound to, whose clock is the
 * one it repeats. A one-off is shown on the day its own moment falls on where the reader stands, so
 * the day and the clock beside it are read off one and the same moment rather than off two.
 */
function dayShownFor(iso: string): string {
  return focusedDate.value ?? nextOccurrenceDate.value ?? toIsoDate(new Date(iso))
}

function combineDateAndTime(iso: string): string {
  return `${formatWeekdayDate(dayShownFor(iso))}, ${formatTime(iso)}`
}

const startFormatted = computed(() => {
  if (!event.value?.startTime) return ''
  return combineDateAndTime(event.value.startTime)
})

const endFormatted = computed(() => {
  if (!event.value?.endTime) return ''
  return combineDateAndTime(event.value.endTime)
})

const registrableMembers = computed((): AnswerablePerson[] => {
  const eligible = eligibleMembers.value[eventId.value]
  const ids = eligible ?? [currentMemberId.value, ...managedMembers.value.map(m => m.id)]
  const result: AnswerablePerson[] = []
  for (const id of ids) {
    if (id === currentMemberId.value) {
      result.push({key: id, name: t('eventsUpcoming.myself')})
    } else {
      const m = managedMembers.value.find(mm => mm.id === id)
      if (m) result.push({key: id, name: m.name ?? m.email ?? `#${id}`})
    }
  }
  return result
})

const hasManagedMembers = computed(() => managedMembers.value.length > 0)

/** What the event is called a kind of, empty where it is called nothing: the badge then stays away. */
const currentCategoryName = computed(() => {
  const id = event.value?.categoryId
  if (!id) return ''
  return categories.value.find(c => c.id === id)?.name ?? ''
})

const currentTemplateName = computed(() => {
  const id = event.value?.templateId
  if (!id) return t('events.noTemplate')
  return templates.value.find(tmpl => tmpl.id === id)?.name ?? ''
})

const {loading, error, reload} = useAsyncLoader(async () => {
  const [ev, cats, flds, completions] = await Promise.all([
    events.getEvent(eventId.value),
    events.listCategories(),
    events.getEventFields(eventId.value),
    stationMembers.listCompletions().catch(() => []),
  ])
  event.value = ev
  categories.value = cats
  fields.value = flds
  allMembers.value = completions.map(c => ({
    id: c.id,
    stationId: '',
    accountId: 0,
    name: c.name,
  }))
  try { reminders.value = await events.getEventReminders(eventId.value) } catch { reminders.value = [] }
  await reloadMyRegistrations()
  if (canManageEvents()) {
    templates.value = await attendance.listTemplates()
  }
  if (isGuardian()) {
    const [managed, elig] = await Promise.all([
      managedMembersApi.listManaged(),
      events.listEligibleMembers(),
    ])
    managedMembers.value = managed.map(m => ({
      id: m.id, stationId: m.stationId, accountId: m.accountId, name: m.name, email: m.email,
    }))
    eligibleMembers.value = elig
  }
  if ((canManageEvents() || canManageAttendance()) && isRecurringEvent(ev.eventType) && ev.dayOfWeek) {
    await loadAbsences()
  }
})

async function loadAbsences() {
  if (!effectiveDate.value) return
  try {
    absentMembers.value = await events.listAbsencesForDate(eventId.value, effectiveDate.value)
  } catch { absentMembers.value = [] }
}

const answer = useEventAnswer(currentMemberId, reloadMyRegistrations, error)

/** Signing up and refusing both need the appointment and the date they are about. */
async function onRegister(people: AnswerablePerson[]) {
  if (!event.value || !effectiveDate.value) return
  await answer.registerFor(event.value, effectiveDate.value, people)
}

async function onDecline(people: AnswerablePerson[]) {
  if (!event.value || !effectiveDate.value) return
  await answer.declineFor(event.value, effectiveDate.value, people)
}

async function onEventCancelled() {
  await reload()
}

function onFieldUpdated(field: EventField) {
  const i = fields.value.findIndex(f => f.id === field.id)
  if (i >= 0) fields.value.splice(i, 1, field)
}
</script>

<template>
  <ViewContent
      :title="t('pages.event-detail.title')"
      :subtitle="t('pages.event-detail.subtitle')"
  >
    <Spinner v-if="loading" size="lg"/>
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <EventDetailBody
        v-if="!loading && event"
        :event="event"
        :event-id="eventId"
        :fields="fields"
        :all-members="allMembers"
        :reminders="reminders"
        :absent-members="absentMembers"
        :focused-date="focusedDate"
        :effective-date="effectiveDate"
        :start-formatted="startFormatted"
        :end-formatted="endFormatted"
        :category-name="currentCategoryName"
        :template-name="currentTemplateName"
        :current-member-id="currentMemberId"
        :registrable-members="registrableMembers"
        :has-managed-members="hasManagedMembers"
        :can-manage-events="canManageEvents()"
        :can-manage-attendance="canManageAttendance()"
        :has-permission="hasPermission"
        :my-registrations="myRegistrations"
        :registering="answer.registering.value !== null"
        @cancelled="onEventCancelled"
        @field-updated="onFieldUpdated"
        @register="onRegister"
        @decline="onDecline"
        @withdraw="answer.withdrawRegistration"
    />

    <EventAnswerDialog
        :model-value="answer.answerPrompt.value !== null"
        :people="answer.answerPrompt.value?.people ?? []"
        :fields="answer.answerPrompt.value?.fields ?? []"
        :attending="answer.answerPrompt.value?.attending ?? true"
        :busy="!!answer.registering.value"
        @update:model-value="shown => { if (!shown) answer.cancelAnswerPrompt() }"
        @confirm="answer.confirmAnswerPrompt"
    />
  </ViewContent>
</template>
