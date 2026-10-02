/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import StationModuleToggle from '@/components/modules/StationModuleToggle.vue'
import {STATION_MODULE_OPTIONS} from '@/data/stationModules'
import type {StationModule} from '@/api/generated/schema'

/**
 * Every module of the station with its switch. A module the cluster switched off is shown as locked
 * rather than simply off, and says who locked it.
 */
const props = defineProps<{
  disabledModules: ReadonlySet<StationModule>
  clusterDenied: ReadonlySet<StationModule>
  clusterName: string | null
  saving: boolean
}>()

const emit = defineEmits<{
  toggle: [module: StationModule]
}>()

const {t} = useI18n()

function isModuleEnabled(key: StationModule): boolean {
  return !props.disabledModules.has(key) && !props.clusterDenied.has(key)
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('stationManage.modulesTitle') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('stationManage.modulesHint') }}</p>
    <div class="space-y-3">
      <StationModuleToggle
          v-for="mod in STATION_MODULE_OPTIONS"
          :key="mod.value"
          :module="mod"
          :model-value="isModuleEnabled(mod.value)"
          :disabled="saving || clusterDenied.has(mod.value)"
          @update:model-value="emit('toggle', mod.value)"
      >
        <span v-if="clusterDenied.has(mod.value)" class="ml-2 text-xs text-(--text-muted)">
          <font-awesome-icon :icon="['fas', 'lock']" class="mr-1 h-3 w-3"/>
          {{ t('stationManage.moduleClusterLocked', {cluster: clusterName ?? ''}) }}
        </span>
      </StationModuleToggle>
    </div>
  </NeutralContainer>
</template>
