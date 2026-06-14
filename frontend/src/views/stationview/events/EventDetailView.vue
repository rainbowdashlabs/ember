/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, nextTick, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {marked} from 'marked'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EventFieldValue from '@/components/display/EventFieldValue.vue'
import DetailLabel from '@/components/typography/DetailLabel.vue'
import type {AttendanceTemplate, EventCategory, EventField, StationEvent, StationMember} from '@/api/types'
import {isRecurringEvent, StationPermission} from '@/api/types'
import type {AbsentMember} from '@/api/events'
import {attendance, events, managedMembers as managedMembersApi} from '@/api'
import {useSession} from '@/composables/useSession'
import CommentSection from '@/components/comment/CommentSection.vue'
import NoteEditor from '@/components/comment/NoteEditor.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import EventCancelModal from './eventdetailview/EventCancelModal.vue'
import EventRegistrationsTab from './eventdetailview/EventRegistrationsTab.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {canManageEvents, canManageAttendance, isGuardian, sessionInfo, hasPermission} = useSession()

const eventId = computed(() => Number(route.params.id))
const currentMemberId = computed(() => sessionInfo.value?.member?.id ?? 0)

// Optional ISO date from the route — present on `/station/events/:id/:date` for deep
// links into a specific occurrence of a recurring event. The view binds itself to a
// single occurrence so start / end times reflect that date, not the recurrence template.
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
const eligibleMembers = ref<Record<number, number[]>>({})
const loading = ref(true)
const error = ref('')
const activeTab = ref<'info' | 'registrations'>('info')
const showCancelModal = ref(false)
const registrationsTab = ref<InstanceType<typeof EventRegistrationsTab> | null>(null)

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
 *   1. {@link focusedDate} from the URL path — explicit user / notification deep link.
 *   2. {@link nextOccurrenceDate} for a recurring event without a path date — sensible default.
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
  // Replace the date portion of the stored ISO with the bound date, keeping hours / minutes.
  const t = new Date(iso)
  const hh = String(t.getHours()).padStart(2, '0')
  const mm = String(t.getMinutes()).padStart(2, '0')
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

function categoryName(id: number | null | undefined): string {
  if (!id) return t('events.noCategory')
  return categories.value.find(c => c.id === id)?.name ?? ''
}

function templateName(id: number | null | undefined): string {
  if (!id) return t('events.noTemplate')
  return templates.value.find(tmpl => tmpl.id === id)?.name ?? ''
}

function formatDatetime(iso?: string): string {
  if (!iso) return ''
  return new Date(iso).toLocaleString('de-DE', {day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'})
}

function renderMarkdown(md: string): string {
  try { return marked.parse(md) as string } catch { return md }
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [ev, cats, flds] = await Promise.all([
      events.getEvent(eventId.value),
      events.listCategories(),
      events.getEventFields(eventId.value),
    ])
    event.value = ev
    categories.value = cats
    fields.value = flds
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
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
  await nextTick()
  await registrationsTab.value?.loadRegistrations()
}

async function loadAbsences() {
  if (!effectiveDate.value) return
  try {
    absentMembers.value = await events.listAbsencesForDate(eventId.value, effectiveDate.value)
  } catch { absentMembers.value = [] }
}

async function onEventCancelled() {
  showCancelModal.value = false
  await loadData()
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && event">
        <Alert v-if="event.cancelled" variant="error">
          <span class="font-bold">{{ t('events.cancelled') }}</span>
          <span v-if="event.cancelReason"> — {{ event.cancelReason }}</span>
          <span v-if="event.cancelledAt" class="text-xs opacity-75 ml-2">{{ formatDatetime(event.cancelledAt) }}</span>
        </Alert>

        <div class="flex items-center justify-between flex-wrap gap-3">
          <div class="flex items-center gap-3">
            <SectionHeader>{{ event.name }}</SectionHeader>
            <SecondaryBadge v-if="isRecurringEvent(event.eventType)"><font-awesome-icon :icon="['fas', 'rotate']" class="mr-1 h-3 w-3"/>{{ t('events.typeRecurring') }}</SecondaryBadge>
            <SecondaryBadge v-else>{{ t('events.typeOneTime') }}</SecondaryBadge>
            <ErrorBadge v-if="event.cancelled">{{ t('events.cancelled') }}</ErrorBadge>
          </div>
          <div class="flex items-center gap-2">
            <SecondaryButton @click="router.push({ name: canManageEvents() ? 'events' : 'events-upcoming' })"><font-awesome-icon :icon="['fas', 'arrow-left']" class="mr-1"/>{{ t('common.back') }}</SecondaryButton>
            <ErrorButton v-if="canManageEvents() && !event.cancelled" @click="showCancelModal = true"><font-awesome-icon :icon="['fas', 'ban']" class="mr-1"/>{{ t('events.cancelEvent') }}</ErrorButton>
            <PrimaryButton v-if="canManageEvents()" @click="router.push({ name: 'event-edit', params: { id: event.id } })"><font-awesome-icon :icon="['fas', 'pen']" class="mr-1"/>{{ t('events.editEvent') }}</PrimaryButton>
          </div>
        </div>

        <div v-if="event.requiresRegistration" class="flex flex-wrap gap-3 text-sm">
          <SuccessBadge>{{ t('events.requiresRegistration') }}</SuccessBadge>
          <InfoBadge v-if="event.requiresConfirmation">{{ t('events.requiresConfirmation') }}</InfoBadge>
          <span v-if="event.registrationDeadline" class="text-(--text-muted)">{{ t('events.registrationDeadline') }}: {{ formatDatetime(event.registrationDeadline) }}</span>
          <span v-if="event.minRegistrations" class="text-(--text-muted)">{{ t('events.minRegistrations') }}: {{ event.minRegistrations }}</span>
          <span v-if="event.thresholdDate" class="text-(--text-muted)">{{ t('events.thresholdDate') }}: {{ formatDatetime(event.thresholdDate) }}</span>
        </div>

        <div v-if="reminders.length > 0" class="flex flex-wrap gap-2 text-sm">
          <span class="text-(--text-muted)">{{ t('eventEdit.reminders') }}:</span>
          <InfoBadge v-for="days in reminders" :key="days">{{ days }} {{ t('eventEdit.daysBefore') }}</InfoBadge>
        </div>

        <TabBar v-if="event.requiresRegistration" v-model="activeTab"
                :tabs="[{key: 'info', label: t('eventDetail.tabInfo')}, {key: 'registrations', label: t('eventDetail.tabRegistrations')}]" />

        <!-- Info tab -->
        <template v-if="activeTab === 'info' || !event.requiresRegistration">
          <NeutralContainer class="space-y-3">
            <SubHeader>{{ t('events.general') }}</SubHeader>
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="sm:col-span-2">
                <DetailLabel>{{ t('events.description') }}</DetailLabel>
                <div v-if="event.description" class="prose prose-sm dark:prose-invert max-w-none mt-1" v-html="renderMarkdown(event.description)"/>
                <p v-else class="text-sm">–</p>
              </div>
              <div>
                <DetailLabel>{{ t('events.category') }}</DetailLabel>
                <p class="text-sm">{{ categoryName(event.categoryId) }}</p>
              </div>
              <div>
                <DetailLabel>{{ t('events.startTime') }}</DetailLabel>
                <p class="text-sm">{{ startFormatted }}</p>
              </div>
              <div>
                <DetailLabel>{{ t('events.endTime') }}</DetailLabel>
                <p class="text-sm">{{ endFormatted }}</p>
              </div>
              <div v-if="canManageEvents()">
                <DetailLabel>{{ t('events.template') }}</DetailLabel>
                <p class="text-sm">{{ templateName(event.templateId) }}</p>
              </div>
              <div v-for="field in fields" :key="field.id">
                <DetailLabel>{{ field.name }}</DetailLabel>
                <p class="text-sm"><EventFieldValue :field-type="field.fieldType" :value="field.value"/></p>
              </div>
            </div>
          </NeutralContainer>

          <NeutralContainer v-if="isRecurringEvent(event.eventType) && (canManageEvents() || canManageAttendance())" class="space-y-3">
            <SubHeader>{{ t('eventDetail.absentMembers') }}</SubHeader>
            <div v-if="absentMembers.length > 0" class="flex flex-wrap gap-2">
              <ErrorBadge v-for="m in absentMembers" :key="m.memberId">{{ m.memberName }}<span v-if="m.reason" class="ml-1 opacity-75">– {{ m.reason }}</span></ErrorBadge>
            </div>
            <p v-else class="text-sm text-(--text-muted)">{{ t('eventDetail.noAbsences') }}</p>
          </NeutralContainer>

          <!-- Event notes require EVENT_EDIT (not the broader EVENT_MANAGER / canManageEvents).
               Matches the backend gate on /api/v1/notes/EVENT/{id}. -->
          <NeutralContainer v-if="hasPermission(StationPermission.EVENT_EDIT)">
            <NoteEditor entity-type="EVENT" :entity-id="eventId"/>
          </NeutralContainer>

          <NeutralContainer>
            <CommentSection :event-id="eventId" :event-date="focusedDate"/>
          </NeutralContainer>
        </template>

        <!-- Registrations tab -->
        <EventRegistrationsTab
            v-show="activeTab === 'registrations' && event.requiresRegistration"
            ref="registrationsTab"
            :event="event"
            :event-id="eventId"
            :current-member-id="currentMemberId"
            :registrable-members="registrableMembers"
            :has-managed-members="hasManagedMembers"
            :next-occurrence-date="effectiveDate"
        />

        <EventCancelModal
            v-if="event"
            :show="showCancelModal"
            :event-id="event.id"
            @close="showCancelModal = false"
            @cancelled="onEventCancelled"
        />
      </template>
    </div>
  </ViewContent>
</template>
