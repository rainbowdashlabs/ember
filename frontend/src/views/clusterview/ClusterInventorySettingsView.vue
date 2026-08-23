/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import InventoryTabs from './clusterinventoryview/InventoryTabs.vue'
import FlowSettingsPanel from './clusterinventoryview/FlowSettingsPanel.vue'
import LossReportSettingPanel from './clusterinventoryview/LossReportSettingPanel.vue'
import {clusterInventory, clusters} from '@/api'
import {ClusterPermission} from '@/api/clusters'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useSession} from '@/composables/useSession'

/**
 * What the association decides about gear, rather than what it owns.
 *
 * <p>Settings guarded by two different rights, so the page shows whichever of them the reader holds.
 * Whether the association keeps gear at all is a module question and belongs to whoever decides modules;
 * the shapes a movement walks and what a loss report has to carry belong to whoever looks after the gear.
 */
const {t} = useI18n()
const {hasClusterPermission} = useSession()

/**
 * Read as a computed, not as a value taken once.
 *
 * <p>What somebody holds at the association arrives with the session, and a screen can be set up
 * before it has. Taken once, both of these would be false for good and the page would show nothing
 * at all to somebody who may set both.
 */
const canSetModule = computed(() => hasClusterPermission(ClusterPermission.CLUSTER_MODULES))
const canSetFlows = computed(() => hasClusterPermission(ClusterPermission.CLUSTER_INVENTORY_MANAGER))

const usesInventory = ref(false)

const {loading, error} = useAsyncLoader(async () => {
  if (!canSetModule.value) return
  const active = await clusters.getActive()
  usesInventory.value = active.usesInventory ?? false
})

const {running: busy, error: writeError, run: toggleUses} = useAsyncAction(async (value: boolean) => {
  await clusterInventory.setUsesInventory(value)
  usesInventory.value = value
})
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-inventory-settings.subtitle')"
               :title="t('pages.cluster-inventory-settings.title')">
    <div class="space-y-6">
      <InventoryTabs/>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error || writeError" variant="error">{{ error || writeError }}</Alert>

      <template v-if="!loading">
        <NeutralContainer v-if="canSetModule" data-testid="inventory-module-setting" class="space-y-3">
          <SectionHeader>{{ t('clusterInventory.usesTitle') }}</SectionHeader>
          <div class="flex items-start justify-between gap-4">
            <p class="text-sm text-(--text-muted)">{{ t('clusterInventory.usesHint') }}</p>
            <ToggleInput :disabled="busy" :model-value="usesInventory" @update:model-value="toggleUses"/>
          </div>
        </NeutralContainer>

        <FlowSettingsPanel v-if="canSetFlows"/>

        <LossReportSettingPanel v-if="canSetFlows"/>
      </template>
    </div>
  </ViewContent>
</template>
