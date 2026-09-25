/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PublicEventList from './publicstationcalendarview/PublicEventList.vue'
import {getIcalFeedUrl, getIcalSubscribeUrl, type PublicEvent} from '@/api/publicEvents'
import {apiUrl} from '@/util/apiUrl'
import {socialMeta, stationLogoImage, titleWithStation, useAbsoluteUrl} from '@/util/socialMeta'
import {usePublicStationAddress} from '@/composables/usePublicStationAddress'

const {t} = useI18n()

const {station, stationUid} = usePublicStationAddress()
const headTitle = computed(() => titleWithStation(t('publicStation.calendar'), station.value?.name))
const absoluteUrl = useAbsoluteUrl()

/**
 * Resolved here rather than inside the loader: the address comes from the runtime configuration,
 * and reading that needs the Nuxt instance, which a loader running on its own no longer has.
 */
const apiBase = apiUrl('')

/**
 * Fetched while the server renders rather than after mounting.
 *
 * <p>The appointments are what this page is. Fetched once the browser had taken over, what the
 * server sent was a calendar with no dates in it, and the structured list of appointments beside it,
 * which is the one thing a search engine can show a date from, was written after nobody was reading.
 */
const {data: allEvents, status} = await useAsyncData(
    () => `public-events-${stationUid.value}`,
    () => $fetch<PublicEvent[]>(`${apiBase}/public/events/${stationUid.value}`),
    {default: (): PublicEvent[] => [], watch: [stationUid]},
)

const loading = computed(() => status.value === 'pending')

const events = computed(() => {
  const now = new Date()
  return allEvents.value.filter(e => {
    if (e.eventType && e.eventType !== 'ONE_TIME') return true
    if (!e.endTime) return !e.startTime || new Date(e.startTime) >= now
    return new Date(e.endTime) >= now
  })
})

useHead(computed(() => {
  const info = station.value
  const oneTimeEvents = events.value.filter(e => e.startTime && (!e.eventType || e.eventType === 'ONE_TIME'))
  return {
    title: headTitle.value,
    meta: info
        ? socialMeta({
          title: headTitle.value,
          description: t('publicStation.meta.calendar', {station: info.name}),
          imageUrl: absoluteUrl(stationLogoImage(info)),
        })
        : [],
    script: oneTimeEvents.length === 0 ? [] : [
      {
        type: 'application/ld+json',
        innerHTML: JSON.stringify(oneTimeEvents.slice(0, 20).map(e => ({
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
