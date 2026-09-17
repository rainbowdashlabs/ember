/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FlowDiagram from '@/components/movement/FlowDiagram.vue'
import MovementActionPanel, {type AcknowledgePayload} from '../movementview/MovementActionPanel.vue'
import {inventory, movements} from '@/api'
import type {InventorySize} from '@/api/inventory'
import type {MovementDetail} from '@/api/movements'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {apiErrorMessage} from '@/util/apiError'

/**
 * Acknowledging the step a movement stands on, from the queue rather than from the page about one.
 *
 * <p>The chain is loaded when the modal opens, because the step is what is acknowledged and a row
 * carries only its words. It is also drawn, so whoever presses the button sees where the piece is
 * going rather than only the word on the step.
 */
const props = defineProps<{
  movementId: number
  /** Whether the viewer may override a party that has not answered. */
  canForce: boolean
}>()

const emit = defineEmits<{
  done: []
}>()

const model = defineModel<boolean>({required: true})

const {t} = useI18n()
const {hasPermission} = useSession()

const detail = ref<MovementDetail | null>(null)
const sizes = ref<InventorySize[]>([])
const loading = ref(false)
const busy = ref(false)
const error = ref('')

const currentStep = computed(() => detail.value?.steps.find(step => step.current) ?? null)
const mayRecord = computed(() => detail.value?.movement.ownerAnswersHere === false)
const drawnSteps = computed(() => detail.value?.steps ?? [])

/** Whether the reader may name the arriving piece, which is whether they may read the shelf. */
const mayPick = computed(() => hasPermission(StationPermission.INVENTORY_READ))

async function load() {
  loading.value = true
  error.value = ''
  try {
    detail.value = await movements.getMovement(props.movementId)
    const naming = mayPick.value && detail.value.steps.some(step => step.current && step.picksItem)
    const inventoryId = detail.value.movement.inventoryId
    sizes.value = naming && inventoryId ? await inventory.listSizes(inventoryId) : []
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('common.error')
  } finally {
    loading.value = false
  }
}

watch(model, open => {
  if (open) void load()
}, {immediate: true})

async function run(action: () => Promise<MovementDetail>) {
  busy.value = true
  error.value = ''
  try {
    await action()
    model.value = false
    emit('done')
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('common.error')
  } finally {
    busy.value = false
  }
}

function acknowledge(payload: AcknowledgePayload) {
  const stepId = currentStep.value?.id
  if (!stepId) return
  void run(() => movements.acknowledgeStep(props.movementId, {stepId, ...payload}))
}

function force(payload: AcknowledgePayload) {
  const stepId = currentStep.value?.id
  if (!stepId) return
  void run(() => movements.forceStep(props.movementId, {stepId, ...payload}))
}
</script>

<template>
  <Modal v-model="model">
    <div class="space-y-4" data-testid="movement-ack-modal">
      <SubHeader>{{ t('movements.queue.acknowledge') }}</SubHeader>
      <Spinner v-if="loading"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="detail && currentStep">
        <MutedText tag="p" size="sm">{{ currentStep.label }}</MutedText>
        <FlowDiagram
            :owner-kind="detail.movement.ownerKind"
            :owner-name="detail.movement.ownerName"
            :steps="drawnSteps"
        />
        <MovementActionPanel
            :busy="busy"
            :can-force="props.canForce"
            :inventory-id="detail.movement.inventoryId"
            :inventory-name="detail.movement.inventoryName"
            :inventory-type="detail.movement.inventoryType"
            :may-record="mayRecord"
            :owner-cluster-id="detail.movement.ownerClusterId"
            :owner-kind="detail.movement.ownerKind"
            :sizes="sizes"
            :step="currentStep"
            :wanted-size-id="detail.movement.newSizeId ?? detail.movement.oldSizeId"
            @acknowledge="acknowledge"
            @force="force"
            @decline="reason => run(() => movements.declineMovement(props.movementId, reason))"
            @cancel="reason => run(() => movements.cancelMovement(props.movementId, reason))"
        />
      </template>
    </div>
  </Modal>
</template>
