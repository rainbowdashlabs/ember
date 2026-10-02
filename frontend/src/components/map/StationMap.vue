/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import type {LayerGroup, Map as LeafletMap, Marker} from 'leaflet'
import {useMapsConfig} from '@/composables/useMapsConfig'
import {addTileLayer, loadLeaflet} from '@/util/leaflet'
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

/**
 * Stations as pins on a map.
 *
 * <p>With `labels` every pin carries the station's name. Pins close enough for their names to run into
 * each other are gathered into a counted marker until the map is zoomed in, which the clustering does
 * with a radius wide enough for a name. The pin named by `selectedUid` stays outside the clusters, so its
 * name always shows, and is drawn highlighted. Without `popups` a click on a pin only reports it.
 */
const props = withDefaults(
    defineProps<{
      stations: MapStation[]
      height?: string
      cluster?: boolean
      fitOnUpdate?: boolean
      initialCenter?: [number, number]
      initialZoom?: number
      labels?: boolean
      popups?: boolean
      selectedUid?: string | null
    }>(),
    {
      height: '420px',
      cluster: true,
      fitOnUpdate: true,
      initialCenter: () => [51.0, 10.0] as [number, number],
      initialZoom: 5,
      labels: false,
      popups: true,
      selectedUid: null,
    },
)

const LABELLED_CLUSTER_RADIUS = 110

const emit = defineEmits<{
  (e: 'marker-click', uid: string): void
  (e: 'ready'): void
}>()

const mapEl = ref<HTMLDivElement | null>(null)
const {load} = useMapsConfig()

type Leaflet = Awaited<ReturnType<typeof loadLeaflet>>

let mapInstance: LeafletMap | null = null
let markerLayer: LayerGroup | null = null
let selectedLayer: LayerGroup | null = null
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
  addTileLayer(L, mapInstance, config)
  markerLayer = props.cluster
      ? L.markerClusterGroup(props.labels ? {maxClusterRadius: LABELLED_CLUSTER_RADIUS, showCoverageOnHover: false} : {})
      : L.layerGroup()
  markerLayer.addTo(mapInstance)
  selectedLayer = L.layerGroup()
  selectedLayer.addTo(mapInstance)
  renderMarkers(L, props.fitOnUpdate)
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

function popupOf(station: MapStation): string {
  const popupParts: string[] = []
  popupParts.push(`<strong>${escapeHtml(station.name)}</strong>`)
  if (station.subtitle) popupParts.push(`<div>${escapeHtml(station.subtitle)}</div>`)
  if (station.href) popupParts.push(
      `<div class="mt-2"><a href="${encodeURI(station.href)}" target="_blank" rel="noopener" class="text-(--primary)">${escapeHtml(station.name)} →</a></div>`,
  )
  return popupParts.join('')
}

function markerOf(L: Leaflet, station: MapStation, selected: boolean): Marker {
  const icon = L.divIcon({
    className: selected ? 'station-map-pin station-map-pin-selected' : 'station-map-pin',
    html: `<span class="pin" style="background:${tintColor(station.tint)}"></span>`,
    iconSize: [18, 18],
    iconAnchor: [9, 18],
    tooltipAnchor: [8, -9],
  })
  const marker = L.marker([station.latitude, station.longitude], {icon, title: station.name})
  if (props.popups) marker.bindPopup(popupOf(station))
  if (props.labels) {
    marker.bindTooltip(escapeHtml(station.name), {
      permanent: true,
      direction: 'right',
      className: selected ? 'station-map-label station-map-label-selected' : 'station-map-label',
    })
  }
  marker.on('click', () => emit('marker-click', station.uid))
  return marker
}

function renderMarkers(L: Leaflet, fit: boolean) {
  if (!markerLayer || !selectedLayer) return
  markerLayer.clearLayers()
  selectedLayer.clearLayers()
  markers.clear()
  const bounds: [number, number][] = []
  for (const station of props.stations) {
    if (typeof station.latitude !== 'number' || typeof station.longitude !== 'number') continue
    const selected = station.uid === props.selectedUid
    const marker = markerOf(L, station, selected)
    const layer = selected ? selectedLayer : markerLayer
    layer.addLayer(marker)
    markers.set(station.uid, marker)
    bounds.push([station.latitude, station.longitude])
  }
  if (fit && bounds.length > 0 && mapInstance) {
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
      renderMarkers(L, props.fitOnUpdate)
    },
    {deep: true},
)

watch(
    () => props.selectedUid,
    async () => {
      if (!mapInstance) return
      const L = await loadLeaflet()
      renderMarkers(L, false)
    },
)

onMounted(async () => {
  await nextTick()
  await init()
})

onBeforeUnmount(() => {
  if (mapInstance) {
    mapInstance.remove()
    mapInstance = null
    markerLayer = null
    selectedLayer = null
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
  /**
   * Moves the map so a station's pin sits in the middle, keeping the zoom.
   */
  center(uid: string) {
    const marker = markers.get(uid)
    if (!mapInstance || !marker) return
    mapInstance.panTo(marker.getLatLng())
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

:deep(.station-map-pin-selected .pin) {
  width: 18px;
  height: 18px;
  border-width: 3px;
}

:deep(.station-map-label) {
  padding: 1px 6px;
  font-size: 0.75rem;
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
}

:deep(.station-map-label-selected) {
  background: #ff6421;
  border-color: #c71100;
  color: #fff;
  z-index: 1000;
}
</style>
