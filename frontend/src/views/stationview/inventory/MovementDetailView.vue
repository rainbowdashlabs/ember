/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {inventory, movements} from '@/api'
import type {InventorySize} from '@/api/inventory'
import {MovementState, type MovementDetail} from '@/api/movements'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure, type Failure} from '@/util/failure'
import MovementChain from './movementview/MovementChain.vue'
import type {AcknowledgePayload} from './movementview/MovementActionPanel.vue'
import MovementSummary from './movementview/MovementSummary.vue'
import LossReportPanel from './movementview/LossReportPanel.vue'

const {t} = useI18n()
const route = useRoute()
const {hasPermission} = useSession()

const movementId = computed(() => Number(route.params.id))
const detail = ref<MovementDetail | null>(null)
const sizes = ref<InventorySize[]>([])
const busy = ref(false)
const actionFailure = ref<Failure | null>(null)

/**
 * What set out, at the head of the page: the piece where the movement names one, the inventory it
 * came off otherwise. The kind of movement was standing there, which is the same handful of words
 * above every one of them, so it moves to the line underneath and takes the number with it.
 */
const subject = computed(() => detail.value?.movement.itemName
    || detail.value?.movement.incomingItemName
    || detail.value?.movement.inventoryName
    || '')

const purposeLabel = computed(() =>
    (detail.value ? t(`movements.purpose.${detail.value.movement.purpose}`) : ''))

const pageTitle = computed(() => subject.value || purposeLabel.value || t('movements.chain'))

const pageSubtitle = computed(() => {
  if (!detail.value) return undefined
  const id = detail.value.movement.id
  return subject.value
      ? t('movements.detailSubtitlePurpose', {purpose: purposeLabel.value, id})
      : t('movements.detailSubtitle', {id})
})

const isManager = computed(() => hasPermission(StationPermission.INVENTORY_MANAGER))
const open = computed(() => detail.value?.movement.state === MovementState.OPEN)
const currentStep = computed(() => detail.value?.steps.find(s => s.current) ?? null)

/**
 * Whether the piece that arrives may be written down here rather than picked.
 *
 * <p>Only where the owner is a body outside Ember. One that keeps its gear here names what it sends,
 * and a second row written by the station for the same piece would be one thing with two records.
 */
const mayRecord = computed(() => detail.value?.movement.ownerAnswersHere === false)

/** Whether the reader may name the arriving piece, which is whether they may read the shelf. */
const mayPick = computed(() => hasPermission(StationPermission.INVENTORY_READ))

const {loading, failure, reload} = useAsyncLoader(async () => {
  detail.value = await movements.getMovement(movementId.value)
  const naming = mayPick.value && detail.value.steps.some(s => s.current && s.picksItem)
  sizes.value = naming ? await sizesOfInventory() : []
})

/**
 * The sizes of the inventory a written-down piece lands in, so it is recorded like any other.
 *
 * <p>Fetched whenever the current step names a piece and the reader may name it, rather than only
 * where writing one down is allowed. Which of the two applies depends on the movement's owner, and
 * reading that first made the sizes arrive a moment too late for the form that needs them.
 */
async function sizesOfInventory(): Promise<InventorySize[]> {
  const inventoryId = detail.value?.movement.inventoryId
  if (!inventoryId) return []
  try {
    return await inventory.listSizes(inventoryId)
  } catch {
    return []
  }
}


/**
 * Answers the step the movement stands on, then reads the movement back.
 *
 * <p>The answer and the reading back are caught apart. Once the step is acknowledged it is acknowledged,
 * and a reader told otherwise answers it again on a movement that has already walked on.
 */
async function run(action: () => Promise<MovementDetail>) {
  busy.value = true
  actionFailure.value = null
  try {
    detail.value = await action()
  } catch (e) {
    actionFailure.value = describeFailure(e, t)
    busy.value = false
    return
  }

  await reload()
  if (failure.value) {
    actionFailure.value = {...failure.value, message: t('failure.staleAfterAction')}
    failure.value = null
  }
  busy.value = false
}

function acknowledge(payload: AcknowledgePayload) {
  const stepId = currentStep.value?.id
  if (!stepId) return
  void run(() => movements.acknowledgeStep(movementId.value, {stepId, ...payload}))
}

function refuse(reported: Failure | null) {
  actionFailure.value = reported
}

function force(payload: AcknowledgePayload) {
  const stepId = currentStep.value?.id
  if (!stepId) return
  void run(() => movements.forceStep(movementId.value, {stepId, ...payload}))
}
</script>

<template>
  <ViewContent
      :subtitle="pageSubtitle"
      :title="pageTitle"
  >
    <Spinner v-if="loading"/>
    <FailureAlert v-else-if="failure" :failure="failure"/>

    <div v-else-if="detail" class="space-y-4">
      <MovementSummary :movement="detail.movement"/>

      <LossReportPanel v-if="detail.lossReport" :movement-id="movementId" :report="detail.lossReport"/>

      <MovementChain
          :busy="busy"
          :detail="detail"
          :failure="actionFailure"
          :is-manager="isManager"
          :may-record="mayRecord"
          :open="open"
          :sizes="sizes"
          @acknowledge="acknowledge"
          @force="force"
          @decline="reason => run(() => movements.declineMovement(movementId, reason))"
          @cancel="reason => run(() => movements.cancelMovement(movementId, reason))"
          @rechain="stepIndex => run(() => movements.rechain(movementId, stepIndex))"
          @refused="refuse"
      />
    </div>
  </ViewContent>
</template>
