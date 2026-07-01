/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import StationMap from '@/components/map/StationMap.vue'
import DiscoveryFilterBar from '@/views/stationview/discovery/indexview/DiscoveryFilterBar.vue'
import DiscoveryStationCard from '@/views/stationview/discovery/indexview/DiscoveryStationCard.vue'
import {discovery, stationManage} from '@/api'
import type {DiscoveredStation} from '@/api/discovery'
import type {MapStation} from '@/components/map/StationMap.vue'

const {t} = useI18n()

const tab = ref<'list' | 'map'>('list')

const query = ref('')
const radius = ref(100)
const nearMeOnly = ref(false)

const stations = ref<DiscoveredStation[]>([])

interface LatLon {
  lat: number
  lon: number
}

const localCoords = ref<LatLon | null>(null)

const stationMap = ref<InstanceType<typeof StationMap> | null>(null)

function distanceKm(a: LatLon, b: LatLon): number {
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

const {loading, error} = useAsyncLoader(async () => {
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
})

function focusStation(uid: string) {
  tab.value = 'map'
  setTimeout(() => stationMap.value?.focus(uid), 100)
}
</script>

<template>
  <ViewContent
      :title="t('pages.station-discovery-network.title')"
      :subtitle="t('pages.station-discovery-network.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <DiscoveryFilterBar
            v-model:query="query"
            v-model:radius="radius"
            v-model:near-me-only="nearMeOnly"
            v-model:tab="tab"
            :has-local-coords="!!localCoords"
        />

        <EmptyState v-if="filtered.length === 0">
          {{ t('stationDiscovery.empty') }}
        </EmptyState>

        <NeutralContainer v-else-if="tab === 'map'">
          <StationMap ref="stationMap" :stations="mapStations" height="520px"/>
        </NeutralContainer>

        <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-3">
          <DiscoveryStationCard
              v-for="station in filtered"
              :key="station.stationUid"
              :station="station"
              @focus="focusStation(station.stationUid)"
          />
        </div>
      </template>
    </div>
  </ViewContent>
</template>
