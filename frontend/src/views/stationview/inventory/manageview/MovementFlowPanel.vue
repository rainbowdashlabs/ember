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
import {useFlowProblems} from '@/composables/useFlowProblems'
import FlowCard from '../flowview/FlowCard.vue'
import FlowBindingTable from '../flowview/FlowBindingTable.vue'

const {t} = useI18n()
const {refusalText} = useFlowProblems()

const flows = ref<MovementFlow[]>([])
const bindings = ref<MovementFlowBinding[]>([])
const busy = ref(false)
const actionError = ref('')
const flowErrors = ref<Record<number, string>>({})

const newName = ref('')
const newPurpose = ref<MovementPurposeName>(MovementPurpose.EXCHANGE)

const {loading, error, reload} = useAsyncLoader(async () => {
  ;[flows.value, bindings.value] = await Promise.all([movements.listFlows(), movements.listBindings()])
})

/**
 * Runs one change to one chain and puts the answer in its place.
 *
 * <p>The chain comes back as it now stands, so the card is replaced and nothing else on the page
 * moves. Fetching the whole page after every saved step threw the reader back to the top with every
 * chain closed again, which made editing a chain of eight steps a page load per step.
 *
 * <p>A refusal is shown on the chain it was about rather than at the top of the page, where the
 * reader is not looking: a step cannot be edited while a movement is walking the chain, and that
 * belongs next to the step.
 */
async function runOnFlow(flowId: number, action: () => Promise<MovementFlow>) {
  busy.value = true
  flowErrors.value = {...flowErrors.value, [flowId]: ''}
  try {
    replace(await action())
  } catch (e) {
    flowErrors.value = {...flowErrors.value, [flowId]: refusalText(e)}
  } finally {
    busy.value = false
  }
}

function replace(flow: MovementFlow) {
  flows.value = flows.value.map(known => (known.id === flow.id ? flow : known))
}

/**
 * Adds a step and reads the chain back.
 *
 * <p>Two calls because adding answers with the step that was created, which is what a caller asking
 * for a step is owed. What the card shows is the whole chain, including whether it can be walked at
 * all, and that is only known once the step is in it.
 */
async function addStep(flowId: number, step: StepRequest): Promise<MovementFlow> {
  await movements.addStep(flowId, step)
  return movements.getFlow(flowId)
}

/** A change that is not about one chain: creating one, or pointing a binding somewhere else. */
async function run(action: () => Promise<unknown>) {
  busy.value = true
  actionError.value = ''
  try {
    await action()
    await reload()
  } catch (e) {
    actionError.value = refusalText(e)
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
            :error="flowErrors[flow.id]"
            :flow="flow"
            @add-step="(flowId: number, step: StepRequest) => runOnFlow(flowId, () => addStep(flowId, step))"
            @archive-step="(stepId: number) => runOnFlow(flow.id, () => movements.archiveStep(stepId))"
            @archive-flow="(flowId: number) => runOnFlow(flowId, () => movements.archiveFlow(flowId))"
            @save-step="(stepId: number, step: StepRequest) => runOnFlow(flow.id, () => movements.updateStep(stepId, step))"
            @reorder="(flowId: number, stepIds: number[]) => runOnFlow(flowId, () => movements.reorderSteps(flowId, stepIds))"
        />
      </div>

      <div class="flex flex-wrap items-end gap-2">
        <div class="w-full space-y-1 sm:w-64">
          <FieldLabel>{{ t('flows.newFlow') }}</FieldLabel>
          <TextInput v-model="newName" :placeholder="t('flows.newFlowPlaceholder')" class="w-full"/>
        </div>
        <div class="w-full space-y-1 sm:w-64">
          <FieldLabel>{{ t('flows.purpose') }}</FieldLabel>
          <SelectInput v-model="newPurpose" class="w-full">
            <option
                v-for="value in [MovementPurpose.EXCHANGE, MovementPurpose.RETURN,
                                 MovementPurpose.ISSUE, MovementPurpose.REQUEST]"
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
