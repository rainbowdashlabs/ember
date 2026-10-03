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
 *
 * <p>Pins are kept per station and updated in place: a new list of stations adds and removes only the pins
 * that differ, and choosing another station moves only the two pins concerned. `stations` is watched by
 * reference, so a caller hands over a new array rather than changing the old one. With `fitOnUpdate` the map
 * frames the pins again only when the stations on it, or their places, change.
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

const PIN_FIELDS = ['name', 'latitude', 'longitude', 'subtitle', 'href', 'tint'] as const satisfies readonly (keyof MapStation)[]

const emit = defineEmits<{
  (e: 'marker-click', uid: string): void
  (e: 'ready'): void
}>()

const mapEl = ref<HTMLDivElement | null>(null)
const {load} = useMapsConfig()

type Leaflet = Awaited<ReturnType<typeof loadLeaflet>>

interface Pin {
  station: MapStation
  marker: Marker
}

let leaflet: Leaflet | null = null
let mapInstance: LeafletMap | null = null
let markerLayer: LayerGroup | null = null
let selectedLayer: LayerGroup | null = null
let shownSelectedUid: string | null = null
let fittedPlaces = ''
const pins: Map<string, Pin> = new Map()

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
  leaflet = L
  shownSelectedUid = props.selectedUid
  showStations()
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

function iconOf(L: Leaflet, station: MapStation, selected: boolean) {
  return L.divIcon({
    className: selected ? 'station-map-pin station-map-pin-selected' : 'station-map-pin',
    html: `<span class="pin" style="background:${tintColor(station.tint)}"></span>`,
    iconSize: [18, 18],
    iconAnchor: [9, 18],
    tooltipAnchor: [8, -9],
  })
}

function bindLabel(marker: Marker, station: MapStation, selected: boolean) {
  if (!props.labels) return
  marker.bindTooltip(escapeHtml(station.name), {
    permanent: true,
    direction: 'right',
    className: selected ? 'station-map-label station-map-label-selected' : 'station-map-label',
  })
}

function markerOf(L: Leaflet, station: MapStation, selected: boolean): Marker {
  const marker = L.marker([station.latitude, station.longitude], {icon: iconOf(L, station, selected), title: station.name})
  if (props.popups) marker.bindPopup(popupOf(station))
  bindLabel(marker, station, selected)
  marker.on('click', () => emit('marker-click', station.uid))
  return marker
}

function hasPlace(station: MapStation): boolean {
  return typeof station.latitude === 'number' && typeof station.longitude === 'number'
}

function samePin(a: MapStation, b: MapStation): boolean {
  return PIN_FIELDS.every(field => a[field] === b[field])
}

function layerOf(uid: string): LayerGroup | null {
  return uid === shownSelectedUid ? selectedLayer : markerLayer
}

/** Takes the pins off the map whose station is gone or has changed. */
function removeStalePins(wanted: ReadonlyMap<string, MapStation>) {
  for (const [uid, pin] of pins) {
    const station = wanted.get(uid)
    if (station && samePin(station, pin.station)) continue
    layerOf(uid)?.removeLayer(pin.marker)
    pins.delete(uid)
  }
}

/** Puts a pin on the map for every station that has none yet. */
function addMissingPins(L: Leaflet, wanted: ReadonlyMap<string, MapStation>) {
  for (const [uid, station] of wanted) {
    if (pins.has(uid)) continue
    const marker = markerOf(L, station, uid === shownSelectedUid)
    layerOf(uid)?.addLayer(marker)
    pins.set(uid, {station, marker})
  }
}

/** Frames every pin, but only once the stations on the map or their places differ from the last framing. */
function fitToPins() {
  if (!mapInstance || !props.fitOnUpdate) return
  const stations = [...pins.values()].map(pin => pin.station)
  const places = stations.map(station => `${station.uid}@${station.latitude},${station.longitude}`).sort().join(' ')
  if (places === fittedPlaces) return
  fittedPlaces = places
  if (stations.length === 0) return
  mapInstance.fitBounds(stations.map(station => [station.latitude, station.longitude]), {padding: [40, 40], maxZoom: 13})
}

/**
 * Brings the pins in line with the stations: a pin whose station left or changed is taken off, a station
 * without a pin gets one, and every other pin stays as it is.
 */
function showStations() {
  if (!leaflet) return
  const wanted: ReadonlyMap<string, MapStation> = new Map(props.stations.filter(hasPlace).map(station => [station.uid, station]))
  removeStalePins(wanted)
  addMissingPins(leaflet, wanted)
  fitToPins()
}

/** Moves one pin between the clusters and the layer of the chosen pin, restyled to match. */
function movePin(uid: string, selected: boolean) {
  const pin = pins.get(uid)
  if (!leaflet || !pin || !markerLayer || !selectedLayer) return
  const [from, to] = selected ? [markerLayer, selectedLayer] : [selectedLayer, markerLayer]
  from.removeLayer(pin.marker)
  pin.marker.setIcon(iconOf(leaflet, pin.station, selected))
  pin.marker.unbindTooltip()
  bindLabel(pin.marker, pin.station, selected)
  to.addLayer(pin.marker)
}

/** Highlights the chosen pin, touching only it and the one chosen before. */
function showSelection(uid: string | null) {
  if (!leaflet || uid === shownSelectedUid) return
  const previous = shownSelectedUid
  shownSelectedUid = uid
  if (previous) movePin(previous, false)
  if (uid) movePin(uid, true)
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"]/g, (c) => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'}[c] ?? c))
}

watch(() => props.stations, showStations)

watch(() => props.selectedUid, uid => showSelection(uid ?? null))

onMounted(async () => {
  await nextTick()
  await init()
})

onBeforeUnmount(() => {
  if (mapInstance) {
    mapInstance.remove()
    mapInstance = null
    leaflet = null
    markerLayer = null
    selectedLayer = null
    pins.clear()
  }
})

defineExpose({
  /**
   * Focuses the map on a single station and opens its popup.
   */
  focus(uid: string) {
    if (!mapInstance) return
    const marker = pins.get(uid)?.marker
    if (!marker) return
    mapInstance.setView(marker.getLatLng(), Math.max(mapInstance.getZoom(), 11))
    marker.openPopup()
  },
  /**
   * Moves the map so a station's pin sits in the middle, keeping the zoom.
   */
  center(uid: string) {
    const marker = pins.get(uid)?.marker
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
