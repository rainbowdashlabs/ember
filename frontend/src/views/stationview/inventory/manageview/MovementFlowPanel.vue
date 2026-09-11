/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
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
  type FlowStepMapping,
  type MovementFlow,
  type MovementFlowBinding,
  type MovementPurposeName,
  type StepRequest,
} from '@/api/movements'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useFlowProblems} from '@/composables/useFlowProblems'
import FlowCard from '../flowview/FlowCard.vue'

const {t} = useI18n()
const {refusalText} = useFlowProblems()

const flows = ref<MovementFlow[]>([])
const bindings = ref<MovementFlowBinding[]>([])
const busy = ref(false)
const actionError = ref('')
const flowErrors = ref<Record<number, string>>({})

/** The purposes in the order a piece of gear meets them, which is also the order the page reads in. */
const PURPOSES: MovementPurposeName[] = [
  MovementPurpose.ISSUE,
  MovementPurpose.EXCHANGE,
  MovementPurpose.RETURN,
  MovementPurpose.REQUEST,
]

const newName = ref('')
const newPurpose = ref<MovementPurposeName>(MovementPurpose.EXCHANGE)

const {loading, error, reload} = useAsyncLoader(async () => {
  ;[flows.value, bindings.value] = await Promise.all([movements.listFlows(), movements.listBindings()])
})

/**
 * Which combination each chain serves.
 *
 * <p>A combination has exactly one chain, so the binding reads as a property of the chain and belongs
 * on its card. Where a chain serves one inventory as well as the general case, the general one is what
 * the card names, since that is the wider of the two answers.
 */
const bindingOf = computed(() => {
  const found = new Map<number, MovementFlowBinding>()
  for (const binding of bindings.value) {
    const known = found.get(binding.flowId)
    if (!known || (known.inventoryId != null && binding.inventoryId == null)) found.set(binding.flowId, binding)
  }
  return found
})

/**
 * The chains under the purpose they serve.
 *
 * <p>One long run of cards says nothing about which of them belong together, and the purpose is the
 * one thing a reader looking for a chain already knows.
 */
const groups = computed(() =>
    PURPOSES
        .map(purpose => ({purpose, flows: flows.value.filter(flow => flow.purpose === purpose)}))
        .filter(group => group.flows.length > 0)
)

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

/** A refusal a card worded for itself, put in the same place as the ones this panel words. */
function showFlowError(flowId: number, message: string) {
  flowErrors.value = {...flowErrors.value, [flowId]: message}
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

/** A change that is not about one chain, which leaves only writing a new one. */
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

      <div v-for="group in groups" :key="group.purpose" class="space-y-2">
        <SubHeader>{{ t(`movements.purpose.${group.purpose}`) }}</SubHeader>
        <FlowCard
            v-for="flow in group.flows"
            :key="flow.id"
            :binding="bindingOf.get(flow.id)"
            :busy="busy"
            :error="flowErrors[flow.id]"
            :flow="flow"
            @add-step="(flowId: number, step: StepRequest) => runOnFlow(flowId, () => addStep(flowId, step))"
            @archive-step="(stepId: number) => runOnFlow(flow.id, () => movements.archiveStep(stepId))"
            @archive-flow="(flowId: number) => runOnFlow(flowId, () => movements.archiveFlow(flowId))"
            @save-step="(stepId: number, step: StepRequest) => runOnFlow(flow.id, () => movements.updateStep(stepId, step))"
            @reorder="(flowId: number, stepIds: number[]) => runOnFlow(flowId, () => movements.reorderSteps(flowId, stepIds))"
            @restore="(flowId: number, mappings: FlowStepMapping[]) => runOnFlow(flowId, () => movements.restoreFlow(flowId, mappings))"
            @restore-refused="showFlowError"
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
            <option v-for="value in PURPOSES" :key="value" :value="value">
              {{ t(`movements.purpose.${value}`) }}
            </option>
          </SelectInput>
        </div>
        <PrimaryButton :disabled="busy || !newName.trim()" @click="createFlow">{{ t('flows.create') }}</PrimaryButton>
      </div>
    </template>
  </div>
</template>
