/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import {useSidebarCounts} from '@/composables/useSidebarCounts'

defineProps<{
  openGroup: string | null
  isDesktop: boolean
}>()

const emit = defineEmits<{
  (e: 'update:openGroup', value: string | null): void
  (e: 'navigate'): void
}>()

const {t} = useI18n()
const {counts} = useSidebarCounts()

function close() {
  emit('navigate')
}
</script>

<template>
  <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => emit('update:openGroup', v)" :badge="counts.federationRequests" :icon="['fas', 'arrow-right-arrow-left']" :label="t('sidebar.federation')" to="/station/federate" name="station-federation" @navigate="close">
    <SidebarLink data-onboarding="nav.federation" :badge="counts.federationRequests" :icon="['fas', 'gear']" name="station-federation-settings" to="/station/federate/settings" @navigate="close">
      {{ t('sidebar.federationSettings') }}
    </SidebarLink>
    <SidebarLink :icon="['fas', 'compass']" name="station-discovery" to="/station/federate/discovery" @navigate="close">
      {{ t('sidebar.discovery') }}
    </SidebarLink>
  </SidebarGroup>
</template>
