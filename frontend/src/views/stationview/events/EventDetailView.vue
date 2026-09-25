/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
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
import {formatTime, formatWeekdayDate, stationDayOf} from '@/util/format'

const {t} = useI18n()
const route = useRoute()
const {
  canManageEvents,
  canManageAttendance,
  isGuardian,
  sessionInfo,
  stationTimezone,
  hasPermission,
} = useSession()

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
 * The next date this appointment falls on, as the server names it.
 *
 * <p>Worked out here once, by stepping to the next matching weekday. That is only the rule a weekly
 * appointment keeps: a monthly or quarterly one was sent to the wrong day of the month, and every
 * series read straight past the weeks its station is off and past the date it runs to. The server
 * holds all three rules already, for the feeds and the attendance sheets, so it is asked.
 */
const nextOccurrenceDate = ref<string | null>(null)

/**
 * The single date this view is bound to: the one named in the address where somebody was sent to a
 * particular day, and otherwise the next day the appointment falls on.
 *
 * <p>Every lookup keyed by an occurrence - absences, sign-ups, the gear claimed for it - reads from
 * here, so this is the name the server knows the occurrence by and not the day the reader sees. The
 * server names an occurrence after the day it falls on where the station stands, so that is the
 * clock this asks: reading the day off the stored moment put the page a day ahead of its own
 * sign-ups for every appointment made late in the evening, and reading it off the reader's clock
 * does the same to anybody sitting in another zone.
 *
 * <p>Nothing until the appointment has been read, which is why the body waits for it. A guess made
 * in the meantime would be a day the page then asks its sign-ups under.
 */
const effectiveDate = computed((): string | null => focusedDate.value ?? nextOccurrenceDate.value)

/**
 * The day written above a time on this page.
 *
 * <p>A repeating appointment is shown on the occurrence the page is bound to, whose clock is the
 * one it repeats. A one-off is shown on the day its own moment falls on where the station stands,
 * which is the same day {@link effectiveDate} looks its sign-ups up under: the day drawn and the
 * day asked for have to be the one day, or the page reads as though it were showing a date it
 * has nothing for.
 */
function dayShownFor(iso: string): string {
  return (
    focusedDate.value ??
    nextOccurrenceDate.value ??
    stationDayOf(new Date(iso), stationTimezone.value)
  )
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

/**
 * The appointment's own name at the head of the page, because "Termin" stands over every one of
 * them and is what the tab, the history and a bookmark end up carrying. The word is what stands
 * there until the appointment has arrived, and where it could not be fetched at all.
 */
const pageTitle = computed(() => event.value?.name || t('pages.event-detail.title'))

/**
 * The day the page is bound to, under the name. A repeating appointment is a different page on
 * every date it falls on, all of them called the same thing, so the date is what tells them apart.
 */
const pageSubtitle = computed(() =>
    formatWeekdayDate(effectiveDate.value) || t('pages.event-detail.subtitle'))

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

const {loading, failure, reload} = useAsyncLoader(async () => {
  const [ev, cats, flds, completions, nextDate] = await Promise.all([
    events.getEvent(eventId.value),
    events.listCategories(),
    events.getEventFields(eventId.value),
    stationMembers.listCompletions().catch(() => []),
    events.getNextDate(eventId.value).catch(() => null),
  ])
  event.value = ev
  nextOccurrenceDate.value = nextDate
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

/**
 * Reads the questions again for the occurrence on screen.
 *
 * <p>Which occurrence that is follows from the appointment, so it is not known when the page first
 * loads, and a question answered per date is answered differently on each of them. Paging to another
 * date therefore has to ask again.
 */
watch(effectiveDate, async date => {
  if (!date) return
  fields.value = await events.getEventFields(eventId.value, date).catch(() => fields.value)
})

async function loadAbsences() {
  if (!effectiveDate.value) return
  try {
    absentMembers.value = await events.listAbsencesForDate(eventId.value, effectiveDate.value)
  } catch { absentMembers.value = [] }
}

const answer = useEventAnswer(currentMemberId, reloadMyRegistrations, failure)

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
      :title="pageTitle"
      :subtitle="pageSubtitle"
  >
    <Spinner v-if="loading" size="lg"/>
    <FailureAlert :failure="failure"/>
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
        @answers-updated="reloadMyRegistrations"
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
