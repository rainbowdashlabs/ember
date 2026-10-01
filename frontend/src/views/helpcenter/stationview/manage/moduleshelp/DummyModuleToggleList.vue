/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import StationModuleToggle from '@/components/modules/StationModuleToggle.vue'
import {StationModules} from '@/api/types'
import type {StationModule} from '@/api/generated/schema'
import {STATION_MODULE_OPTIONS} from '@/data/stationModules'

const {t} = useI18n()

/** The modules the example station has switched on. */
const ENABLED = new Set<StationModule>([
  StationModules.INVENTORY,
  StationModules.NEWS,
  StationModules.EVENTS,
  StationModules.ATTENDANCE,
  StationModules.FORMS,
  StationModules.QUIZ,
  StationModules.KNOWLEDGE_BASE,
  StationModules.DOCUMENTS,
])
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
        :model-value="ENABLED.has(mod.value)"
        disabled
      />
    </div>
  </NeutralContainer>
</template>
