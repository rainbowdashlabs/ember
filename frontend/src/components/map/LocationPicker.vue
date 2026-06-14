/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useMapsConfig} from '@/composables/useMapsConfig'
import DecimalInput from '@/components/input/number/DecimalInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import 'leaflet/dist/leaflet.css'

const props = withDefaults(
    defineProps<{
      latitude: number | null
      longitude: number | null
      height?: string
      initialCenter?: [number, number]
      initialZoom?: number
    }>(),
    {
      height: '320px',
      initialCenter: () => [51.0, 10.0] as [number, number],
      initialZoom: 5,
    },
)

const emit = defineEmits<{
  (e: 'update:latitude', value: number | null): void
  (e: 'update:longitude', value: number | null): void
}>()

const {t} = useI18n()
const mapEl = ref<HTMLDivElement | null>(null)
const {load} = useMapsConfig()

let mapInstance: any = null
let marker: any = null

const latInput = ref<number | null>(props.latitude)
const lonInput = ref<number | null>(props.longitude)

watch(
    () => props.latitude,
    (v) => {
      latInput.value = v
    },
)
watch(
    () => props.longitude,
    (v) => {
      lonInput.value = v
    },
)

watch([latInput, lonInput], async ([lat, lon]) => {
  emit('update:latitude', lat ?? null)
  emit('update:longitude', lon ?? null)
  if (!mapInstance || typeof lat !== 'number' || typeof lon !== 'number') return
  const L = (await import('leaflet')).default
  setOrMoveMarker(L, lat, lon, false)
})

async function init() {
  if (!mapEl.value || typeof window === 'undefined') return
  const L = (await import('leaflet')).default
  const config = await load()
  if (!mapEl.value) return

  const startLat = props.latitude ?? props.initialCenter[0]
  const startLon = props.longitude ?? props.initialCenter[1]
  const startZoom = props.latitude && props.longitude ? 13 : props.initialZoom

  mapInstance = L.map(mapEl.value, {center: [startLat, startLon], zoom: startZoom, scrollWheelZoom: true})
  L.tileLayer(config.urlTemplate, {
    minZoom: config.minZoom,
    maxZoom: config.maxZoom,
    attribution: config.attribution,
  }).addTo(mapInstance)

  if (typeof props.latitude === 'number' && typeof props.longitude === 'number') {
    setOrMoveMarker(L, props.latitude, props.longitude, false)
  }

  mapInstance.on('click', (event: any) => {
    const {lat, lng} = event.latlng
    setOrMoveMarker(L, lat, lng, true)
  })
}

function setOrMoveMarker(L: any, lat: number, lng: number, fromMapClick: boolean) {
  const rounded = {
    lat: Math.round(lat * 1_000_000) / 1_000_000,
    lng: Math.round(lng * 1_000_000) / 1_000_000,
  }
  if (!marker) {
    marker = L.marker([rounded.lat, rounded.lng], {draggable: true}).addTo(mapInstance)
    marker.on('dragend', () => {
      const pos = marker.getLatLng()
      latInput.value = Math.round(pos.lat * 1_000_000) / 1_000_000
      lonInput.value = Math.round(pos.lng * 1_000_000) / 1_000_000
    })
  } else {
    marker.setLatLng([rounded.lat, rounded.lng])
  }
  if (fromMapClick) {
    latInput.value = rounded.lat
    lonInput.value = rounded.lng
  }
}

function clearPin() {
  if (marker && mapInstance) {
    mapInstance.removeLayer(marker)
    marker = null
  }
  latInput.value = null
  lonInput.value = null
}

defineExpose({
  /**
   * Centers the map on the given coordinates and drops a marker. Used by
   * {@code GeolocateButton} when "use my position" succeeds.
   */
  async setCoordinates(lat: number, lng: number) {
    const L = (await import('leaflet')).default
    if (mapInstance) {
      mapInstance.setView([lat, lng], 13)
    }
    setOrMoveMarker(L, lat, lng, true)
  },
})

onMounted(async () => {
  await nextTick()
  await init()
})

onBeforeUnmount(() => {
  if (mapInstance) {
    mapInstance.remove()
    mapInstance = null
    marker = null
  }
})
</script>

<template>
  <div class="space-y-3">
    <div ref="mapEl" :style="{height}" class="w-full rounded-(--radius-theme) overflow-hidden border border-bg-light-accent dark:border-bg-dark-accent z-0"/>
    <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
      <div>
        <FieldLabel>{{ t('geolocation.latitude') }}</FieldLabel>
        <DecimalInput
            :model-value="latInput ?? undefined"
            step="0.000001"
            :placeholder="t('geolocation.latitudePlaceholder')"
            @update:model-value="(v) => (latInput = (v ?? null) as number | null)"
        />
      </div>
      <div>
        <FieldLabel>{{ t('geolocation.longitude') }}</FieldLabel>
        <DecimalInput
            :model-value="lonInput ?? undefined"
            step="0.000001"
            :placeholder="t('geolocation.longitudePlaceholder')"
            @update:model-value="(v) => (lonInput = (v ?? null) as number | null)"
        />
      </div>
    </div>
    <div class="flex flex-wrap gap-2">
      <slot name="actions"/>
      <SecondaryButton compact @click="clearPin">
        {{ t('geolocation.clearPin') }}
      </SecondaryButton>
    </div>
  </div>
</template>
