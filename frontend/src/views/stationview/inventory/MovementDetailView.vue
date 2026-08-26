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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import {inventory, movements} from '@/api'
import type {InventorySize} from '@/api/inventory'
import {MovementState, type MovementDetail} from '@/api/movements'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {apiErrorMessage} from '@/util/apiError'
import MovementStep from './movementview/MovementStep.vue'
import MovementActionPanel, {type AcknowledgePayload} from './movementview/MovementActionPanel.vue'
import LossReportPanel from './movementview/LossReportPanel.vue'

const {t} = useI18n()
const route = useRoute()
const {hasPermission} = useSession()

const movementId = computed(() => Number(route.params.id))
const detail = ref<MovementDetail | null>(null)
const sizes = ref<InventorySize[]>([])
const busy = ref(false)
const actionError = ref('')

const canForce = computed(() => hasPermission(StationPermission.INVENTORY_MANAGER))
const open = computed(() => detail.value?.movement.state === MovementState.OPEN)
const currentStep = computed(() => detail.value?.steps.find(s => s.current) ?? null)

/** Retired steps only belong on the chain when this movement actually walked through one. */
const visibleSteps = computed(() =>
    (detail.value?.steps ?? []).filter(step => !step.archived || step.ackKind || step.current))

/**
 * Whether the piece that arrives may be written down here rather than picked.
 *
 * <p>Only where the owner is a body outside Ember. One that keeps its gear here names what it sends,
 * and a second row written by the station for the same piece would be one thing with two records.
 */
const mayRecord = computed(() => detail.value?.movement.ownerAnswersHere === false)

const {loading, error, reload} = useAsyncLoader(async () => {
  detail.value = await movements.getMovement(movementId.value)
  const naming = detail.value.steps.some(s => s.current && s.picksItem)
  sizes.value = naming ? await sizesOfInventory() : []
})

/**
 * The sizes of the inventory a written-down piece lands in, so it is recorded like any other.
 *
 * <p>Fetched whenever the current step names a piece, rather than only where writing one down is
 * allowed. Which of the two applies depends on the movement's owner, and reading that first made the
 * sizes arrive a moment too late for the form that needs them.
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


async function run(action: () => Promise<MovementDetail>) {
  busy.value = true
  actionError.value = ''
  try {
    detail.value = await action()
    await reload()
  } catch (e) {
    actionError.value = apiErrorMessage(e) ?? t('common.error')
  } finally {
    busy.value = false
  }
}

function acknowledge(payload: AcknowledgePayload) {
  const stepId = currentStep.value?.id
  if (!stepId) return
  void run(() => movements.acknowledgeStep(movementId.value, {stepId, ...payload}))
}

function force(payload: AcknowledgePayload) {
  const stepId = currentStep.value?.id
  if (!stepId) return
  void run(() => movements.forceStep(movementId.value, {stepId, ...payload}))
}
</script>

<template>
  <ViewContent
      :subtitle="detail ? t('movements.detailSubtitle', {id: detail.movement.id}) : undefined"
      :title="detail ? t(`movements.purpose.${detail.movement.purpose}`) : t('movements.chain')"
  >
    <Spinner v-if="loading"/>
    <Alert v-else-if="error" variant="error">{{ error }}</Alert>

    <div v-else-if="detail" class="space-y-4">
      <NeutralContainer class="space-y-1 text-sm">
        <div v-if="detail.movement.memberIdentity" class="flex items-center gap-2">
          <span class="text-(--text-muted)">{{ t('movements.member') }}</span>
          <MemberName :identity="detail.movement.memberIdentity"/>
        </div>
        <div v-if="detail.movement.inventoryName">
          <span class="text-(--text-muted)">{{ t('movements.inventory') }}</span>
          {{ detail.movement.inventoryName }}
        </div>
        <div v-if="detail.movement.reason">
          <span class="text-(--text-muted)">{{ t('movements.reason') }}</span>
          {{ detail.movement.reason }}
        </div>
        <div v-if="detail.movement.closeReason">
          <span class="text-(--text-muted)">{{ t('movements.closeReason') }}</span>
          {{ detail.movement.closeReason }}
        </div>
        <div v-if="!open">
          <span class="text-(--text-muted)">{{ t('movements.stateLabel') }}</span>
          {{ t(`movements.state.${detail.movement.state}`) }}
        </div>
      </NeutralContainer>

      <LossReportPanel v-if="detail.lossReport" :movement-id="movementId" :report="detail.lossReport"/>

      <div>
        <SubHeader class="mb-2">{{ t('movements.chain') }}</SubHeader>
        <Alert v-if="actionError" variant="error" class="mb-2">{{ actionError }}</Alert>
        <MovementStep v-for="step in visibleSteps" :key="step.id" :step="step" :open="open">
          <template #action>
            <MovementActionPanel
                v-if="step.current && open"
                :busy="busy"
                :can-force="canForce"
                :may-record="mayRecord"
                :sizes="sizes"
                :wanted-size-id="detail.movement.newSizeId ?? detail.movement.oldSizeId"
                :step="step"
                @acknowledge="acknowledge"
                @force="force"
                @decline="reason => run(() => movements.declineMovement(movementId, reason))"
                @cancel="reason => run(() => movements.cancelMovement(movementId, reason))"
            />
          </template>
        </MovementStep>
      </div>
    </div>
  </ViewContent>
</template>
