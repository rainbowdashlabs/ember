/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Alert from '@/components/feedback/Alert.vue'
import FormLabel from '@/components/input/FormLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import FlowCard from '@/views/stationview/inventory/flowview/FlowCard.vue'
import {clusterInventory} from '@/api'
import type {ClusterFlow} from '@/api/clusterInventory'
import {MovementPurpose, type MovementFlow, type StepRequest} from '@/api/movements'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {apiErrorMessage} from '@/util/apiError'

/**
 * The chains the association's own gear walks.
 *
 * <p>The station's flow card, mounted whole: a chain is its steps, and a screen showing only a name and
 * a purpose showed a label for something whose content it never displayed. Bindings are the one thing
 * the association genuinely does without, because a movement of its gear picks the chain by owner and
 * purpose before it ever looks at a binding.
 */
const {t} = useI18n()

const flows = ref<ClusterFlow[]>([])
const busy = ref(false)
const actionError = ref('')

const newName = ref('')
const newPurpose = ref<string>(MovementPurpose.ISSUE)

const {loading, error, reload} = useAsyncLoader(async () => {
  flows.value = await clusterInventory.listFlows()
})

/**
 * The card wants a station's flow. Nobody stands above the association, so nothing here is somebody
 * else's to leave alone, and every chain is editable.
 */
const cards = computed<MovementFlow[]>(() => flows.value.map(flow => ({...flow, ownedByCluster: false})))

/**
 * Runs one change and reloads. Refusals are shown rather than swallowed: a step cannot be changed while
 * a movement is walking the chain, and a second chain for a purpose already covered is refused naming
 * the one in the way.
 */
async function run(action: () => Promise<unknown>) {
  busy.value = true
  actionError.value = ''
  try {
    await action()
    await reload()
  } catch (e) {
    actionError.value = apiErrorMessage(e) ?? t('common.error')
  } finally {
    busy.value = false
  }
}

function createFlow() {
  if (!newName.value.trim()) return
  const name = newName.value.trim()
  void run(async () => {
    await clusterInventory.createFlow(name, newPurpose.value)
    newName.value = ''
  })
}
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="inventory-flow-setting">
    <SectionHeader>{{ t('clusterInventory.flowsTitle') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('clusterInventory.flowsHint') }}</p>
    <Alert v-if="error || actionError" variant="error">{{ error || actionError }}</Alert>

    <EmptyState v-if="!loading && flows.length === 0" compact>{{ t('clusterInventory.flowsEmpty') }}</EmptyState>
    <div v-else class="space-y-2">
      <FlowCard
          v-for="flow in cards"
          :key="flow.id"
          :busy="busy"
          :flow="flow"
          @add-step="(flowId: number, step: StepRequest) => run(() => clusterInventory.addStep(flowId, step))"
          @archive-step="(stepId: number) => run(() => clusterInventory.archiveStep(stepId))"
          @archive-flow="(flowId: number) => run(() => clusterInventory.archiveFlow(flowId))"
      />
    </div>

    <div class="flex flex-wrap items-end gap-2">
      <div class="space-y-1">
        <FormLabel>{{ t('clusterInventory.flowNameLabel') }}</FormLabel>
        <TextInput v-model="newName" :placeholder="t('clusterInventory.flowNamePlaceholder')"
                   data-testid="cluster-flow-name"/>
      </div>
      <div class="space-y-1">
        <FormLabel>{{ t('clusterInventory.flowPurposeLabel') }}</FormLabel>
        <SelectInput v-model="newPurpose" class="w-48" data-testid="cluster-flow-purpose">
          <option v-for="purpose in [MovementPurpose.ISSUE, MovementPurpose.RETURN, MovementPurpose.EXCHANGE]"
                  :key="purpose" :value="purpose">
            {{ t(`movements.purpose.${purpose}`) }}
          </option>
        </SelectInput>
      </div>
      <PrimaryButton :disabled="busy || !newName.trim()" data-testid="cluster-flow-create" @click="createFlow">
        {{ t('common.create') }}
      </PrimaryButton>
    </div>
  </NeutralContainer>
</template>
