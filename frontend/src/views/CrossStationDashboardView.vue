/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import {getCrossStationDashboard, type CrossStationDashboard, type CrossStationNotification} from '@/api/session'
import {useStations} from '@/composables/useStations'
import {formatRelative} from '@/util/format'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
const route = useRoute()
const {setActiveStation, load: loadStations, getStationLogoUrl} = useStations()

const dashboard = ref<CrossStationDashboard | null>(null)
const loading = ref(true)

onMounted(async () => {
  await loadStations()
  try {
    dashboard.value = await getCrossStationDashboard()
  } catch { /* ignore */ }
  loading.value = false
})

/** A path on this instance, and not this page again — a target pointing back here loops the picker. */
function usableRedirect(target: string | null): boolean {
  return !!target && target.startsWith('/') && !target.startsWith('//') && !target.startsWith('/cross-station')
}

function resolveRedirect(): string | null {
  // Prefer the route query (Vue Router state) but fall back to the raw window URL
  // — during SSR hydration the route can briefly lag behind the browser bar, and
  // we never want to drop a deep link silently.
  const fromRoute = typeof route.query.redirect === 'string' ? route.query.redirect : null
  if (usableRedirect(fromRoute)) return fromRoute
  if (typeof window !== 'undefined') {
    const fromUrl = new URLSearchParams(window.location.search).get('redirect')
    if (usableRedirect(fromUrl)) return fromUrl
  }
  return null
}

function selectStation(stationId: string) {
  setActiveStation(stationId)
  const redirect = resolveRedirect()
  window.location.href = redirect ?? '/station/dashboard/overview'
}

</script>

<template>
  <div class="max-w-5xl mx-auto px-4 py-8 space-y-8">
    <div class="text-center">
      <PageHeroIcon :icon="['fas', 'building']"/>
      <PageHeader>{{ t('crossStation.title') }}</PageHeader>
      <MutedText tag="p" size="sm" class="mt-1">{{ t('crossStation.subtitle') }}</MutedText>
    </div>

    <Spinner v-if="loading" size="lg"/>

    <template v-if="!loading && dashboard">
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <NeutralContainer
            v-for="station in dashboard.stations"
            :key="station.stationId"
            class="cursor-pointer hover:border-primary transition-colors space-y-3"
            @click="selectStation(station.stationId)"
        >
          <div class="flex items-center gap-3">
            <img v-if="getStationLogoUrl(station.stationId)" :src="getStationLogoUrl(station.stationId)!" alt=""
                 class="h-10 w-10 rounded object-contain"/>
            <font-awesome-icon v-else :icon="['fas', 'building']" class="h-6 w-6 text-(--text-muted)"/>
            <span class="font-semibold text-lg">{{ station.stationName }}</span>
          </div>
          <div class="flex gap-3">
            <div v-if="station.notifications > 0" class="flex items-center gap-1.5">
              <PrimaryBadge>{{ station.notifications }}</PrimaryBadge>
              <span class="text-sm text-(--text-muted)">{{ t('crossStation.notifications') }}</span>
            </div>
            <div v-if="station.requirements > 0" class="flex items-center gap-1.5">
              <ErrorBadge>{{ station.requirements }}</ErrorBadge>
              <span class="text-sm text-(--text-muted)">{{ t('crossStation.requirements') }}</span>
            </div>
            <span v-if="station.notifications === 0 && station.requirements === 0" class="text-sm text-(--text-muted)">
              <font-awesome-icon :icon="['fas', 'check']" class="text-green-500 mr-1"/>
              {{ t('crossStation.noNotifications') }}
            </span>
          </div>
        </NeutralContainer>
      </div>

      <div v-if="dashboard.recentNotifications.length > 0" class="space-y-3">
        <SectionHeader>{{ t('crossStation.recentNotifications') }}</SectionHeader>
        <div class="space-y-2">
          <NeutralContainer
              v-for="n in dashboard.recentNotifications"
              :key="n.id"
              class="flex items-start gap-3 cursor-pointer hover:border-primary transition-colors"
              @click="selectStation(n.stationId)"
          >
            <SecondaryBadge class="shrink-0 mt-0.5">{{ n.stationName }}</SecondaryBadge>
            <div class="flex-1 min-w-0">
              <p class="text-sm">{{ t(n.localeKey, n.params) }}</p>
              <span class="text-xs text-(--text-muted)">{{ formatRelative(n.createdAt) }}</span>
            </div>
          </NeutralContainer>
        </div>
      </div>
    </template>
  </div>
</template>
