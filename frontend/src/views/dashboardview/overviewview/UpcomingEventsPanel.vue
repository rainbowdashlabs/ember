/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import RegistrationStatusBadge from '@/views/stationview/events/eventshared/RegistrationStatusBadge.vue'
import EventAnswerDialog from '@/views/stationview/events/eventshared/EventAnswerDialog.vue'
import {
  EventTypes,
  isRecurringEvent,
  type EventBreak,
  type EventRegistrationEntry,
  type StationEvent,
} from '@/api/events'
import type {StationMember} from '@/api/types'
import {events, managedMembers as managedMembersApi} from '@/api'
import {getFeedStatus, type FeedStatusResponse} from '@/api/feedToken'
import {useSession} from '@/composables/useSession'
import {formatDate, formatTime} from '@/util/format'
import {answerableMembers} from '@/util/eventAnswers'

const {t} = useI18n()
const router = useRouter()
const {sessionInfo, isGuardian} = useSession()

const allEvents = ref<StationEvent[]>([])
const eventBreaks = ref<EventBreak[]>([])
const eligibleMembers = ref<Record<number, number[]>>({})
const feedStatus = ref<FeedStatusResponse | null>(null)
const myRegistrations = ref<EventRegistrationEntry[]>([])
const managed = ref<StationMember[]>([])
const declining = ref<UpcomingEvent | null>(null)
const decliningBusy = ref(false)
const declineError = ref('')

const showFeedCta = computed(() => {
  if (!feedStatus.value) return false
  return !feedStatus.value.hasToken || !feedStatus.value.icalActive
})

const feedCtaMessage = computed(() => {
  if (!feedStatus.value) return ''
  if (!feedStatus.value.hasToken) return t('dashboard.feedIcalSetupHint')
  return t('dashboard.feedIcalInactiveHint')
})

const dayNames = ['', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag']

/** Weekday label for an ISO day-of-week (1 = Monday … 7 = Sunday). */
function dayName(dayOfWeek: number): string {
  return dayNames[dayOfWeek] ?? ''
}

interface UpcomingEvent {
  event: StationEvent
  date: string
  dayLabel: string
}

const upcomingEvents = computed((): UpcomingEvent[] => {
  const now = new Date()
  const todayStr = now.toISOString().slice(0, 10)
  const upcoming: UpcomingEvent[] = []
  const myId = sessionInfo.value?.member?.id ?? 0

  function isRelevant(eventId: number): boolean {
    const eligible = eligibleMembers.value[eventId]
    if (eligible === undefined) return true
    return eligible.includes(myId)
  }

  for (const ev of allEvents.value) {
    if (!isRelevant(ev.id)) continue
    if (ev.eventType === EventTypes.ONE_TIME && ev.startTime) {
      const eventDateStr = new Date(ev.startTime).toISOString().slice(0, 10)
      if (eventDateStr >= todayStr) {
        const d = new Date(ev.startTime)
        const dow = d.getUTCDay() === 0 ? 7 : d.getUTCDay()
        upcoming.push({event: ev, date: eventDateStr, dayLabel: dayName(dow)})
      }
    }
  }

  for (let offset = 0; offset <= 14; offset++) {
    const date = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + offset))
    const dateStr = date.toISOString().slice(0, 10)
    const dow = date.getUTCDay() === 0 ? 7 : date.getUTCDay()
    const dayOfMonth = date.getUTCDate()
    const month = date.getUTCMonth()
    const inBreak = eventBreaks.value.some(b => b.startDate && b.endDate && dateStr >= b.startDate && dateStr <= b.endDate)
    if (inBreak) continue

    for (const ev of allEvents.value) {
      if (!isRecurringEvent(ev.eventType)) continue
      if (!isRelevant(ev.id)) continue
      if (!ev.dayOfWeek || ev.dayOfWeek !== dow) continue

      if (ev.eventType === EventTypes.RECURRING) {
        upcoming.push({event: ev, date: dateStr, dayLabel: dayName(dow)})
      } else if (ev.eventType === EventTypes.MONTHLY_FIRST) {
        if (dayOfMonth <= 7) upcoming.push({event: ev, date: dateStr, dayLabel: dayName(dow)})
      } else if (ev.eventType === EventTypes.QUARTERLY) {
        if (dayOfMonth <= 7 && (month % 3 === 0)) upcoming.push({event: ev, date: dateStr, dayLabel: dayName(dow)})
      } else if (ev.eventType === EventTypes.YEARLY && ev.startTime) {
        const refDate = new Date(ev.startTime)
        if (refDate.getUTCMonth() === month && refDate.getUTCDate() === dayOfMonth) {
          upcoming.push({event: ev, date: dateStr, dayLabel: dayName(dow)})
        }
      }
    }
  }

  upcoming.sort((a, b) => a.date.localeCompare(b.date))
  return upcoming.slice(0, 10)
})

async function loadData() {
  try {
    const [ev, br, elig, fs, regs, mine] = await Promise.all([
      events.listEvents(),
      events.listBreaks().catch(() => []),
      events.listEligibleMembers().catch(() => ({})),
      getFeedStatus().catch(() => null),
      events.listMyRegistrations().catch(() => []),
      isGuardian() ? managedMembersApi.listManaged().catch(() => []) : Promise.resolve([]),
    ])
    allEvents.value = ev
    eventBreaks.value = br
    eligibleMembers.value = elig
    feedStatus.value = fs
    myRegistrations.value = regs
    managed.value = mine
  } catch { /* ignore */ }
}

/** The dialog is open exactly while an appointment is waiting to be refused for somebody. */
const declineDialogOpen = computed({
  get: () => declining.value !== null,
  set: (open: boolean) => {
    if (!open) declining.value = null
  },
})

/** Who the open dialog offers, empty while none is open: the dialog stays mounted and reads this. */
const decliningPeople = computed(() => (declining.value
    ? withoutAnswer(declining.value).map(member => ({memberId: member.id, name: member.name}))
    : []))

/** Everybody this member answers for on that appointment, themselves first. */
function answerableFor(item: UpcomingEvent) {
  return answerableMembers(
      item.event.id,
      eligibleMembers.value,
      sessionInfo.value?.member?.id ?? 0,
      managed.value,
      t('eventsUpcoming.myself'))
}

/**
 * What has been answered for this date, one entry per person who answered.
 *
 * <p>An appointment repeats, so an answer belongs to a date rather than to the appointment: last
 * week's refusal says nothing about next week.
 */
function answersOn(item: UpcomingEvent) {
  const answerable = answerableFor(item)
  return myRegistrations.value
      .filter(reg => reg.eventId === item.event.id && reg.eventDate === item.date)
      .map(reg => ({
        registration: reg,
        name: answerable.find(member => member.id === reg.memberId)?.name ?? reg.memberName,
      }))
}

/** Who has not said anything yet, which is who a refusal can still be given for. */
function withoutAnswer(item: UpcomingEvent) {
  const answered = new Set(answersOn(item).map(entry => entry.registration.memberId))
  return answerableFor(item).filter(member => !answered.has(member.id))
}

/**
 * Refuses the appointment for one person, or asks which of them where there is more than one.
 *
 * <p>A guardian answers for a household, and the household rarely refuses as a whole: one child is
 * ill while the other goes. Which is why the choice is asked rather than assumed.
 */
function decline(item: UpcomingEvent) {
  const open = withoutAnswer(item)
  if (open.length === 1) {
    void sendDecline(item, [open[0]!.id])
    return
  }
  declineError.value = ''
  declining.value = item
}

async function sendDecline(item: UpcomingEvent, memberIds: number[]) {
  decliningBusy.value = true
  declineError.value = ''
  try {
    const me = sessionInfo.value?.member?.id ?? 0
    for (const memberId of memberIds) {
      await events.declineEvent(item.event.id, {
        eventDate: item.date,
        memberId: memberId === me ? undefined : memberId,
      })
    }
    myRegistrations.value = await events.listMyRegistrations()
    declining.value = null
  } catch {
    declineError.value = t('common.error')
  } finally {
    decliningBusy.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <NeutralContainer v-if="upcomingEvents.length > 0 || showFeedCta" class="flex flex-col max-h-[66vh]">
    <div class="flex items-center justify-between mb-3 shrink-0">
      <SectionHeader>
        <font-awesome-icon :icon="['fas', 'calendar-plus']" class="mr-2"/>
        {{ t('dashboard.upcomingEvents') }}
      </SectionHeader>
      <SecondaryButton @click="router.push({ name: 'events-upcoming' })">
        {{ t('dashboard.showAll') }}
      </SecondaryButton>
    </div>
    <div class="overflow-y-auto flex-1 space-y-2">
      <InfoContainer v-if="showFeedCta" class="flex items-center justify-between gap-3 py-2 px-3">
        <div class="flex items-center gap-2">
          <font-awesome-icon :icon="['fas', 'calendar-days']" class="text-info shrink-0"/>
          <p class="text-xs">{{ feedCtaMessage }}</p>
        </div>
        <SecondaryButton class="shrink-0 text-xs" compact @click="router.push({ name: 'profile-notifications' })">
          {{ t('dashboard.feedSetup') }}
        </SecondaryButton>
      </InfoContainer>
      <NeutralContainer v-for="item in upcomingEvents" :key="`${item.event.id}-${item.date}`"
                        class="py-2 px-3 cursor-pointer hover:bg-(--bg-accent)"
                        @click="router.push(isRecurringEvent(item.event.eventType)
                          ? { name: 'event-detail-date', params: { id: item.event.id, date: item.date } }
                          : { name: 'event-detail', params: { id: item.event.id } })">
        <div class="flex items-start justify-between gap-2">
          <div class="min-w-0">
            <p class="truncate text-sm font-medium">{{ item.event.name }}</p>
            <p class="text-xs text-(--text-muted)">
              {{ item.dayLabel }}, {{ formatDate(item.date + 'T00:00:00') }}
              <template v-if="item.event.startTime"> · {{ formatTime(item.event.startTime) }}</template>
              <template v-if="item.event.endTime"> – {{ formatTime(item.event.endTime) }}</template>
            </p>
          </div>

          <InfoBadge v-if="item.event.requiresRegistration && answersOn(item).length === 0" class="shrink-0">
            {{ t('dashboard.registrationRequired') }}
          </InfoBadge>
          <SecondaryButton
              v-else-if="!item.event.requiresRegistration && withoutAnswer(item).length > 0"
              :disabled="decliningBusy"
              :data-testid="`dashboard-decline-${item.event.id}`"
              class="shrink-0"
              compact
              @click.stop="decline(item)"
          >
            {{ t('eventsUpcoming.decline') }}
          </SecondaryButton>
        </div>

        <div v-if="answersOn(item).length > 0" class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1">
          <span v-for="answer in answersOn(item)" :key="answer.registration.id" class="flex items-center gap-1">
            <span v-if="managed.length > 0" class="text-xs text-(--text-muted)">{{ answer.name }}</span>
            <RegistrationStatusBadge :status="answer.registration.status"/>
          </span>
        </div>
      </NeutralContainer>
    </div>

    <EventAnswerDialog
        v-model="declineDialogOpen"
        :people="decliningPeople"
        :fields="[]"
        :attending="false"
        :busy="decliningBusy"
        :error="declineError"
        @confirm="answers => declining && sendDecline(declining, answers.map(a => a.memberId))"
    />
  </NeutralContainer>
</template>
