/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'

/**
 * Looking in on what the station's own application is doing. Kept apart from Manage, which holds
 * the things one sets up once: this is the corner one comes back to.
 */
defineProps<{
  openGroup: string | null
  isDesktop: boolean
}>()

const emit = defineEmits<{
  (e: 'update:openGroup', value: string | null): void
  (e: 'navigate'): void
}>()

const {t} = useI18n()

function close() {
  emit('navigate')
}
</script>

<template>
  <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => emit('update:openGroup', v)" :icon="['fas', 'chart-line']" :label="t('sidebar.monitoring')" to="/station/monitoring/traffic" name="station-monitoring" @navigate="close">
    <SidebarLink :icon="['fas', 'tower-broadcast']" name="station-traffic" to="/station/monitoring/traffic" @navigate="close">
      {{ t('sidebar.stationTraffic') }}
    </SidebarLink>
    <SidebarLink :icon="['fas', 'chart-pie']" name="station-insights" to="/station/monitoring/insights" @navigate="close">
      {{ t('sidebar.stationInsights') }}
    </SidebarLink>
    <SidebarLink :icon="['fas', 'rss']" name="station-feeds" to="/station/monitoring/feeds" @navigate="close">
      {{ t('sidebar.stationFeeds') }}
    </SidebarLink>
    <SidebarLink :icon="['fas', 'hard-drive']" name="station-storage" to="/station/monitoring/storage" @navigate="close">
      {{ t('sidebar.storage') }}
    </SidebarLink>
  </SidebarGroup>
</template>
