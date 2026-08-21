/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import ClusterItemRow from './clusterinventoryview/ClusterItemRow.vue'
import {clusterInventory, clusters} from '@/api'
import type {ClusterItem} from '@/api/clusterInventory'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useSession} from '@/composables/useSession'
import {ClusterPermission} from '@/api/clusters'

const {t} = useI18n()
const {hasClusterPermission} = useSession()

const busy = ref(false)
const usesInventory = ref(false)

const {config: items, loading, error, runWith} = useConfigPanel<ClusterItem[]>({
  initial: [],
  fetch: async () => {
    usesInventory.value = (await clusters.getActive()).usesInventory ?? false
    return clusterInventory.listItems()
  },
})

async function toggleUses(value: boolean) {
  await runWith(async () => {
    await clusterInventory.setUsesInventory(value)
    usesInventory.value = value
    return clusterInventory.listItems()
  }, {busy})
}
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-inventory.subtitle')" :title="t('pages.cluster-inventory.title')">
    <div class="space-y-4">
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <NeutralContainer v-if="hasClusterPermission(ClusterPermission.CLUSTER_MODULES)" class="space-y-3">
        <SectionHeader>{{ t('clusterInventory.usesTitle') }}</SectionHeader>
        <div class="flex items-start justify-between gap-4">
          <p class="text-sm text-(--text-muted)">{{ t('clusterInventory.usesHint') }}</p>
          <ToggleInput :disabled="busy" :model-value="usesInventory" @update:model-value="toggleUses"/>
        </div>
      </NeutralContainer>

      <Spinner v-if="loading" size="lg"/>

      <template v-else>
        <EmptyState v-if="items.length === 0">{{ t('clusterInventory.empty') }}</EmptyState>
        <div v-else class="space-y-2">
          <ClusterItemRow v-for="item in items" :key="item.id" :item="item"/>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
