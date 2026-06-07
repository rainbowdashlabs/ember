/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
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
import type {AttendanceTemplate, EventCategory, EventField, StationEvent} from '@/api/types'
import {EventTypes, RegistrationStatus, StationPermission, isRecurringEvent} from '@/api/types'
import type {AbsentMember, EventRegistrationEntry, MemberRegistrationStats, FederatedEventRegistration} from '@/api/events'
import {attendance, events, stationMembers} from '@/api'
import {useSession} from '@/composables/useSession'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import CommentSection from '@/components/comment/CommentSection.vue'
import NoteEditor from '@/components/comment/NoteEditor.vue'
import EventCancelModal from './eventdetailview/EventCancelModal.vue'
import FederatedRegistrationsPanel from './eventdetailview/FederatedRegistrationsPanel.vue'
import RegistrationsPanel from './eventdetailview/RegistrationsPanel.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {canManageEvents, canManageAttendance, hasPermission, sessionInfo} = useSession()
const {refresh: refreshSidebarCounts} = useSidebarCounts()

const eventId = computed(() => Number(route.params.id))
const currentMemberId = computed(() => sessionInfo.value?.member?.id ?? 0)

const event = ref<StationEvent | null>(null)
const categories = ref<EventCategory[]>([])
const templates = ref<AttendanceTemplate[]>([])
const fields = ref<EventField[]>([])
const reminders = ref<number[]>([])
const registrations = ref<EventRegistrationEntry[]>([])
const absentMembers = ref<AbsentMember[]>([])
const registrationStats = ref<MemberRegistrationStats[]>([])
const federatedRegs = ref<FederatedEventRegistration[]>([])
const allMembers = ref<{ id: number; name: string }[]>([])
const loading = ref(true)
const error = ref('')

const dayNames = ['', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag']

interface StatusGroup {
  status: string
  entries: EventRegistrationEntry[]
}

const pendingRegistrations = computed(() => {
  const pending = registrations.value.filter(r => r.status === RegistrationStatus.PENDING)
  // Sort by fairness score (highest first = most deserving)
  return [...pending].sort((a, b) => {
    const sa = getStatsForMember(a.memberId)
    const sb = getStatsForMember(b.memberId)
    return (sb?.fairnessScore ?? 0) - (sa?.fairnessScore ?? 0)
  })
})

const nonPendingRegistrations = computed<StatusGroup[]>(() => {
  const byStatus = new Map<string, EventRegistrationEntry[]>()
  for (const reg of registrations.value) {
    if (reg.status === RegistrationStatus.PENDING) continue
    const list = byStatus.get(reg.status) ?? []
    list.push(reg)
    byStatus.set(reg.status, list)
  }
  for (const list of byStatus.values()) {
    list.sort((a, b) => a.memberName.localeCompare(b.memberName, 'de'))
  }
  return [RegistrationStatus.ACCEPTED, RegistrationStatus.DECLINED, RegistrationStatus.DENIED]
      .filter(s => byStatus.has(s))
      .map(s => ({status: s, entries: byStatus.get(s)!}))
})

function nextOccurrence(dayOfWeek: number): string {
  const today = new Date()
  const todayDow = today.getDay() === 0 ? 7 : today.getDay() // ISO: 1=Mon..7=Sun
  let daysAhead = dayOfWeek - todayDow
  if (daysAhead <= 0) daysAhead += 7
  const next = new Date(today)
  next.setDate(today.getDate() + daysAhead)
  return next.toISOString().slice(0, 10)
}

const nextOccurrenceDate = computed(() => {
  if (!event.value || event.value.eventType !== EventTypes.RECURRING || !event.value.dayOfWeek) return null
  // Only show next occurrence for weekly recurring
  return nextOccurrence(event.value.dayOfWeek)
})

function statusLabel(status: string): string {
  if (status === RegistrationStatus.ACCEPTED) return t('eventsUpcoming.statusAccepted')
  if (status === RegistrationStatus.PENDING) return t('eventsUpcoming.statusPending')
  if (status === RegistrationStatus.DENIED) return t('eventsUpcoming.statusDenied')
  if (status === RegistrationStatus.DECLINED) return t('eventsUpcoming.statusDeclined')
  return status
}

function getStatsForMember(memberId: number): MemberRegistrationStats | undefined {
  return registrationStats.value.find(s => s.memberId === memberId)
}

function renderMarkdown(md: string): string {
  try {
    return marked.parse(md) as string
  } catch {
    return md
  }
}

function categoryName(id: number | null | undefined): string {
  if (!id) return t('events.noCategory')
  return categories.value.find(c => c.id === id)?.name ?? ''
}

function templateName(id: number | null | undefined): string {
  if (!id) return t('events.noTemplate')
  return templates.value.find(tmpl => tmpl.id === id)?.name ?? ''
}

function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString('de-DE', {day: '2-digit', month: '2-digit', year: 'numeric'})
}

function formatDateLong(dateStr?: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString('de-DE', {weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric'})
}

function formatDatetime(iso?: string): string {
  if (!iso) return ''
  return new Date(iso).toLocaleString('de-DE', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
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
    await loadRegistrations()
    if ((canManageEvents() || canManageAttendance()) && isRecurringEvent(ev.eventType) && ev.dayOfWeek) {
      await loadAbsences()
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function loadRegistrations() {
  try {
    registrations.value = await events.listEventRegistrations(eventId.value)
    if (hasPermission(StationPermission.EVENT_REGISTRATION) && event.value?.requiresRegistration) {
      registrationStats.value = await events.getRegistrationStats(
          eventId.value, event.value.categoryId ?? undefined)
    }
    if (hasPermission(StationPermission.EVENT_REGISTRATION)) {
      federatedRegs.value = await events.listFederationRegistrations(eventId.value).catch(() => [])
      const members = await stationMembers.listMembers().catch(() => [])
      allMembers.value = members.map(m => ({ id: m.id, name: m.name ?? m.email ?? `#${m.id}` }))
    }
  } catch {
    error.value = t('common.error')
  }
}

async function acceptFederatedReg(regId: number) {
  await events.updateFederationRegistrationStatus(regId, 'ACCEPTED')
  await loadRegistrations()
}

async function denyFederatedReg(regId: number) {
  await events.updateFederationRegistrationStatus(regId, 'DENIED')
  await loadRegistrations()
}

async function loadAbsences() {
  if (!nextOccurrenceDate.value) return
  try {
    absentMembers.value = await events.listAbsencesForDate(eventId.value, nextOccurrenceDate.value)
  } catch {
    // Not critical — may lack permission
    absentMembers.value = []
  }
}

async function reloadRegistrationsAndCounts() {
  await loadRegistrations()
  refreshSidebarCounts()
}

async function acceptRegistration(id: number) {
  try {
    await events.updateRegistrationStatus(id, RegistrationStatus.ACCEPTED)
    await reloadRegistrationsAndCounts()
  } catch { error.value = t('common.error') }
}

async function denyRegistration(id: number) {
  try {
    await events.updateRegistrationStatus(id, RegistrationStatus.DENIED)
    await reloadRegistrationsAndCounts()
  } catch { error.value = t('common.error') }
}

// Self-registration for members
const myRegistration = computed(() => registrations.value.find(r => r.memberId === currentMemberId.value))
const registering = ref(false)
const showCancelModal = ref(false)

async function onEventCancelled() {
  showCancelModal.value = false
  await loadData()
}

async function registerSelf() {
  if (!event.value) return
  registering.value = true
  try {
    await events.registerForEvent(event.value.id, {memberId: currentMemberId.value})
    await reloadRegistrationsAndCounts()
  } catch { error.value = t('common.error') }
  finally { registering.value = false }
}

async function declineSelf() {
  if (!event.value) return
  registering.value = true
  try {
    await events.declineEvent(event.value.id, {memberId: currentMemberId.value})
    await reloadRegistrationsAndCounts()
  } catch { error.value = t('common.error') }
  finally { registering.value = false }
}

async function withdrawSelf() {
  if (!myRegistration.value) return
  registering.value = true
  try {
    await events.withdrawRegistration(myRegistration.value.id)
    await reloadRegistrationsAndCounts()
  } catch { error.value = t('common.error') }
  finally { registering.value = false }
}

const manualRegisterMemberId = ref('')
const unregisteredMembers = computed(() => {
  const regIds = new Set(registrations.value.map(r => r.memberId))
  return allMembers.value.filter(m => !regIds.has(m.id)).sort((a, b) => a.name.localeCompare(b.name))
})

async function manualRegister() {
  if (!event.value || !manualRegisterMemberId.value) return
  try {
    await events.registerForEvent(event.value.id, { memberId: Number(manualRegisterMemberId.value) })
    manualRegisterMemberId.value = ''
    await reloadRegistrationsAndCounts()
  } catch { error.value = t('common.error') }
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && event">
        <!-- Cancelled banner -->
        <Alert v-if="event.cancelled" variant="error">
          <span class="font-bold">{{ t('events.cancelled') }}</span>
          <span v-if="event.cancelReason"> — {{ event.cancelReason }}</span>
          <span v-if="event.cancelledAt" class="text-xs opacity-75 ml-2">{{ formatDatetime(event.cancelledAt) }}</span>
        </Alert>

        <!-- Header -->
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

        <!-- Registration info -->
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

        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('events.general') }}</SubHeader>
          <div class="grid gap-4 sm:grid-cols-2">
            <div class="sm:col-span-2">
              <span class="text-xs font-medium text-(--text-muted) uppercase">{{ t('events.description') }}</span>
              <div v-if="event.description" class="prose prose-sm dark:prose-invert max-w-none mt-1" v-html="renderMarkdown(event.description)"/>
              <p v-else class="text-sm">–</p>
            </div>
            <div>
              <span class="text-xs font-medium text-(--text-muted) uppercase">{{ t('events.category') }}</span>
              <p class="text-sm">{{ categoryName(event.categoryId) }}</p>
            </div>
            <div v-if="isRecurringEvent(event.eventType)">
              <span class="text-xs font-medium text-(--text-muted) uppercase">{{ t('events.dayOfWeek') }}</span>
              <p class="text-sm">{{ event.dayOfWeek ? dayNames[event.dayOfWeek] : '–' }}</p>
            </div>
            <div v-else>
              <span class="text-xs font-medium text-(--text-muted) uppercase">{{ t('events.date') }}</span>
              <p class="text-sm">{{ formatDate(event.startTime) }}</p>
            </div>
            <div>
              <span class="text-xs font-medium text-(--text-muted) uppercase">{{ t('events.startTime') }} – {{ t('events.endTime') }}</span>
              <p class="text-sm">{{ formatTime(event.startTime) }} – {{ formatTime(event.endTime) }}</p>
            </div>
            <div v-if="canManageEvents()">
              <span class="text-xs font-medium text-(--text-muted) uppercase">{{ t('events.template') }}</span>
              <p class="text-sm">{{ templateName(event.templateId) }}</p>
            </div>
            <!-- Event Fields inline -->
            <div v-for="field in fields" :key="field.id">
              <span class="text-xs font-medium text-(--text-muted) uppercase">{{ field.name }}</span>
              <p class="text-sm">
                <EventFieldValue :field-type="field.fieldType" :value="field.value"/>
              </p>
            </div>
          </div>
        </NeutralContainer>

        <NeutralContainer v-if="isRecurringEvent(event.eventType) && nextOccurrenceDate" class="space-y-3">
          <SubHeader>{{ t('eventDetail.nextOccurrence') }}</SubHeader>
          <p class="text-sm font-medium">{{ formatDateLong(nextOccurrenceDate) }}</p>
          <template v-if="canManageEvents() || canManageAttendance()">
            <div v-if="absentMembers.length > 0" class="space-y-2">
              <h4 class="text-xs font-semibold uppercase text-(--text-muted)">{{ t('eventDetail.absentMembers') }} ({{ absentMembers.length }})</h4>
              <div class="flex flex-wrap gap-2">
                <ErrorBadge v-for="m in absentMembers" :key="m.memberId">
                  {{ m.memberName }}
                  <span v-if="m.reason" class="ml-1 opacity-75">– {{ m.reason }}</span>
                </ErrorBadge>
              </div>
            </div>
            <p v-else class="text-sm text-(--text-muted)">{{ t('eventDetail.noAbsences') }}</p>
          </template>
        </NeutralContainer>

        <NeutralContainer v-if="event.requiresRegistration && !canManageEvents()" class="space-y-3">
          <SubHeader>{{ t('eventDetail.myRegistration') }}</SubHeader>
          <div v-if="myRegistration" class="flex items-center gap-3">
            <component :is="myRegistration.status === RegistrationStatus.ACCEPTED ? SuccessBadge : myRegistration.status === RegistrationStatus.PENDING ? InfoBadge : ErrorBadge">
              {{ statusLabel(myRegistration.status) }}
            </component>
            <SecondaryButton v-if="myRegistration.status !== RegistrationStatus.DECLINED" :disabled="registering" @click="withdrawSelf">
              <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1"/>
              {{ t('eventsUpcoming.withdraw') }}
            </SecondaryButton>
          </div>
          <div v-else class="flex gap-2">
            <PrimaryButton :disabled="registering" @click="registerSelf">
              <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
              {{ t('eventsUpcoming.register') }}
            </PrimaryButton>
            <ErrorButton :disabled="registering" @click="declineSelf">
              <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1"/>
              {{ t('eventsUpcoming.decline') }}
            </ErrorButton>
          </div>
        </NeutralContainer>

        <RegistrationsPanel
            :event="event"
            :registrations="registrations"
            :pending-registrations="pendingRegistrations"
            :non-pending-registrations="nonPendingRegistrations"
            :registration-stats="registrationStats"
            :unregistered-members="unregisteredMembers"
            v-model:manual-register-member-id="manualRegisterMemberId"
            @accept="acceptRegistration"
            @deny="denyRegistration"
            @manual-register="manualRegister"
        />
      </template>

      <FederatedRegistrationsPanel
          v-if="!loading && canManageEvents()"
          :registrations="federatedRegs"
          @accept="acceptFederatedReg"
          @deny="denyFederatedReg"
      />

      <!-- Notes (manager only) -->
      <NeutralContainer v-if="!loading && canManageEvents()">
        <NoteEditor entity-type="EVENT" :entity-id="eventId"/>
      </NeutralContainer>

      <!-- Comments -->
      <NeutralContainer v-if="!loading">
        <CommentSection :event-id="eventId"/>
      </NeutralContainer>

      <!-- Cancel Event Modal -->
      <EventCancelModal
          v-if="event"
          :show="showCancelModal"
          :event-id="event.id"
          @close="showCancelModal = false"
          @cancelled="onEventCancelled"
      />
    </div>
  </ViewContent>
</template>
