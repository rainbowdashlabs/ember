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
import {isRecurringEvent, type AbsentMember, type EventCategory, type EventField, type StationEvent} from '@/api/events'
import type {StationMember} from '@/api/types'
import {attendance, events, managedMembers as managedMembersApi, stationMembers} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import EventDetailBody from './eventdetailview/EventDetailBody.vue'

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

function nextOccurrence(dayOfWeek: number): string {
  const now = new Date()
  const todayDow = now.getDay() === 0 ? 7 : now.getDay()
  let daysAhead = dayOfWeek - todayDow
  if (daysAhead < 0) daysAhead += 7
  if (daysAhead === 0 && event.value?.endTime) {
    const endToday = new Date(event.value.endTime)
    if (now > endToday) daysAhead = 7
  }
  const next = new Date(now)
  next.setDate(now.getDate() + daysAhead)
  return next.toISOString().slice(0, 10)
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
 * Every downstream lookup (absences, registrations, start/end formatting) reads from this so the
 * view never mixes "template" times (raw startTime) with "occurrence" times.
 */
const effectiveDate = computed((): string | null => {
  if (focusedDate.value) return focusedDate.value
  if (nextOccurrenceDate.value) return nextOccurrenceDate.value
  if (event.value?.startTime) return new Date(event.value.startTime).toISOString().slice(0, 10)
  return null
})

function combineDateAndTime(date: string, iso: string): string {
  const parsed = new Date(iso)
  const hh = String(parsed.getHours()).padStart(2, '0')
  const mm = String(parsed.getMinutes()).padStart(2, '0')
  return `${new Date(`${date}T00:00:00`).toLocaleDateString('de-DE', {weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric'})}, ${hh}:${mm}`
}

const startFormatted = computed(() => {
  if (!event.value?.startTime || !effectiveDate.value) return ''
  return combineDateAndTime(effectiveDate.value, event.value.startTime)
})

const endFormatted = computed(() => {
  if (!event.value?.endTime || !effectiveDate.value) return ''
  return combineDateAndTime(effectiveDate.value, event.value.endTime)
})

const registrableMembers = computed((): { id: number; name: string }[] => {
  const eligible = eligibleMembers.value[eventId.value]
  const ids = eligible ?? [currentMemberId.value, ...managedMembers.value.map(m => m.id)]
  const result: { id: number; name: string }[] = []
  for (const id of ids) {
    if (id === currentMemberId.value) {
      result.push({id, name: t('eventsUpcoming.myself')})
    } else {
      const m = managedMembers.value.find(mm => mm.id === id)
      if (m) result.push({id, name: m.name ?? m.email ?? `#${id}`})
    }
  }
  return result
})

const hasManagedMembers = computed(() => managedMembers.value.length > 0)

const currentCategoryName = computed(() => {
  const id = event.value?.categoryId
  if (!id) return t('events.noCategory')
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
        @cancelled="onEventCancelled"
        @field-updated="onFieldUpdated"
    />
  </ViewContent>
</template>
