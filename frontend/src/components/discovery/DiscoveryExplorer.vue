/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import EmptyState from '@/components/feedback/EmptyState.vue'
import DiscoveryMapSection from '@/components/discovery/DiscoveryMapSection.vue'
import DiscoveryToolbar from '@/components/discovery/DiscoveryToolbar.vue'
import DiscoveryGroups from '@/components/discovery/DiscoveryGroups.vue'
import {searchDiscovery} from '@/components/discovery/discoverySearch'
import type {DiscoveryViewer} from '@/composables/useDiscoveryViewer'
import type {DiscoveryEntry} from '@/api/generated/schema'

/**
 * A discovery page: the map on top, the chosen station's tile right below it, then the search, then the
 * list. The search filters the map and the list together, and a chosen station the search no longer
 * finds is closed.
 *
 * <p>The chosen station is kept in the page address under `selectionKey`, so a link can open the page
 * with a station already chosen. Choosing one from the list brings the map back into view.
 */
const props = defineProps<{
  stations: DiscoveryEntry[]
  viewer: DiscoveryViewer
  selectionKey: string
}>()

const emit = defineEmits<{
  connect: [station: DiscoveryEntry]
  invite: [station: DiscoveryEntry]
}>()

const search = defineModel<string>('search', {required: true})

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const mapSection = ref<HTMLElement | null>(null)

const shown = computed(() => searchDiscovery(props.stations, search.value))

const chosenUid = computed(() => {
  const value = route.query[props.selectionKey]
  return typeof value === 'string' ? value : null
})

const chosen = computed(() => shown.value.find(station => station.stationUid === chosenUid.value) ?? null)

function choose(station: DiscoveryEntry | null) {
  void router.replace({query: {...route.query, [props.selectionKey]: station?.stationUid}})
}

function locate(station: DiscoveryEntry) {
  choose(station)
  mapSection.value?.scrollIntoView?.({behavior: 'smooth', block: 'start'})
}

watch(shown, () => {
  if (chosenUid.value && !chosen.value && props.stations.length > 0) choose(null)
})
</script>

<template>
  <div class="space-y-4">
    <div ref="mapSection">
      <DiscoveryMapSection
          :stations="shown"
          :chosen="chosen"
          :viewer="viewer"
          @choose="choose"
          @connect="s => emit('connect', s)"
          @invite="s => emit('invite', s)"
      />
    </div>
    <DiscoveryToolbar v-model:search="search"/>
    <EmptyState v-if="shown.length === 0" :message="t('discovery.noSearchResults')"/>
    <DiscoveryGroups
        v-else
        :stations="shown"
        :viewer="viewer"
        @connect="s => emit('connect', s)"
        @invite="s => emit('invite', s)"
        @locate="locate"
    />
  </div>
</template>
