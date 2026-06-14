/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import StationMap from '@/components/map/StationMap.vue'
import {discovery, stationManage} from '@/api'
import type {DiscoveredStation} from '@/api/discovery'
import type {MapStation} from '@/components/map/StationMap.vue'

const {t} = useI18n()

const loading = ref(true)
const error = ref('')
const tab = ref<'list' | 'map'>('list')

const query = ref('')
const radius = ref(100)
const nearMeOnly = ref(false)

const stations = ref<DiscoveredStation[]>([])
const localCoords = ref<{lat: number; lon: number} | null>(null)

const stationMap = ref<InstanceType<typeof StationMap> | null>(null)

function distanceKm(a: {lat: number; lon: number}, b: {lat: number; lon: number}): number {
  const R = 6371
  const dLat = (b.lat - a.lat) * Math.PI / 180
  const dLon = (b.lon - a.lon) * Math.PI / 180
  const x = Math.sin(dLat / 2) ** 2
      + Math.cos(a.lat * Math.PI / 180) * Math.cos(b.lat * Math.PI / 180) * Math.sin(dLon / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(x))
}

const enriched = computed(() =>
    stations.value.map((s) => {
      const dist = s.latitude != null && s.longitude != null && localCoords.value
          ? distanceKm(localCoords.value, {lat: s.latitude, lon: s.longitude})
          : null
      return {...s, distance: dist}
    }),
)

const filtered = computed(() => {
  const term = query.value.trim().toLowerCase()
  let list = enriched.value
  if (term) {
    list = list.filter((s) => {
      return (
          s.name.toLowerCase().includes(term)
          || (s.city ?? '').toLowerCase().includes(term)
          || (s.slogan ?? '').toLowerCase().includes(term)
          || s.tags.some((tag) => tag.toLowerCase().includes(term))
      )
    })
  }
  if (nearMeOnly.value && localCoords.value) {
    list = list.filter((s) => s.distance != null && s.distance <= radius.value)
  }
  return [...list].sort((a, b) => {
    if (a.distance == null && b.distance == null) return 0
    if (a.distance == null) return 1
    if (b.distance == null) return -1
    return a.distance - b.distance
  })
})

const mapStations = computed<MapStation[]>(() => filtered.value
    .filter((s) => typeof s.latitude === 'number' && typeof s.longitude === 'number')
    .map((s) => {
      const tint: MapStation['tint'] = s.distance == null
          ? null
          : s.distance < 50
              ? 'local'
              : s.distance < 200
                  ? 'near'
                  : 'far'
      const subtitle = s.distance != null
          ? t('lendingDistance.distanceKm', {distance: s.distance.toFixed(1)})
          : (s.city ?? null)
      return {
        uid: s.stationUid,
        name: s.name,
        latitude: s.latitude as number,
        longitude: s.longitude as number,
        subtitle,
        href: s.contactUrl ?? null,
        tint,
      }
    }),
)

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [list, location] = await Promise.all([
      discovery.listDiscoveredStations(),
      stationManage.getStationLocation().catch(() => null),
    ])
    stations.value = list
    if (location && location.latitude != null && location.longitude != null) {
      localCoords.value = {lat: location.latitude, lon: location.longitude}
    } else {
      localCoords.value = null
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function focusStation(uid: string) {
  tab.value = 'map'
  setTimeout(() => stationMap.value?.focus(uid), 100)
}

onMounted(loadAll)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div>
        <SectionHeader>{{ t('stationDiscovery.title') }}</SectionHeader>
        <p class="text-sm text-(--text-muted)">{{ t('stationDiscovery.subtitle') }}</p>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <NeutralContainer class="space-y-3">
          <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div class="md:col-span-2">
              <SearchInput v-model="query" :placeholder="t('stationDiscovery.searchPlaceholder')"/>
            </div>
            <div class="space-y-1">
              <FieldLabel>{{ t('stationDiscovery.radiusLabel') }}</FieldLabel>
              <NumberInput v-model="radius" :min="1" :max="2000" :disabled="!localCoords"/>
            </div>
          </div>

          <div class="flex items-center justify-between gap-3">
            <div class="flex items-center gap-2">
              <ToggleInput v-model="nearMeOnly" :disabled="!localCoords"/>
              <span class="text-sm">{{ t('stationDiscovery.nearMeOnly') }}</span>
            </div>
            <div class="flex gap-1">
              <SelectionToggleButton :selected="tab === 'list'" @toggle="tab = 'list'">
                <font-awesome-icon :icon="['fas', 'list']" class="mr-1"/>
                {{ t('stationDiscovery.listTab') }}
              </SelectionToggleButton>
              <SelectionToggleButton :selected="tab === 'map'" @toggle="tab = 'map'">
                <font-awesome-icon :icon="['fas', 'map-location-dot']" class="mr-1"/>
                {{ t('stationDiscovery.mapTab') }}
              </SelectionToggleButton>
            </div>
          </div>

          <Alert v-if="!localCoords" variant="info">
            {{ t('stationDiscovery.noCoordinatesForFilter') }}
          </Alert>
        </NeutralContainer>

        <EmptyState v-if="filtered.length === 0">
          {{ t('stationDiscovery.empty') }}
        </EmptyState>

        <NeutralContainer v-else-if="tab === 'map'">
          <StationMap ref="stationMap" :stations="mapStations" height="520px"/>
        </NeutralContainer>

        <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-3">
          <NeutralContainer
              v-for="station in filtered"
              :key="station.stationUid"
              class="space-y-2 cursor-pointer"
              @click="focusStation(station.stationUid)"
          >
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0 space-y-1">
                <p class="font-medium truncate">{{ station.name }}</p>
                <p v-if="station.slogan" class="text-xs text-(--text-muted) line-clamp-2">{{ station.slogan }}</p>
              </div>
              <div class="flex flex-col items-end gap-1 shrink-0">
                <PrimaryBadge v-if="station.distance != null">
                  {{ t('lendingDistance.distanceKm', {distance: station.distance.toFixed(1)}) }}
                </PrimaryBadge>
                <SecondaryBadge>{{ station.memberCount }}</SecondaryBadge>
              </div>
            </div>
            <p v-if="station.city || station.country" class="text-xs text-(--text-muted)">
              {{ [station.city, station.country].filter(Boolean).join(', ') }}
            </p>
          </NeutralContainer>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
