/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FormLabel from '@/components/input/FormLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import InventoryTabs from './clusterinventoryview/InventoryTabs.vue'
import {clusterInventory, clusters} from '@/api'
import type {ClusterFlow} from '@/api/clusterInventory'
import {ClusterPermission} from '@/api/clusters'
import {MovementPurpose} from '@/api/movements'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useSession} from '@/composables/useSession'

/**
 * What the association decides about gear, rather than what it owns.
 *
 * <p>Two settings guarded by two different rights, so the page shows whichever of them the reader
 * holds. Whether the association keeps gear at all is a module question and belongs to whoever
 * decides modules; the shapes a movement walks belong to whoever looks after the gear.
 */
const {t} = useI18n()
const {hasClusterPermission} = useSession()

const canSetModule = hasClusterPermission(ClusterPermission.CLUSTER_MODULES)
const canSetFlows = hasClusterPermission(ClusterPermission.CLUSTER_INVENTORY_MANAGER)

const usesInventory = ref(false)
const flows = ref<ClusterFlow[]>([])
const newName = ref('')
const newPurpose = ref<string>(MovementPurpose.ISSUE)

const {loading, error} = useAsyncLoader(async () => {
  if (canSetModule) {
    const active = await clusters.getActive()
    usesInventory.value = active.usesInventory ?? false
  }
  if (canSetFlows) flows.value = await clusterInventory.listFlows()
})

const {running: busy, error: writeError, run: toggleUses} = useAsyncAction(async (value: boolean) => {
  await clusterInventory.setUsesInventory(value)
  usesInventory.value = value
})

const {running: creating, run: addFlow} = useAsyncAction(async () => {
  if (!newName.value.trim()) return
  await clusterInventory.createFlow(newName.value.trim(), newPurpose.value)
  newName.value = ''
  flows.value = await clusterInventory.listFlows()
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
        <NeutralContainer v-if="canSetModule" class="space-y-3">
          <SectionHeader>{{ t('clusterInventory.usesTitle') }}</SectionHeader>
          <div class="flex items-start justify-between gap-4">
            <p class="text-sm text-(--text-muted)">{{ t('clusterInventory.usesHint') }}</p>
            <ToggleInput :disabled="busy" :model-value="usesInventory" @update:model-value="toggleUses"/>
          </div>
        </NeutralContainer>

        <NeutralContainer v-if="canSetFlows" class="space-y-4">
          <SectionHeader>{{ t('clusterInventory.flowsTitle') }}</SectionHeader>
          <p class="text-sm text-(--text-muted)">{{ t('clusterInventory.flowsHint') }}</p>

          <EmptyState v-if="flows.length === 0" compact>{{ t('clusterInventory.flowsEmpty') }}</EmptyState>
          <div v-else class="space-y-1">
            <div v-for="flow in flows" :key="flow.id"
                 class="flex items-center justify-between border-b border-(--border) py-1 last:border-0">
              <span class="font-medium">{{ flow.name }}</span>
              <span class="text-sm text-(--text-muted)">{{ t(`movements.purpose.${flow.purpose}`) }}</span>
            </div>
          </div>

          <div class="flex flex-wrap items-end gap-2">
            <div class="space-y-1">
              <FormLabel>{{ t('clusterInventory.flowNameLabel') }}</FormLabel>
              <TextInput v-model="newName" :placeholder="t('clusterInventory.flowNamePlaceholder')"/>
            </div>
            <div class="space-y-1">
              <FormLabel>{{ t('clusterInventory.flowPurposeLabel') }}</FormLabel>
              <SelectInput v-model="newPurpose" class="w-48">
                <option v-for="purpose in [MovementPurpose.ISSUE, MovementPurpose.RETURN, MovementPurpose.EXCHANGE]"
                        :key="purpose" :value="purpose">
                  {{ t(`movements.purpose.${purpose}`) }}
                </option>
              </SelectInput>
            </div>
            <PrimaryButton :disabled="creating || !newName.trim()" @click="addFlow">
              {{ t('common.create') }}
            </PrimaryButton>
          </div>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
