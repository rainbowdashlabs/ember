/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {CrossStationDashboard, CrossStationNotification} from '@/api/session'
import {getCrossStationDashboard} from '@/api/session'
import {useStations} from '@/composables/useStations'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
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

function selectStation(stationId: string) {
  setActiveStation(stationId)
  window.location.href = '/station/dashboard/overview'
}

function formatTime(iso: string): string {
  const date = new Date(iso)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return 'gerade eben'
  if (diffMin < 60) return `vor ${diffMin} Min.`
  const diffH = Math.floor(diffMin / 60)
  if (diffH < 24) return `vor ${diffH} Std.`
  const diffD = Math.floor(diffH / 24)
  return `vor ${diffD} Tag${diffD > 1 ? 'en' : ''}`
}
</script>

<template>
  <div class="max-w-5xl mx-auto px-4 py-8 space-y-8">
    <div class="text-center">
      <font-awesome-icon :icon="['fas', 'building']" class="text-4xl text-primary mb-3"/>
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
              <span class="text-xs text-(--text-muted)">{{ formatTime(n.createdAt) }}</span>
            </div>
          </NeutralContainer>
        </div>
      </div>
    </template>
  </div>
</template>
