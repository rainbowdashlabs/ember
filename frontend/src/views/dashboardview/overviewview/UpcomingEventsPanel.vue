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
import type {EventBreak, StationEvent} from '@/api/types'
import {EventTypes, isRecurringEvent} from '@/api/types'
import {events} from '@/api'
import {getFeedStatus} from '@/api/feedToken'
import type {FeedStatusResponse} from '@/api/feedToken'
import {useSession} from '@/composables/useSession'
import {formatTime} from '@/util/format'

const {t} = useI18n()
const router = useRouter()
const {sessionInfo} = useSession()

const allEvents = ref<StationEvent[]>([])
const eventBreaks = ref<EventBreak[]>([])
const eligibleMembers = ref<Record<number, number[]>>({})
const feedStatus = ref<FeedStatusResponse | null>(null)

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
        upcoming.push({event: ev, date: eventDateStr, dayLabel: dayNames[dow]})
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
        upcoming.push({event: ev, date: dateStr, dayLabel: dayNames[dow]})
      } else if (ev.eventType === EventTypes.MONTHLY_FIRST) {
        if (dayOfMonth <= 7) upcoming.push({event: ev, date: dateStr, dayLabel: dayNames[dow]})
      } else if (ev.eventType === EventTypes.QUARTERLY) {
        if (dayOfMonth <= 7 && (month % 3 === 0)) upcoming.push({event: ev, date: dateStr, dayLabel: dayNames[dow]})
      } else if (ev.eventType === EventTypes.YEARLY && ev.startTime) {
        const refDate = new Date(ev.startTime)
        if (refDate.getUTCMonth() === month && refDate.getUTCDate() === dayOfMonth) {
          upcoming.push({event: ev, date: dateStr, dayLabel: dayNames[dow]})
        }
      }
    }
  }

  upcoming.sort((a, b) => a.date.localeCompare(b.date))
  return upcoming.slice(0, 10)
})

async function loadData() {
  try {
    const [ev, br, elig, fs] = await Promise.all([
      events.listEvents(),
      events.listBreaks().catch(() => []),
      events.listEligibleMembers().catch(() => ({})),
      getFeedStatus().catch(() => null),
    ])
    allEvents.value = ev
    eventBreaks.value = br
    eligibleMembers.value = elig
    feedStatus.value = fs
  } catch { /* ignore */ }
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
        <div class="flex items-center justify-between gap-2">
          <div>
            <p class="text-sm font-medium">{{ item.event.name }}</p>
            <p class="text-xs text-(--text-muted)">
              {{ item.dayLabel }}, {{ new Date(item.date + 'T00:00:00').toLocaleDateString('de-DE') }}
              <template v-if="item.event.startTime"> · {{ formatTime(item.event.startTime) }}</template>
              <template v-if="item.event.endTime"> – {{ formatTime(item.event.endTime) }}</template>
            </p>
          </div>
          <InfoBadge v-if="item.event.requiresRegistration">{{ t('dashboard.registrationRequired') }}</InfoBadge>
        </div>
      </NeutralContainer>
    </div>
  </NeutralContainer>
</template>
