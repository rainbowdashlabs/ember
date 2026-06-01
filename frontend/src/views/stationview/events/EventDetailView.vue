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
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EventFieldValue from '@/components/display/EventFieldValue.vue'
import type {AttendanceTemplate, EventCategory, EventField, StationEvent} from '@/api/types'
import {EventTypes, RegistrationStatus, isRecurringEvent} from '@/api/types'
import type {AbsentMember, EventRegistrationEntry, MemberRegistrationStats} from '@/api/events'
import {attendance, events} from '@/api'
import {useSession} from '@/composables/useSession'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import CommentSection from '@/components/comment/CommentSection.vue'
import NoteEditor from '@/components/comment/NoteEditor.vue'
import MemberName from '@/components/avatar/MemberName.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {canManageEvents, canManageAttendance, sessionInfo} = useSession()
const {refresh: refreshSidebarCounts} = useSidebarCounts()

const eventId = computed(() => Number(route.params.id))
const currentMemberId = computed(() => sessionInfo.value?.member?.id ?? 0)

const event = ref<StationEvent | null>(null)
const categories = ref<EventCategory[]>([])
const templates = ref<AttendanceTemplate[]>([])
const fields = ref<EventField[]>([])
const registrations = ref<EventRegistrationEntry[]>([])
const absentMembers = ref<AbsentMember[]>([])
const registrationStats = ref<MemberRegistrationStats[]>([])
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

const registrationSummary = computed(() => {
  const accepted = registrations.value.filter(r => r.status === RegistrationStatus.ACCEPTED).length
  const pending = registrations.value.filter(r => r.status === RegistrationStatus.PENDING).length
  const denied = registrations.value.filter(r => r.status === RegistrationStatus.DENIED).length
  const declined = registrations.value.filter(r => r.status === RegistrationStatus.DECLINED).length
  return {accepted, pending, denied, declined, total: registrations.value.length}
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
    if (canManageEvents() && event.value?.requiresRegistration) {
      registrationStats.value = await events.getRegistrationStats(
          eventId.value, event.value.categoryId ?? undefined)
    }
  } catch {
    error.value = t('common.error')
  }
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

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && event">
        <!-- Header -->
        <div class="flex items-center justify-between flex-wrap gap-3">
          <div class="flex items-center gap-3">
            <SectionHeader>{{ event.name }}</SectionHeader>
            <SecondaryBadge v-if="isRecurringEvent(event.eventType)">
              <font-awesome-icon :icon="['fas', 'rotate']" class="mr-1 h-3 w-3"/>
              {{ t('events.typeRecurring') }}
            </SecondaryBadge>
            <SecondaryBadge v-else>{{ t('events.typeOneTime') }}</SecondaryBadge>
          </div>
          <div class="flex items-center gap-2">
            <SecondaryButton @click="router.push({ name: canManageEvents() ? 'events' : 'events-upcoming' })">
              <font-awesome-icon :icon="['fas', 'arrow-left']" class="mr-1"/>
              {{ t('common.back') }}
            </SecondaryButton>
            <PrimaryButton v-if="canManageEvents()" @click="router.push({ name: 'event-edit', params: { id: event.id } })">
              <font-awesome-icon :icon="['fas', 'pen']" class="mr-1"/>
              {{ t('events.editEvent') }}
            </PrimaryButton>
          </div>
        </div>

        <!-- Registration info (shown prominently below title) -->
        <div v-if="event.requiresRegistration" class="flex flex-wrap gap-3 text-sm">
          <SuccessBadge>{{ t('events.requiresRegistration') }}</SuccessBadge>
          <InfoBadge v-if="event.requiresConfirmation">{{ t('events.requiresConfirmation') }}</InfoBadge>
          <span v-if="event.registrationDeadline" class="text-(--text-muted)">
            {{ t('events.registrationDeadline') }}: {{ formatDatetime(event.registrationDeadline) }}
          </span>
        </div>

        <!-- Event Info + Fields -->
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

        <!-- Next Occurrence (recurring events) -->
        <NeutralContainer v-if="isRecurringEvent(event.eventType) && nextOccurrenceDate" class="space-y-3">
          <SubHeader>{{ t('eventDetail.nextOccurrence') }}</SubHeader>
          <p class="text-sm font-medium">{{ formatDateLong(nextOccurrenceDate) }}</p>

          <template v-if="canManageEvents() || canManageAttendance()">
            <div v-if="absentMembers.length > 0" class="space-y-2">
              <h4 class="text-xs font-semibold uppercase text-(--text-muted)">
                {{ t('eventDetail.absentMembers') }} ({{ absentMembers.length }})
              </h4>
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

        <!-- Self-registration for members -->
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

        <!-- Registrations -->
        <NeutralContainer v-if="registrations.length > 0" class="space-y-4">
          <SubHeader>{{ t('eventDetail.registrations') }}</SubHeader>

          <!-- Summary badges -->
          <div v-if="registrations.length > 0" class="flex flex-wrap gap-2">
            <SuccessBadge v-if="registrationSummary.accepted > 0">
              {{ registrationSummary.accepted }} {{ t('eventsUpcoming.accepted') }}
            </SuccessBadge>
            <InfoBadge v-if="registrationSummary.pending > 0">
              {{ registrationSummary.pending }} {{ t('eventsUpcoming.pendingCount') }}
            </InfoBadge>
            <ErrorBadge v-if="registrationSummary.denied > 0">
              {{ registrationSummary.denied }} {{ t('eventsRegistrations.deny') }}
            </ErrorBadge>
            <PrimaryBadge v-if="registrationSummary.declined > 0">
              {{ registrationSummary.declined }} {{ t('eventsUpcoming.declinedCount') }}
            </PrimaryBadge>
          </div>

          <!-- Pending registrations with stats (sorted by fairness score) -->
          <div v-if="pendingRegistrations.length > 0" class="space-y-2">
            <h4 class="text-xs font-semibold uppercase text-(--text-muted) pt-1">{{ statusLabel('PENDING') }}</h4>
            <div class="overflow-x-auto">
              <table class="w-full text-sm border-collapse">
                <thead v-if="canManageEvents() && registrationStats.length > 0">
                <tr class="border-b border-(--border) text-left text-xs text-(--text-muted) uppercase">
                  <th class="p-2">{{ t('registrationStats.member') }}</th>
                  <th class="p-2 text-center">{{ t('registrationStats.score') }}</th>
                  <th class="p-2 text-center">{{ t('registrationStats.accepted') }}</th>
                  <th class="p-2 text-center">{{ t('registrationStats.denied') }}</th>
                  <th class="p-2 text-center">{{ t('registrationStats.rate') }}</th>
                  <th class="p-2"></th>
                </tr>
                </thead>
                <tbody>
                <tr v-for="reg in pendingRegistrations" :key="reg.id" class="border-b border-(--border)">
                  <td class="p-2">
                    <MemberName :name="reg.memberName" :member-id="reg.memberId"/>
                  </td>
                  <template v-if="canManageEvents() && getStatsForMember(reg.memberId)">
                    <td class="p-2 text-center font-bold" :class="getStatsForMember(reg.memberId)!.priority === 'HIGH' ? 'text-[var(--error)]' : getStatsForMember(reg.memberId)!.priority === 'MEDIUM' ? 'text-[var(--info)]' : ''">
                      {{ getStatsForMember(reg.memberId)!.fairnessScore }}
                    </td>
                    <td class="p-2 text-center"><SuccessBadge>{{ getStatsForMember(reg.memberId)!.accepted }}</SuccessBadge></td>
                    <td class="p-2 text-center">
                      <ErrorBadge v-if="getStatsForMember(reg.memberId)!.denied > 0">{{ getStatsForMember(reg.memberId)!.denied }}</ErrorBadge>
                      <span v-else>0</span>
                    </td>
                    <td class="p-2 text-center">{{ Math.round(getStatsForMember(reg.memberId)!.acceptRate * 100) }}%</td>
                  </template>
                  <template v-else>
                    <td class="p-2 text-center text-(--text-muted)" colspan="4">–</td>
                  </template>
                  <td class="p-2">
                    <div v-if="canManageEvents() && event.requiresConfirmation" class="flex items-center gap-2 justify-end">
                      <PrimaryButton @click="acceptRegistration(reg.id)">
                        <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
                        {{ t('eventsRegistrations.accept') }}
                      </PrimaryButton>
                      <ErrorButton @click="denyRegistration(reg.id)">
                        <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1"/>
                        {{ t('eventsRegistrations.deny') }}
                      </ErrorButton>
                    </div>
                  </td>
                </tr>
                </tbody>
              </table>
            </div>
          </div>

          <!-- Non-pending registrations (accepted, declined, denied) -->
          <div v-for="group in nonPendingRegistrations" :key="group.status" class="space-y-2">
            <h4 class="text-xs font-semibold uppercase text-(--text-muted) pt-1">{{ statusLabel(group.status) }}</h4>
            <NeutralContainer v-for="reg in group.entries" :key="reg.id" class="flex items-center justify-between">
              <MemberName :name="reg.memberName" :member-id="reg.memberId"/>
              <span v-if="reg.eventDate" class="text-xs text-(--text-muted)">{{ formatDate(reg.eventDate) }}</span>
            </NeutralContainer>
          </div>
        </NeutralContainer>
      </template>

      <!-- Notes (manager only) -->
      <NeutralContainer v-if="!loading && canManageEvents()">
        <NoteEditor entity-type="EVENT" :entity-id="eventId"/>
      </NeutralContainer>

      <!-- Comments -->
      <NeutralContainer v-if="!loading">
        <CommentSection :event-id="eventId"/>
      </NeutralContainer>
    </div>
  </ViewContent>
</template>
