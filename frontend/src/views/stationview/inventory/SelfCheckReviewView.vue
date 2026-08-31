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
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ReviewRowCard from './selfcheckreviewview/ReviewRowCard.vue'
import RefuseRowModal from './selfcheckreviewview/RefuseRowModal.vue'
import CorrectItemModal from './checkmemberview/CorrectItemModal.vue'
import {selfChecks} from '@/api'
import type {CorrectItemRequest} from '@/api/inventoryCheck'
import type {InventoryItem, RequiredInventoryItem} from '@/api/inventory'
import type {SelfCheckReview, SelfCheckReviewRow} from '@/api/selfChecks'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {formatDate} from '@/util/format'

const {t} = useI18n()
const route = useRoute()

const taskId = computed(() => Number(route.params.id))

const {config: review, loading, error} = useConfigPanel<SelfCheckReview | null>({
  initial: null,
  fetch: () => selfChecks.readReview(taskId.value),
})

const busy = ref(false)

/** The kind of gear one answer is about, which is what a correction has to be told. */
function requirementOf(row: SelfCheckReviewRow): RequiredInventoryItem | null {
  return review.value?.required.find(req => req.inventoryId === row.row.inventoryId) ?? null
}

function freeStockOf(inventoryId: number): InventoryItem[] {
  return review.value?.freeStock[inventoryId] ?? []
}

function itemLabel(item: InventoryItem, req: RequiredInventoryItem): string {
  const size = item.sizeId == null ? '' : (req.sizes.find(s => s.id === item.sizeId)?.label ?? '')
  return [item.name, item.internalId, size].filter(Boolean).join(' - ')
}

async function settle(action: () => Promise<SelfCheckReview>) {
  busy.value = true
  error.value = ''
  try {
    review.value = await action()
  } finally {
    busy.value = false
  }
}

const take = (rowId: number) => settle(() => selfChecks.takeRow(taskId.value, rowId))

const correcting = ref<SelfCheckReviewRow | null>(null)
const showCorrect = ref(false)

function openCorrect(row: SelfCheckReviewRow) {
  correcting.value = row
  clearCorrectError()
  showCorrect.value = true
}

const {running: correctBusy, error: correctError, run: applyCorrection, clearError: clearCorrectError} =
    useAsyncAction(async (payload: CorrectItemRequest) => {
      const row = correcting.value
      if (!row) return
      review.value = await selfChecks.correctRow(taskId.value, row.row.id, {
        inventoryId: payload.inventoryId,
        pickedItemId: payload.pickedItemId,
        sizeId: payload.sizeId,
        ownerKind: payload.ownerKind,
        internalId: payload.internalId,
        metadata: payload.metadata,
      })
      showCorrect.value = false
    })

const refusing = ref<SelfCheckReviewRow | null>(null)
const showRefuse = ref(false)
const refuseReason = ref('')

function openRefuse(row: SelfCheckReviewRow) {
  refusing.value = row
  refuseReason.value = ''
  clearRefuseError()
  showRefuse.value = true
}

const {running: refuseBusy, error: refuseError, run: applyRefusal, clearError: clearRefuseError} =
    useAsyncAction(async () => {
      const row = refusing.value
      if (!row) return
      review.value = await selfChecks.refuseRow(taskId.value, row.row.id, refuseReason.value.trim())
      showRefuse.value = false
    })

const outstanding = computed(() => (review.value?.rows ?? []).filter(row => row.row.state === 'OUTSTANDING').length)
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-self-check-review.title')"
      :subtitle="t('pages.inventory-self-check-review.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && review">
        <NeutralContainer class="space-y-1">
          <SubHeader>{{ review.memberName }}</SubHeader>
          <MutedText size="sm" data-testid="review-people">
            {{ t('selfCheck.review.people', {reporter: review.submittedByName, handedOut: review.handedOutByName}) }}
          </MutedText>
          <MutedText v-if="review.task.dueOn" size="sm">
            {{ t('selfCheck.dueOn', {date: formatDate(review.task.dueOn)}) }}
          </MutedText>
          <MutedText size="sm" data-testid="review-outstanding">
            {{ t('selfCheck.review.outstanding', {count: outstanding}) }}
          </MutedText>
        </NeutralContainer>

        <Alert v-if="!review.mayApprove" variant="info" data-testid="review-refusal">
          {{ review.approvalRefusal }}
        </Alert>

        <NeutralContainer v-if="review.raised.length > 0" class="space-y-2">
          <SubHeader>{{ t('selfCheck.review.raisedTitle') }}</SubHeader>
          <MutedText size="sm">{{ t('selfCheck.review.raisedHint') }}</MutedText>
          <div v-for="entry in review.raised" :key="entry.raised.id" class="text-sm" data-testid="review-raised">
            {{ t(`selfCheck.review.raised.${entry.raised.kind}`, {item: entry.itemName, name: entry.raisedByName}) }}
          </div>
        </NeutralContainer>

        <div class="space-y-2">
          <ReviewRowCard
              v-for="row in review.rows"
              :key="row.row.id"
              :row="row"
              :busy="busy || correctBusy || refuseBusy"
              :may-approve="review.mayApprove"
              @take="take"
              @correct="openCorrect"
              @refuse="openRefuse"
          />
          <MutedText v-if="review.rows.length === 0" size="sm">{{ t('selfCheck.review.nothingSaid') }}</MutedText>
        </div>
      </template>
    </div>

    <CorrectItemModal
        v-model="showCorrect"
        :available-items="correcting ? freeStockOf(correcting.row.inventoryId) : []"
        :busy="correctBusy"
        :error="correctError"
        :item="correcting?.item ?? null"
        :item-label="itemLabel"
        :req="correcting ? requirementOf(correcting) : null"
        @confirm="applyCorrection"
    />

    <RefuseRowModal
        v-model="showRefuse"
        v-model:reason="refuseReason"
        :busy="refuseBusy"
        :error="refuseError"
        :item-name="refusing?.item?.name ?? refusing?.inventoryName ?? ''"
        @confirm="applyRefusal"
    />
  </ViewContent>
</template>
