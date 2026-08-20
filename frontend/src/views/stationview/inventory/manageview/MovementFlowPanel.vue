/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {movements} from '@/api'
import {
  MovementPurpose,
  type MovementFlow,
  type MovementFlowBinding,
  type MovementPurposeName,
  type StepRequest,
} from '@/api/movements'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {apiErrorMessage} from '@/util/apiError'
import FlowCard from '../flowview/FlowCard.vue'
import FlowBindingTable from '../flowview/FlowBindingTable.vue'

const {t} = useI18n()

const flows = ref<MovementFlow[]>([])
const bindings = ref<MovementFlowBinding[]>([])
const busy = ref(false)
const actionError = ref('')

const newName = ref('')
const newPurpose = ref<MovementPurposeName>(MovementPurpose.EXCHANGE)

const {loading, error, reload} = useAsyncLoader(async () => {
  ;[flows.value, bindings.value] = await Promise.all([movements.listFlows(), movements.listBindings()])
})

/**
 * Runs one change and reloads. Refusals are shown rather than swallowed: a step cannot be edited
 * while a movement is walking the flow, and the reader needs to be told which is in the way.
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
    await movements.createFlow({name, purpose: newPurpose.value})
    newName.value = ''
  })
}
</script>

<template>
  <div class="space-y-3">
    <SectionHeader>{{ t('flows.title') }}</SectionHeader>
    <MutedText size="sm" tag="p">{{ t('flows.intro') }}</MutedText>

    <Spinner v-if="loading"/>
    <Alert v-else-if="error" variant="error">{{ error }}</Alert>

    <template v-else>
      <Alert v-if="actionError" variant="error">{{ actionError }}</Alert>

      <FlowBindingTable
          :bindings="bindings"
          :busy="busy"
          :flows="flows"
          @rebind="(binding, flowId) => run(() => movements.bindFlow({...binding, flowId}))"
      />

      <div class="space-y-2">
        <FlowCard
            v-for="flow in flows"
            :key="flow.id"
            :busy="busy"
            :flow="flow"
            @add-step="(flowId: number, step: StepRequest) => run(() => movements.addStep(flowId, step))"
            @archive-step="(stepId: number) => run(() => movements.archiveStep(stepId))"
            @archive-flow="(flowId: number) => run(() => movements.archiveFlow(flowId))"
        />
      </div>

      <div class="flex flex-wrap items-end gap-2">
        <div class="space-y-1">
          <FieldLabel>{{ t('flows.newFlow') }}</FieldLabel>
          <TextInput v-model="newName" :placeholder="t('flows.newFlowPlaceholder')"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('flows.purpose') }}</FieldLabel>
          <SelectInput v-model="newPurpose">
            <option
                v-for="value in [MovementPurpose.EXCHANGE, MovementPurpose.RETURN, MovementPurpose.ISSUE]"
                :key="value"
                :value="value"
            >
              {{ t(`movements.purpose.${value}`) }}
            </option>
          </SelectInput>
        </div>
        <PrimaryButton :disabled="busy || !newName.trim()" @click="createFlow">{{ t('flows.create') }}</PrimaryButton>
      </div>
    </template>
  </div>
</template>
