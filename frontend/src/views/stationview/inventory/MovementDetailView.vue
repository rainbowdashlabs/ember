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
import {isAvailable, type InventoryItem} from '@/api/inventory'
import {MovementState, type MovementDetail} from '@/api/movements'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {apiErrorMessage} from '@/util/apiError'
import MovementStep from './movementview/MovementStep.vue'
import MovementActionPanel from './movementview/MovementActionPanel.vue'
import LossReportPanel from './movementview/LossReportPanel.vue'

const {t} = useI18n()
const route = useRoute()
const {hasPermission} = useSession()

const movementId = computed(() => Number(route.params.id))
const detail = ref<MovementDetail | null>(null)
const candidates = ref<InventoryItem[]>([])
const busy = ref(false)
const actionError = ref('')

const canForce = computed(() => hasPermission(StationPermission.INVENTORY_MANAGER))
const open = computed(() => detail.value?.movement.state === MovementState.OPEN)
const currentStep = computed(() => detail.value?.steps.find(s => s.current) ?? null)

/** Retired steps only belong on the chain when this movement actually walked through one. */
const visibleSteps = computed(() =>
    (detail.value?.steps ?? []).filter(step => !step.archived || step.ackKind || step.current))

const {loading, error, reload} = useAsyncLoader(async () => {
  detail.value = await movements.getMovement(movementId.value)
  const inventoryId = detail.value.movement.inventoryId
  // Only the step that names the arriving item needs something to choose from, and only gear the
  // station is actually holding can be handed over
  candidates.value = inventoryId && detail.value.steps.some(s => s.current && s.picksItem)
      ? (await inventory.listItems(inventoryId)).filter(item => isAvailable(item.custody))
      : []
})

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

function acknowledge(payload: {note: string; pickedItemId: number | null}) {
  const stepId = currentStep.value?.id
  if (!stepId) return
  void run(() => movements.acknowledgeStep(movementId.value, {stepId, ...payload}))
}

function force(payload: {note: string; pickedItemId: number | null}) {
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
                :candidates="candidates"
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
