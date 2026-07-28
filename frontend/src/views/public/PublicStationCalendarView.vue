/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, inject} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import type {Ref} from 'vue'
import type {PublicStationInfo} from '@/api/discovery'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PublicEventList from './publicstationcalendarview/PublicEventList.vue'
import type {PublicEvent} from '@/api/publicEvents'
import {getIcalFeedUrl, getIcalSubscribeUrl, listPublicEvents} from '@/api/publicEvents'

const {t} = useI18n()
const route = useRoute()

const publicStation = inject<Ref<PublicStationInfo | null>>('publicStation')
const stationUid = computed(() => publicStation?.value?.stationUid ?? route.params.stationUid as string)
const allEvents = ref<PublicEvent[]>([])
const loading = ref(true)

const events = computed(() => {
  const now = new Date()
  return allEvents.value.filter(e => {
    if (e.eventType && e.eventType !== 'ONE_TIME') return true
    if (!e.endTime) return !e.startTime || new Date(e.startTime) >= now
    return new Date(e.endTime) >= now
  })
})

useHead(computed(() => {
  const oneTimeEvents = events.value.filter(e => e.startTime && (!e.eventType || e.eventType === 'ONE_TIME'))
  if (oneTimeEvents.length === 0) return {}
  return {
    script: [
      {
        type: 'application/ld+json',
        children: JSON.stringify(oneTimeEvents.slice(0, 20).map(e => ({
          '@context': 'https://schema.org',
          '@type': 'Event',
          name: e.name,
          ...(e.startTime ? {startDate: e.startTime} : {}),
          ...(e.endTime ? {endDate: e.endTime} : {}),
          ...(e.description ? {description: e.description} : {}),
          eventAttendanceMode: 'https://schema.org/OfflineEventAttendanceMode',
        }))),
      },
    ],
  }
}))

onMounted(async () => {
  try {
    allEvents.value = await listPublicEvents(stationUid.value)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <ViewContent :title="t('pages.public-station-calendar.title')" :subtitle="t('pages.public-station-calendar.subtitle')">
    <div class="space-y-4">
      <div class="flex items-center justify-end flex-wrap gap-2">
        <a :href="getIcalSubscribeUrl(stationUid)" class="text-sm text-primary hover:underline">
          <font-awesome-icon :icon="['fas', 'calendar-plus']" class="mr-1"/>
          {{ t('publicStation.subscribeCal') }}
        </a>
      </div>

      <NeutralContainer class="text-xs text-(--text-muted) space-y-1">
        <p>{{ t('publicStation.icalHint') }}</p>
        <code class="block bg-(--bg-accent) rounded px-2 py-1 select-all break-all">{{ getIcalFeedUrl(stationUid) }}</code>
      </NeutralContainer>

      <Spinner v-if="loading"/>
      <PublicEventList v-else :events="events"/>
    </div>
  </ViewContent>
</template>
