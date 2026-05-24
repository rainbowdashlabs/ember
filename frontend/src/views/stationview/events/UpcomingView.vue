/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {EventBreak, StationEvent, StationMember} from '@/api/types'
import {EventTypes, RegistrationStatus, isRecurringEvent} from '@/api/types'
import {events, managedMembers as managedMembersApi} from '@/api'
import type {EventRegistrationEntry, RegistrationCount} from '@/api/events'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const router = useRouter()
const {sessionInfo, loaded, isGuardian, canManageAttendance} = useSession()

const allEvents = ref<StationEvent[]>([])
const todayEvents = ref<StationEvent[]>([])

const breaks = ref<EventBreak[]>([])
const myRegistrations = ref<EventRegistrationEntry[]>([])
// eventId -> eligible memberIds (missing = all eligible)
const eligibleMembers = ref<Record<number, number[]>>({})
const managedMembers = ref<StationMember[]>([])
const registrationCounts = ref<RegistrationCount[]>([])
const loading = ref(true)
const error = ref('')
const registering = ref<string | null>(null)
const selectedMemberForEvent = ref<Map<string, string>>(new Map())

const dayNames = ['', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag']

const currentMemberId = computed(() => sessionInfo.value?.member?.id ?? 0)

interface UpcomingEvent {
  event: StationEvent
  date: string
  dayLabel: string
}

function isEventRelevant(eventId: number): boolean {
  // No restriction entry = all eligible
  const eligible = eligibleMembers.value[eventId]
  if (eligible === undefined) return true
  return eligible.length > 0
}

function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const filteredTodayEvents = computed(() => todayEvents.value.filter(ev => isEventRelevant(ev.id)))

const upcomingEvents = computed((): UpcomingEvent[] => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const todayStr = today.toISOString().slice(0, 10)
  const upcoming: UpcomingEvent[] = []

  // All future one-time events
  for (const ev of allEvents.value) {
    if (!isEventRelevant(ev.id)) continue
    if (ev.eventType === EventTypes.ONE_TIME && ev.startTime) {
      const eventDateStr = new Date(ev.startTime).toISOString().slice(0, 10)
      if (eventDateStr >= todayStr) {
        const d = new Date(ev.startTime)
        const dow = d.getUTCDay() === 0 ? 7 : d.getUTCDay()
        upcoming.push({event: ev, date: eventDateStr, dayLabel: dayNames[dow]})
      }
    }
  }

  // Recurring events for the next 4 weeks (including today)
  const maxDays = 28
  for (let offset = 0; offset <= maxDays; offset++) {
    const date = new Date(today)
    date.setDate(date.getDate() + offset)
    const dateStr = date.toISOString().slice(0, 10)
    const dow = date.getDay() === 0 ? 7 : date.getDay()
    const dayOfMonth = date.getDate()
    const month = date.getMonth()
    const inBreak = breaks.value.some(b => b.startDate && b.endDate && dateStr >= b.startDate && dateStr <= b.endDate)
    if (inBreak) continue

    for (const ev of allEvents.value) {
      if (!isRecurringEvent(ev.eventType)) continue
      if (!isEventRelevant(ev.id)) continue
      if (!ev.dayOfWeek || ev.dayOfWeek !== dow) continue

      if (ev.eventType === EventTypes.RECURRING) {
        upcoming.push({event: ev, date: dateStr, dayLabel: dayNames[dow]})
      } else if (ev.eventType === EventTypes.MONTHLY_FIRST) {
        if (dayOfMonth <= 7) {
          upcoming.push({event: ev, date: dateStr, dayLabel: dayNames[dow]})
        }
      } else if (ev.eventType === EventTypes.QUARTERLY) {
        if (dayOfMonth <= 7 && (month % 3 === 0)) {
          upcoming.push({event: ev, date: dateStr, dayLabel: dayNames[dow]})
        }
      } else if (ev.eventType === EventTypes.YEARLY) {
        // Yearly events use startTime as the reference date
        if (ev.startTime) {
          const refDate = new Date(ev.startTime)
          if (refDate.getUTCMonth() === month && refDate.getUTCDate() === dayOfMonth) {
            upcoming.push({event: ev, date: dateStr, dayLabel: dayNames[dow]})
          }
        }
      }
    }
  }

  // Sort by date
  upcoming.sort((a, b) => a.date.localeCompare(b.date))
  return upcoming
})

function getRegistration(eventId: number, date: string, memberId?: number): EventRegistrationEntry | undefined {
  const mid = memberId ?? currentMemberId.value
  return myRegistrations.value.find(r => r.eventId === eventId && r.eventDate === date && r.memberId === mid)
}

function getEligibleMembers(eventId: number): { id: number; name: string }[] {
  const eligible = eligibleMembers.value[eventId]
  // No restriction = self + managed
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
}

function getRegistrationSummary(eventId: number, date: string): {
  accepted: number;
  pending: number;
  declined: number
} {
  const counts = registrationCounts.value.filter(c => c.eventId === eventId && c.eventDate === date)
  return {
    accepted: counts.find(c => c.status === RegistrationStatus.ACCEPTED)?.count ?? 0,
    pending: counts.find(c => c.status === RegistrationStatus.PENDING)?.count ?? 0,
    declined: counts.find(c => c.status === RegistrationStatus.DECLINED)?.count ?? 0,
  }
}

/**
 * Members who have no registration at all (can register or decline).
 */
function getMembersWithoutRegistration(eventId: number, date: string): { id: number; name: string }[] {
  return getEligibleMembers(eventId).filter(m => !getRegistration(eventId, date, m.id))
}

function getSelectedMemberId(eventId: number, date: string): number | null {
  const members = getMembersWithoutRegistration(eventId, date)
  if (members.length === 1) return members[0].id
  const key = `${eventId}-${date}`
  const selected = selectedMemberForEvent.value.get(key)
  return selected ? Number(selected) : null
}

function formatDeadline(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [ev, today, br, regs, elig, counts] = await Promise.all([
      events.listEvents(),
      events.listTodayEvents(),
      events.listBreaks(),
      events.listMyRegistrations(),
      events.listEligibleMembers(),
      events.listRegistrationCounts(),
    ])
    allEvents.value = ev
    todayEvents.value = today
    breaks.value = br
    myRegistrations.value = regs
    eligibleMembers.value = elig
    registrationCounts.value = counts

    if (isGuardian()) {
      const managed = await managedMembersApi.listManaged()
      managedMembers.value = managed.map(m => ({
        id: m.id,
        stationId: m.stationId,
        accountId: m.accountId,
        name: m.name,
        email: m.email
      }))
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function registerForEvent(ev: StationEvent, date: string, memberId: number) {
  const key = `${ev.id}-${date}-${memberId}`
  registering.value = key
  error.value = ''
  try {
    await events.registerForEvent(ev.id, {
      eventDate: date,
      memberId: memberId !== currentMemberId.value ? memberId : undefined,
    })
    await reloadRegistrations()
  } catch {
    error.value = t('common.error')
  } finally {
    registering.value = null
  }
}

function handleRegister(ev: StationEvent, date: string) {
  const mid = getSelectedMemberId(ev.id, date)
  if (mid != null) registerForEvent(ev, date, mid)
}

function handleDecline(ev: StationEvent, date: string) {
  const mid = getSelectedMemberId(ev.id, date)
  if (mid != null) declineEvent(ev, date, mid)
}

function setSelectedMember(eventId: number, date: string, value: string) {
  const key = `${eventId}-${date}`
  selectedMemberForEvent.value = new Map([...selectedMemberForEvent.value, [key, value]])
}

async function reloadRegistrations() {
  const [regs, counts] = await Promise.all([
    events.listMyRegistrations(),
    events.listRegistrationCounts(),
  ])
  myRegistrations.value = regs
  registrationCounts.value = counts
}

async function withdrawRegistration(regId: number) {
  error.value = ''
  try {
    await events.withdrawRegistration(regId)
    await reloadRegistrations()
  } catch {
    error.value = t('common.error')
  }
}

async function declineEvent(ev: StationEvent, date: string, memberId: number) {
  error.value = ''
  try {
    await events.declineEvent(ev.id, {
      eventDate: date,
      memberId: memberId !== currentMemberId.value ? memberId : undefined,
    })
    await reloadRegistrations()
  } catch {
    error.value = t('common.error')
  }
}

function getMemberName(memberId: number): string {
  if (memberId === currentMemberId.value) return t('eventsUpcoming.myself')
  const m = managedMembers.value.find(mm => mm.id === memberId)
  return m?.name ?? m?.email ?? `#${memberId}`
}

function goToAttendance(ev: StationEvent) {
  if (ev.templateId) {
    router.push({name: 'attendance-new', query: {templateId: String(ev.templateId), eventId: String(ev.id)}})
  }
}

onMounted(() => {
  if (loaded.value) {
    loadData()
  }
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) {
    loadData()
  }
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <!-- Today -->
        <div v-if="filteredTodayEvents.length > 0" class="space-y-3">
          <SectionHeader>{{ t('eventsUpcoming.today') }}</SectionHeader>
          <div class="grid gap-3 sm:grid-cols-2">
            <PrimaryContainer v-for="ev in filteredTodayEvents" :key="ev.id" class="space-y-2">
              <div class="flex items-center justify-between">
                <router-link :to="{ name: 'event-detail', params: { id: ev.id } }" class="font-semibold text-primary hover:underline">{{ ev.name }}</router-link>
                <font-awesome-icon v-if="ev.restricted" :icon="['fas', 'lock']" class="ml-1 h-3 w-3 text-[var(--text-muted)]"/>
                <span class="text-sm">{{ formatTime(ev.startTime) }} – {{ formatTime(ev.endTime) }}</span>
              </div>
              <p v-if="ev.description" class="text-sm text-(--text-muted)">{{ ev.description }}</p>
              <div class="flex gap-2">
                <PrimaryButton v-if="ev.templateId && canManageAttendance()"
                               @click="goToAttendance(ev)">
                  <font-awesome-icon :icon="['fas', 'clipboard-user']" class="mr-1"/>
                  {{ t('eventsUpcoming.attendance') }}
                </PrimaryButton>
              </div>
            </PrimaryContainer>
          </div>
        </div>

        <div v-if="filteredTodayEvents.length === 0" class="text-center text-(--text-muted) py-4">
          {{ t('eventsUpcoming.noToday') }}
        </div>

        <!-- Upcoming -->
        <div v-if="upcomingEvents.length > 0" class="space-y-3">
          <SectionHeader>{{ t('eventsUpcoming.upcoming') }}</SectionHeader>
          <div class="space-y-2">
            <NeutralContainer v-for="item in upcomingEvents" :key="`${item.event.id}-${item.date}`" class="space-y-2">
              <div class="flex items-center justify-between flex-wrap gap-2">
                <div>
                  <router-link :to="{ name: 'event-detail', params: { id: item.event.id } }" class="font-medium text-primary hover:underline">{{ item.event.name }}</router-link>
                  <font-awesome-icon v-if="item.event.restricted" :icon="['fas', 'lock']" class="ml-1 h-3 w-3 text-[var(--text-muted)]"/>
                  <span class="ml-2 text-sm text-(--text-muted)">{{ item.dayLabel }}, {{ item.date }}</span>
                  <span class="ml-2 text-xs text-(--text-muted)">{{
                      formatTime(item.event.startTime)
                    }} – {{ formatTime(item.event.endTime) }}</span>
                  <span v-if="item.event.requiresRegistration && item.event.registrationDeadline"
                        class="ml-2 text-xs text-(--text-muted)">
                    ({{ t('eventsUpcoming.deadline') }}: {{ formatDeadline(item.event.registrationDeadline) }})
                  </span>
                  <p v-if="item.event.description" class="text-sm text-(--text-muted) mt-0.5">{{ item.event.description }}</p>
                </div>
                <!-- Registration counts -->
                <div
                    v-if="getRegistrationSummary(item.event.id, item.date).accepted > 0 || getRegistrationSummary(item.event.id, item.date).pending > 0 || getRegistrationSummary(item.event.id, item.date).declined > 0"
                    class="flex items-center gap-2 text-xs">
                  <SuccessBadge v-if="getRegistrationSummary(item.event.id, item.date).accepted > 0">
                    {{ getRegistrationSummary(item.event.id, item.date).accepted }} {{ t('eventsUpcoming.accepted') }}
                  </SuccessBadge>
                  <InfoBadge v-if="getRegistrationSummary(item.event.id, item.date).pending > 0">
                    {{ getRegistrationSummary(item.event.id, item.date).pending }} {{
                      t('eventsUpcoming.pendingCount')
                    }}
                  </InfoBadge>
                  <ErrorBadge v-if="getRegistrationSummary(item.event.id, item.date).declined > 0">
                    {{ getRegistrationSummary(item.event.id, item.date).declined }} {{
                      t('eventsUpcoming.declinedCount')
                    }}
                  </ErrorBadge>
                </div>
              </div>
              <div class="flex items-center gap-2 flex-wrap">
                <!-- Existing registrations (all types) with withdraw -->
                <template v-for="m in getEligibleMembers(item.event.id)" :key="`reg-${m.id}`">
                  <template v-if="getRegistration(item.event.id, item.date, m.id)">
                    <div class="flex items-center gap-1">
                      <span v-if="managedMembers.length > 0" class="text-xs text-(--text-muted)">{{ m.name }}:</span>
                      <SecondaryButton
                          :title="t('eventsUpcoming.withdraw')"
                          class="!p-0 !bg-transparent !border-0"
                          @click="withdrawRegistration(getRegistration(item.event.id, item.date, m.id)!.id)"
                      >
                        <SuccessBadge v-if="getRegistration(item.event.id, item.date, m.id)!.status === RegistrationStatus.ACCEPTED">
                          {{ t('eventsUpcoming.statusAccepted') }}
                          <font-awesome-icon :icon="['fas', 'xmark']" class="ml-1 h-3 w-3"/>
                        </SuccessBadge>
                        <InfoBadge v-else-if="getRegistration(item.event.id, item.date, m.id)!.status === RegistrationStatus.PENDING">
                          {{ t('eventsUpcoming.statusPending') }}
                          <font-awesome-icon :icon="['fas', 'xmark']" class="ml-1 h-3 w-3"/>
                        </InfoBadge>
                        <ErrorBadge v-else-if="getRegistration(item.event.id, item.date, m.id)!.status === RegistrationStatus.DENIED">
                          {{ t('eventsUpcoming.statusDenied') }}
                          <font-awesome-icon :icon="['fas', 'xmark']" class="ml-1 h-3 w-3"/>
                        </ErrorBadge>
                        <ErrorBadge v-else-if="getRegistration(item.event.id, item.date, m.id)!.status === RegistrationStatus.DECLINED">
                          {{ t('eventsUpcoming.statusDeclined') }}
                          <font-awesome-icon :icon="['fas', 'xmark']" class="ml-1 h-3 w-3"/>
                        </ErrorBadge>
                      </SecondaryButton>
                      <span v-if="getRegistration(item.event.id, item.date, m.id)?.createdByName" class="text-xs text-(--text-muted) italic">
                        {{ t('common.createdBy', { name: getRegistration(item.event.id, item.date, m.id)!.createdByName }) }}
                      </span>
                    </div>
                  </template>
                </template>

                <!-- Action buttons: member selector + register + decline -->
                <template v-if="getMembersWithoutRegistration(item.event.id, item.date).length > 0">
                  <!-- Member dropdown (only when multiple actionable members) -->
                  <SelectInput
                      v-if="getMembersWithoutRegistration(item.event.id, item.date).length > 1"
                      :model-value="selectedMemberForEvent.get(`${item.event.id}-${item.date}`) ?? ''"
                      class="text-sm w-40"
                      @update:model-value="setSelectedMember(item.event.id, item.date, $event ?? '')"
                  >
                    <option disabled value="">{{ t('eventsUpcoming.selectMember') }}</option>
                    <option
                        v-for="m in getMembersWithoutRegistration(item.event.id, item.date)"
                        :key="m.id"
                        :value="String(m.id)"
                    >
                      {{ m.name }}
                    </option>
                  </SelectInput>

                  <!-- Register button (only for events requiring registration) -->
                  <PrimaryButton
                      v-if="item.event.requiresRegistration"
                      :disabled="registering != null || getSelectedMemberId(item.event.id, item.date) == null"
                      class="text-sm"
                      @click="handleRegister(item.event, item.date)"
                  >
                    <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
                    {{
                      getSelectedMemberId(item.event.id, item.date) != null && getSelectedMemberId(item.event.id, item.date) !== currentMemberId
                          ? t('eventsUpcoming.registerFor', {name: getMemberName(getSelectedMemberId(item.event.id, item.date)!)})
                          : t('eventsUpcoming.register')
                    }}
                  </PrimaryButton>

                  <!-- Decline button (always available) -->
                  <ErrorButton
                      :disabled="getSelectedMemberId(item.event.id, item.date) == null"
                      class="text-sm"
                      @click="handleDecline(item.event, item.date)"
                  >
                    <font-awesome-icon :icon="['fas', 'ban']" class="mr-1"/>
                    {{
                      getSelectedMemberId(item.event.id, item.date) != null && getSelectedMemberId(item.event.id, item.date) !== currentMemberId
                          ? t('eventsUpcoming.declineFor', {name: getMemberName(getSelectedMemberId(item.event.id, item.date)!)})
                          : t('eventsUpcoming.decline')
                    }}
                  </ErrorButton>
                </template>
              </div>
            </NeutralContainer>
          </div>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
