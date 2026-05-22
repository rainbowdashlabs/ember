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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type {EventBreak, StationEvent} from '@/api/types'
import {EventTypes, isRecurringEvent} from '@/api/types'
import {events} from '@/api'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const router = useRouter()
const {sessionInfo} = useSession()

const allEvents = ref<StationEvent[]>([])
const eventBreaks = ref<EventBreak[]>([])
const eligibleMembers = ref<Record<number, number[]>>({})

const dayNames = ['', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag']

interface UpcomingEvent {
  event: StationEvent
  date: string
  dayLabel: string
}

const upcomingEvents = computed((): UpcomingEvent[] => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const todayStr = today.toISOString().slice(0, 10)
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
    const date = new Date(today)
    date.setDate(date.getDate() + offset)
    const dateStr = date.toISOString().slice(0, 10)
    const dow = date.getDay() === 0 ? 7 : date.getDay()
    const dayOfMonth = date.getDate()
    const month = date.getMonth()
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

function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadData() {
  try {
    const [ev, br, elig] = await Promise.all([
      events.listEvents(),
      events.listBreaks().catch(() => []),
      events.listEligibleMembers().catch(() => ({})),
    ])
    allEvents.value = ev
    eventBreaks.value = br
    eligibleMembers.value = elig
  } catch { /* ignore */ }
}

onMounted(loadData)
</script>

<template>
  <NeutralContainer v-if="upcomingEvents.length > 0" class="flex flex-col max-h-[66vh]">
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
      <NeutralContainer v-for="item in upcomingEvents" :key="`${item.event.id}-${item.date}`"
                        class="py-2 px-3 cursor-pointer hover:bg-(--bg-accent)"
                        @click="router.push({ name: 'events-upcoming' })">
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
