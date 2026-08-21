/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import DiscoveryGrid from '@/components/discovery/DiscoveryGrid.vue'
import type {DiscoveryEntry} from '@/api/discovery'

const props = defineProps<{
  stations: DiscoveryEntry[]
  canConnect: boolean
  showInvite: boolean
}>()

const emit = defineEmits<{
  connect: [station: DiscoveryEntry]
  invite: [station: DiscoveryEntry]
}>()

const {t} = useI18n()

/**
 * The stations that answer to a cluster, gathered under it.
 *
 * <p>Grouping is a reading aid, not a filter: every station on the page is in exactly one of the groups or
 * in the loose list below them, and a station hidden from discovery is in neither because it never reached
 * this component at all.
 */
const groups = computed(() => {
  const byCluster = new Map<string, {name: string; stations: DiscoveryEntry[]}>()
  for (const station of props.stations) {
    if (!station.clusterUid) continue
    const group = byCluster.get(station.clusterUid)
    if (group) group.stations.push(station)
    else byCluster.set(station.clusterUid, {name: station.clusterName ?? '', stations: [station]})
  }
  return [...byCluster.entries()]
      .map(([uid, group]) => ({uid, ...group}))
      .sort((a, b) => a.name.localeCompare(b.name))
})

/** Everything that answers to nobody, which needs no heading. */
const loose = computed(() => props.stations.filter(station => !station.clusterUid))
</script>

<template>
  <div class="space-y-6">
    <section v-for="group in groups" :key="group.uid" class="space-y-3">
      <SectionHeader>{{ group.name }}</SectionHeader>
      <DiscoveryGrid
          :can-connect="canConnect"
          :show-invite="showInvite"
          :stations="group.stations"
          @connect="s => emit('connect', s)"
          @invite="s => emit('invite', s)"
      />
    </section>

    <section v-if="loose.length > 0" class="space-y-3">
      <SectionHeader v-if="groups.length > 0">{{ t('discovery.ungrouped') }}</SectionHeader>
      <DiscoveryGrid
          :can-connect="canConnect"
          :show-invite="showInvite"
          :stations="loose"
          @connect="s => emit('connect', s)"
          @invite="s => emit('invite', s)"
      />
    </section>
  </div>
</template>
