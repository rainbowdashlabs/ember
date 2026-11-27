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
import {navigationLinks, navigationPlatform, type NavigationPlatform} from '@/util/navigationLinks'
import 'leaflet/dist/leaflet.css'

/**
 * A small map showing where one place lies, to look at only: no dragging the pin, no clicking it
 * somewhere else. Links below start navigation there, in the way the reader's device offers it, and
 * open the same spot on OpenStreetMap; an instance without map tiles shows only the links rather than an
 * empty box. On a touch device the map does not move under one finger, so swiping over it scrolls the
 * page as everywhere else.
 *
 * <p>The device is asked once the page runs in the browser, so the server and the first render agree on
 * the desktop links and a phone switches to its own right after.
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
const platform = ref<NavigationPlatform>('desktop')

const ZOOM = 16

let mapInstance: LeafletMap | null = null
let marker: Marker | null = null

const osmLink = computed(() =>
    `https://www.openstreetmap.org/?mlat=${props.latitude}&mlon=${props.longitude}#map=${ZOOM}/${props.latitude}/${props.longitude}`)

const navigation = computed(() => navigationLinks(
    {latitude: props.latitude, longitude: props.longitude, label: props.label}, platform.value))

const NAVIGATION_LABEL = {navigate: 'places.navigate', apple: 'places.openInAppleMaps', google: 'places.openInGoogleMaps'} as const

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
  platform.value = navigationPlatform()
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
    <div class="flex flex-wrap gap-x-3 gap-y-1 text-xs" data-testid="place-map-links">
      <a
          v-for="link in navigation"
          :key="link.kind"
          :href="link.href"
          :data-testid="`place-map-${link.kind}`"
          rel="noopener noreferrer"
          :target="link.kind === 'navigate' ? undefined : '_blank'"
      >{{ t(NAVIGATION_LABEL[link.kind]) }}</a>
      <a :href="osmLink" rel="noopener noreferrer" target="_blank">{{ t('places.openInOsm') }}</a>
    </div>
  </div>
</template>
