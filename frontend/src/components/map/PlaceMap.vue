/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import type {Map as LeafletMap, Marker} from 'leaflet'
import {useI18n} from 'vue-i18n'
import {useMapsConfig} from '@/composables/useMapsConfig'
import {addTileLayer, loadLeaflet} from '@/util/leaflet'
import 'leaflet/dist/leaflet.css'

/**
 * A small map showing where one place lies, to look at only: no dragging the pin, no clicking it
 * somewhere else. A link below opens the same spot on OpenStreetMap for directions; an instance without map tiles
 * shows only the link rather than an empty box. On a touch device
 * the map does not move under one finger, so swiping over it scrolls the page as everywhere else.
 */
const props = withDefaults(defineProps<{
  latitude: number
  longitude: number
  /** The place's name, read out for the map and shown on the pin. */
  label: string
  height?: string
}>(), {height: '180px'})

const {t} = useI18n()
const {load} = useMapsConfig()
const mapEl = ref<HTMLDivElement | null>(null)
/** Whether the instance has map tiles; unknown until its map settings are read, when nothing is drawn yet. */
const hasTiles = ref<boolean | null>(null)

const ZOOM = 16

let mapInstance: LeafletMap | null = null
let marker: Marker | null = null

const osmLink = computed(() =>
    `https://www.openstreetmap.org/?mlat=${props.latitude}&mlon=${props.longitude}#map=${ZOOM}/${props.latitude}/${props.longitude}`)

async function init() {
  if (typeof window === 'undefined') return
  const L = await loadLeaflet()
  const config = await load()
  hasTiles.value = Boolean(config.urlTemplate)
  if (!hasTiles.value) return
  await nextTick()
  if (!mapEl.value) return
  mapInstance = L.map(mapEl.value, {
    center: [props.latitude, props.longitude],
    zoom: ZOOM,
    scrollWheelZoom: false,
    dragging: !L.Browser.mobile,
  })
  addTileLayer(L, mapInstance, config)
  marker = L.marker([props.latitude, props.longitude], {draggable: false, title: props.label}).addTo(mapInstance)
}

watch(() => [props.latitude, props.longitude] as const, ([lat, lng]) => {
  mapInstance?.setView([lat, lng], ZOOM)
  marker?.setLatLng([lat, lng])
})

onMounted(async () => {
  await nextTick()
  await init()
})

onBeforeUnmount(() => {
  mapInstance?.remove()
  mapInstance = null
  marker = null
})
</script>

<template>
  <div class="space-y-1" data-testid="place-map">
    <div
        v-if="hasTiles"
        ref="mapEl"
        :style="{height}"
        :aria-label="t('places.mapOf', {place: label})"
        role="region"
        class="w-full rounded-(--radius-theme) overflow-hidden border border-bg-light-accent dark:border-bg-dark-accent z-0"
    />
    <a :href="osmLink" class="text-xs" rel="noopener noreferrer" target="_blank">{{ t('places.openInOsm') }}</a>
  </div>
</template>
