/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import StationModuleToggle from '@/components/modules/StationModuleToggle.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {STATION_MODULE_OPTIONS} from '@/data/stationModules'
import {clusterGovernance, clusterStationGroups} from '@/api'
import type {StationGroupResponse} from '@/api/generated/schema'
import {useConfigPanel} from '@/composables/useConfigPanel'

const {t} = useI18n()

const busy = ref(false)
const saved = ref(false)

/**
 * Which stations the switches below are about: all of them, or one group.
 *
 * <p>An association that files nothing sees exactly the screen it saw before this row existed. The
 * tabs are independent of one another: saving what is denied of everybody leaves what is denied of a
 * group exactly where it was, because denials add up rather than replacing each other.
 */
const groups = ref<StationGroupResponse[]>([])
const stationGroupId = ref<number | null>(null)

const tabs = computed(() => [
  {key: '', label: t('clusterModules.everyStation')},
  ...groups.value.map(group => ({key: String(group.id), label: group.name})),
])

const activeTab = computed({
  get: () => stationGroupId.value === null ? '' : String(stationGroupId.value),
  set: (key: string) => { stationGroupId.value = key ? Number(key) : null },
})

const {config: denied, loading, failure, runWith} = useConfigPanel<string[]>({
  initial: [],
  fetch: async () => {
    groups.value = await clusterStationGroups.listGroups()
    return (await clusterGovernance.getDeniedModules(stationGroupId.value)).deniedModules
  },
})

watch(stationGroupId, () => {
  saved.value = false
  void runWith(async () => (await clusterGovernance.getDeniedModules(stationGroupId.value)).deniedModules, {busy})
})

function allowed(module: string): boolean {
  return !denied.value.includes(module)
}

function toggle(module: string, allow: boolean) {
  denied.value = allow ? denied.value.filter(m => m !== module) : [...denied.value, module]
}

async function save() {
  saved.value = false
  await runWith(async () => {
    await clusterGovernance.setDeniedModules(denied.value, stationGroupId.value)
    saved.value = true
    return (await clusterGovernance.getDeniedModules(stationGroupId.value)).deniedModules
  }, {busy})
}
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-modules.subtitle')" :title="t('pages.cluster-modules.title')">
    <div class="space-y-4">
      <FailureAlert :failure="failure"/>
      <Alert v-if="saved" variant="success">{{ t('clusterModules.saved') }}</Alert>

      <Spinner v-if="loading" size="lg"/>

      <template v-else>
        <p class="text-sm text-(--text-muted)">{{ t('clusterModules.hint') }}</p>

        <TabBar v-if="groups.length > 0" v-model="activeTab" :tabs="tabs"/>
        <p v-if="groups.length > 0 && stationGroupId !== null" class="text-sm text-(--text-muted)">
          {{ t('clusterModules.groupHint') }}
        </p>

        <NeutralContainer class="space-y-3" data-testid="cluster-module-switches">
          <StationModuleToggle
              v-for="mod in STATION_MODULE_OPTIONS"
              :key="mod.value"
              :module="mod"
              :model-value="allowed(mod.value)"
              @update:model-value="v => toggle(mod.value, v)"
          />
        </NeutralContainer>

        <PrimaryButton :disabled="busy" @click="save">{{ t('common.save') }}</PrimaryButton>
      </template>
    </div>
  </ViewContent>
</template>
