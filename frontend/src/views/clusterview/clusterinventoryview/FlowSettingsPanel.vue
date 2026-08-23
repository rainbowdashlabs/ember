/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Alert from '@/components/feedback/Alert.vue'
import FormLabel from '@/components/input/FormLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {clusterInventory} from '@/api'
import type {ClusterFlow} from '@/api/clusterInventory'
import {MovementPurpose} from '@/api/movements'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'

/** The chains the association's own gear walks, named and given a purpose here. */
const {t} = useI18n()

const flows = ref<ClusterFlow[]>([])
const newName = ref('')
const newPurpose = ref<string>(MovementPurpose.ISSUE)

const {loading, error} = useAsyncLoader(async () => {
  flows.value = await clusterInventory.listFlows()
})

const {running: creating, error: createError, run: addFlow} = useAsyncAction(async () => {
  if (!newName.value.trim()) return
  await clusterInventory.createFlow(newName.value.trim(), newPurpose.value)
  newName.value = ''
  flows.value = await clusterInventory.listFlows()
})
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="inventory-flow-setting">
    <SectionHeader>{{ t('clusterInventory.flowsTitle') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('clusterInventory.flowsHint') }}</p>
    <Alert v-if="error || createError" variant="error">{{ error || createError }}</Alert>

    <EmptyState v-if="!loading && flows.length === 0" compact>{{ t('clusterInventory.flowsEmpty') }}</EmptyState>
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
