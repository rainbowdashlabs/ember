/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
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
import type {AttendanceTemplate, EventCategory, EventField, StationEvent} from '@/api/types'
import {EventTypes, RegistrationStatus, isRecurringEvent} from '@/api/types'
import type {AbsentMember, EventRegistrationEntry} from '@/api/events'
import {attendance, events} from '@/api'
import {useSession} from '@/composables/useSession'
import MemberName from '@/components/avatar/MemberName.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {canManageEvents} = useSession()

const eventId = computed(() => Number(route.params.id))

const event = ref<StationEvent | null>(null)
const categories = ref<EventCategory[]>([])
const templates = ref<AttendanceTemplate[]>([])
const fields = ref<EventField[]>([])
const registrations = ref<EventRegistrationEntry[]>([])
const absentMembers = ref<AbsentMember[]>([])
const loading = ref(true)
const error = ref('')

const dayNames = ['', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag']

const statusOrder = [RegistrationStatus.PENDING, RegistrationStatus.ACCEPTED, RegistrationStatus.DECLINED, RegistrationStatus.DENIED] as string[]

interface StatusGroup {
  status: string
  entries: EventRegistrationEntry[]
}

const groupedRegistrations = computed<StatusGroup[]>(() => {
  const byStatus = new Map<string, EventRegistrationEntry[]>()
  for (const reg of registrations.value) {
    const list = byStatus.get(reg.status) ?? []
    list.push(reg)
    byStatus.set(reg.status, list)
  }
  for (const list of byStatus.values()) {
    list.sort((a, b) => a.memberName.localeCompare(b.memberName, 'de'))
  }
  return statusOrder
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
    const [ev, cats, tmpls, flds] = await Promise.all([
      events.getEvent(eventId.value),
      events.listCategories(),
      attendance.listTemplates(),
      events.getEventFields(eventId.value),
    ])
    event.value = ev
    categories.value = cats
    templates.value = tmpls
    fields.value = flds
    await loadRegistrations()
    if (isRecurringEvent(ev.eventType) && ev.dayOfWeek) {
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

async function acceptRegistration(id: number) {
  try {
    await events.updateRegistrationStatus(id, RegistrationStatus.ACCEPTED)
    await loadRegistrations()
  } catch {
    error.value = t('common.error')
  }
}

async function denyRegistration(id: number) {
  try {
    await events.updateRegistrationStatus(id, RegistrationStatus.DENIED)
    await loadRegistrations()
  } catch {
    error.value = t('common.error')
  }
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
            <SecondaryButton @click="router.push({ name: 'events' })">
              <font-awesome-icon :icon="['fas', 'arrow-left']" class="mr-1"/>
              {{ t('common.back') }}
            </SecondaryButton>
            <PrimaryButton v-if="canManageEvents()" @click="router.push({ name: 'event-edit', params: { id: event.id } })">
              <font-awesome-icon :icon="['fas', 'pen']" class="mr-1"/>
              {{ t('events.editEvent') }}
            </PrimaryButton>
          </div>
        </div>

        <!-- Event Info -->
        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('events.general') }}</SubHeader>

          <div class="grid gap-4 sm:grid-cols-2">
            <div>
              <span class="text-xs font-medium text-(--text-muted) uppercase">{{ t('events.description') }}</span>
              <p class="text-sm">{{ event.description || '–' }}</p>
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
            <div>
              <span class="text-xs font-medium text-(--text-muted) uppercase">{{ t('events.template') }}</span>
              <p class="text-sm">{{ templateName(event.templateId) }}</p>
            </div>
          </div>
        </NeutralContainer>

        <!-- Next Occurrence (recurring events) -->
        <NeutralContainer v-if="isRecurringEvent(event.eventType) && nextOccurrenceDate" class="space-y-3">
          <SubHeader>{{ t('eventDetail.nextOccurrence') }}</SubHeader>
          <p class="text-sm font-medium">{{ formatDateLong(nextOccurrenceDate) }}</p>

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
        </NeutralContainer>

        <!-- Event Fields -->
        <NeutralContainer v-if="fields.length > 0" class="space-y-3">
          <SubHeader>{{ t('eventDetail.fields') }}</SubHeader>
          <div class="grid gap-3 sm:grid-cols-2">
            <div v-for="field in fields" :key="field.id">
              <span class="text-xs font-medium text-(--text-muted) uppercase">{{ field.name }}</span>
              <p class="text-sm">{{ field.value || '–' }}</p>
            </div>
          </div>
        </NeutralContainer>

        <!-- Registration Settings -->
        <NeutralContainer v-if="event.requiresRegistration" class="space-y-3">
          <SubHeader>{{ t('events.registration') }}</SubHeader>
          <div class="flex flex-wrap gap-3 text-sm">
            <SuccessBadge>{{ t('events.requiresRegistration') }}</SuccessBadge>
            <InfoBadge v-if="event.requiresConfirmation">{{ t('events.requiresConfirmation') }}</InfoBadge>
            <span v-if="event.registrationDeadline" class="text-(--text-muted)">
              {{ t('events.registrationDeadline') }}: {{ formatDatetime(event.registrationDeadline) }}
            </span>
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

          <!-- Grouped registration list -->
          <div v-for="group in groupedRegistrations" :key="group.status" class="space-y-2">
            <h4 class="text-xs font-semibold uppercase text-(--text-muted) pt-1">{{ statusLabel(group.status) }}</h4>
            <NeutralContainer v-for="reg in group.entries" :key="reg.id" class="flex items-center justify-between">
              <div>
                <MemberName :name="reg.memberName"/>
                <span v-if="reg.eventDate" class="ml-2 text-xs text-(--text-muted)">{{ formatDate(reg.eventDate) }}</span>
                <span v-if="reg.createdByName" class="ml-2 text-xs text-(--text-muted) italic">
                  {{ t('common.createdBy', { name: reg.createdByName }) }}
                </span>
              </div>
              <div class="flex items-center gap-2">
                <template v-if="canManageEvents() && event.requiresConfirmation && reg.status === RegistrationStatus.PENDING">
                  <PrimaryButton class="text-xs" @click="acceptRegistration(reg.id)">
                    <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
                    {{ t('eventsRegistrations.accept') }}
                  </PrimaryButton>
                  <ErrorButton class="text-xs" @click="denyRegistration(reg.id)">
                    <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1"/>
                    {{ t('eventsRegistrations.deny') }}
                  </ErrorButton>
                </template>
              </div>
            </NeutralContainer>
          </div>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
