/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import {ClusterPermission} from '@/api/clusters'
import {useSession} from '@/composables/useSession'

/**
 * The parts of the association's gear, as addresses rather than as state.
 *
 * <p>Each tab is its own page, so reloading keeps you where you were and a notification about a
 * movement waiting on the association can point straight at the tab that shows it.
 */
const {t} = useI18n()
const route = useRoute()
const {hasClusterPermission} = useSession()

const tabs = computed(() => [
  {name: 'cluster-inventory', to: '/cluster/inventory', label: t('clusterInventory.tabStock'), shown: true},
  {
    name: 'cluster-inventory-out',
    to: '/cluster/inventory/out',
    label: t('clusterInventory.tabOut'),
    shown: true,
  },
  {
    name: 'cluster-inventory-requirements',
    to: '/cluster/inventory/requirements',
    label: t('clusterInventory.tabRequirements'),
    shown: true,
  },
  {
    name: 'cluster-inventory-collections',
    to: '/cluster/inventory/collections',
    label: t('clusterInventory.tabCollections'),
    shown: true,
  },
  {
    name: 'cluster-inventory-movements',
    to: '/cluster/inventory/movements',
    label: t('clusterInventory.tabMovements'),
    shown: hasClusterPermission(ClusterPermission.CLUSTER_INVENTORY_TRANSFER),
  },
  {
    name: 'cluster-inventory-statistics',
    to: '/cluster/inventory/statistics',
    label: t('clusterInventory.tabStatistics'),
    shown: true,
  },
  {
    name: 'cluster-inventory-settings',
    to: '/cluster/inventory/settings',
    label: t('clusterInventory.tabSettings'),
    // Either right is enough to have something to see there, and each section shows under its own.
    shown: hasClusterPermission(ClusterPermission.CLUSTER_MODULES)
        || hasClusterPermission(ClusterPermission.CLUSTER_INVENTORY_MANAGER),
  },
].filter(tab => tab.shown))

/** A tab stays lit while somewhere below it is open, so a container walk still reads as gear. */
function active(tab: {to: string}): boolean {
  if (tab.to === '/cluster/inventory') return route.path === '/cluster/inventory'
  return route.path.startsWith(tab.to)
}
</script>

<template>
  <div data-testid="cluster-inventory-tabs"
       class="flex gap-2 border-b border-bg-light-accent dark:border-bg-dark-accent overflow-x-auto">
    <router-link
        v-for="tab in tabs"
        :key="tab.name"
        :to="tab.to"
        :class="active(tab) ? 'border-primary text-primary' : 'border-transparent text-(--text-muted) hover:text-(--text)'"
        :data-testid="`tab-${tab.name}`"
        class="px-4 py-2 text-sm font-medium transition-colors -mb-px border-b-2 whitespace-nowrap no-underline"
    >
      {{ tab.label }}
    </router-link>
  </div>
</template>
