/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import type {LayerGroup, Map as LeafletMap, Marker, TileLayer} from 'leaflet'
import {useMapsConfig} from '@/composables/useMapsConfig'
import {loadLeaflet} from '@/util/leaflet'
import 'leaflet/dist/leaflet.css'
import 'leaflet.markercluster/dist/MarkerCluster.css'
import 'leaflet.markercluster/dist/MarkerCluster.Default.css'

export interface MapStation {
  uid: string
  name: string
  latitude: number
  longitude: number
  /** Optional subtitle line in the popup (e.g. distance label or city). */
  subtitle?: string | null
  /** Optional external link rendered as a button in the popup. */
  href?: string | null
  /** Optional category tint: orange (local), blue (near), grey (far). */
  tint?: 'local' | 'near' | 'far' | null
}

const props = withDefaults(
    defineProps<{
      stations: MapStation[]
      height?: string
      cluster?: boolean
      fitOnUpdate?: boolean
      initialCenter?: [number, number]
      initialZoom?: number
    }>(),
    {
      height: '420px',
      cluster: true,
      fitOnUpdate: true,
      initialCenter: () => [51.0, 10.0] as [number, number],
      initialZoom: 5,
    },
)

const emit = defineEmits<{
  (e: 'marker-click', uid: string): void
  (e: 'ready'): void
}>()

const mapEl = ref<HTMLDivElement | null>(null)
const {load} = useMapsConfig()

type Leaflet = Awaited<ReturnType<typeof loadLeaflet>>

let mapInstance: LeafletMap | null = null
let tileLayer: TileLayer | null = null
let markerLayer: LayerGroup | null = null
const markers: Map<string, Marker> = new Map()

async function init() {
  if (!mapEl.value || typeof window === 'undefined') return
  const L = await loadLeaflet()
  if (props.cluster) {
    await import('leaflet.markercluster')
  }
  const config = await load()
  if (!mapEl.value) return

  mapInstance = L.map(mapEl.value, {
    center: props.initialCenter,
    zoom: props.initialZoom,
    scrollWheelZoom: true,
  })
  tileLayer = L.tileLayer(config.urlTemplate, {
    minZoom: config.minZoom,
    maxZoom: config.maxZoom,
    attribution: config.attribution,
  }).addTo(mapInstance)
  markerLayer = props.cluster ? L.markerClusterGroup() : L.layerGroup()
  markerLayer.addTo(mapInstance)
  renderMarkers(L)
  emit('ready')
}

function tintColor(tint?: MapStation['tint']): string {
  switch (tint) {
    case 'local':
      return '#ff6421'
    case 'near':
      return '#3694ff'
    case 'far':
      return '#9ca3af'
    default:
      return '#c71100'
  }
}

function renderMarkers(L: Leaflet) {
  if (!markerLayer) return
  markerLayer.clearLayers()
  markers.clear()
  const bounds: [number, number][] = []
  for (const station of props.stations) {
    if (typeof station.latitude !== 'number' || typeof station.longitude !== 'number') continue
    const icon = L.divIcon({
      className: 'station-map-pin',
      html: `<span class="pin" style="background:${tintColor(station.tint)}"></span>`,
      iconSize: [18, 18],
      iconAnchor: [9, 18],
    })
    const marker = L.marker([station.latitude, station.longitude], {icon})
    const popupParts: string[] = []
    popupParts.push(`<strong>${escapeHtml(station.name)}</strong>`)
    if (station.subtitle) popupParts.push(`<div>${escapeHtml(station.subtitle)}</div>`)
    if (station.href) popupParts.push(
        `<div class="mt-2"><a href="${encodeURI(station.href)}" target="_blank" rel="noopener" class="text-(--primary)">${escapeHtml(station.name)} →</a></div>`,
    )
    marker.bindPopup(popupParts.join(''))
    marker.on('click', () => emit('marker-click', station.uid))
    markerLayer.addLayer(marker)
    markers.set(station.uid, marker)
    bounds.push([station.latitude, station.longitude])
  }
  if (props.fitOnUpdate && bounds.length > 0 && mapInstance) {
    mapInstance.fitBounds(bounds, {padding: [40, 40], maxZoom: 13})
  }
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"]/g, (c) => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'}[c] ?? c))
}

watch(
    () => props.stations,
    async () => {
      if (!mapInstance) return
      const L = await loadLeaflet()
      renderMarkers(L)
    },
    {deep: true},
)

onMounted(async () => {
  await nextTick()
  await init()
})

onBeforeUnmount(() => {
  if (mapInstance) {
    mapInstance.remove()
    mapInstance = null
    tileLayer = null
    markerLayer = null
    markers.clear()
  }
})

defineExpose({
  /**
   * Focuses the map on a single station and opens its popup.
   */
  focus(uid: string) {
    if (!mapInstance) return
    const marker = markers.get(uid)
    if (!marker) return
    mapInstance.setView(marker.getLatLng(), Math.max(mapInstance.getZoom(), 11))
    marker.openPopup()
  },
})
</script>

<template>
  <div ref="mapEl" :style="{height}" class="w-full rounded-(--radius-theme) overflow-hidden border border-bg-light-accent dark:border-bg-dark-accent z-0"/>
</template>

<style scoped>
:deep(.station-map-pin .pin) {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 50% 50% 50% 0;
  transform: rotate(-45deg);
  border: 2px solid #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.35);
}
</style>
