/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import StationMap, {type MapStation} from '@/components/map/StationMap.vue'
import DiscoveryTile from '@/components/discovery/DiscoveryTile.vue'
import {discoveryKey, isOnTheMap} from '@/components/discovery/tileRules'
import type {DiscoveryViewer} from '@/composables/useDiscoveryViewer'
import type {DiscoveryEntry} from '@/api/generated/schema'

/**
 * The map of the discovery page with every station that has a place on it, each pin named, and below it
 * the tile of the chosen station, drawn large. The map keeps the chosen station in its middle.
 */
const props = defineProps<{
  stations: DiscoveryEntry[]
  chosen: DiscoveryEntry | null
  viewer: DiscoveryViewer
}>()

const emit = defineEmits<{
  choose: [station: DiscoveryEntry | null]
  connect: [station: DiscoveryEntry]
  invite: [station: DiscoveryEntry]
}>()

const {t} = useI18n()
const map = ref<InstanceType<typeof StationMap> | null>(null)

const pins = computed<MapStation[]>(() => props.stations
    .filter(isOnTheMap)
    .map((station) => ({
      uid: discoveryKey(station),
      name: station.name,
      latitude: station.latitude as number,
      longitude: station.longitude as number,
      tint: station.isOwnStation ? 'local' : station.alreadyFederated ? 'near' : null,
    })))

const chosenKey = computed(() => props.chosen ? discoveryKey(props.chosen) : null)

function pick(key: string) {
  emit('choose', props.stations.find(station => discoveryKey(station) === key) ?? null)
}

function centreChosen() {
  if (chosenKey.value) map.value?.center(chosenKey.value)
}

watch(chosenKey, centreChosen, {flush: 'post'})
</script>

<template>
  <section class="space-y-3" :aria-label="t('discovery.mapLabel')">
    <StationMap
        ref="map"
        :stations="pins"
        :selected-uid="chosenKey"
        labels
        :popups="false"
        height="clamp(240px, 50vw, 360px)"
        @marker-click="pick"
        @ready="centreChosen"
    />
    <DiscoveryTile
        v-if="chosen"
        :station="chosen"
        :viewer="viewer"
        large
        @close="emit('choose', null)"
        @connect="s => emit('connect', s)"
        @invite="s => emit('invite', s)"
    />
  </section>
</template>
