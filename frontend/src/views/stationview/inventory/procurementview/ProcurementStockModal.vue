/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AppLink from '@/components/navigation/AppLink.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Modal from '@/components/feedback/Modal.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { ProcurementResponse, StockPiece } from '@/api/generated/schema'
import { procurement } from '@/api'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useInventoryRoutes } from '@/composables/useInventoryRoutes'

/**
 * Settles an order with a piece the station already has: the reader picks one of the matching pieces in
 * storage, and it goes out to the member on a movement while the order is marked fulfilled.
 */
const modelValue = defineModel<boolean>({required: true})

const props = defineProps<{
  entry: ProcurementResponse
}>()

const emit = defineEmits<{
  (e: 'issued'): void
}>()

const { t } = useI18n()
const routes = useInventoryRoutes()
const router = useRouter()

const pickedId = ref('')
const movementId = ref<number | null>(null)

const movementRoute = computed(() => movementId.value === null
  ? null
  : router.resolve({name: routes.movement, params: {id: String(movementId.value)}}).href)

function pieceLabel(piece: StockPiece): string {
  return piece.internalId ? `${piece.name} (${piece.internalId})` : piece.name
}

function close() {
  modelValue.value = false
}

const {running, failure, run: submit, clearError} = useAsyncAction(async () => {
  const started = await procurement.issueFromStock(props.entry.id, Number(pickedId.value))
  movementId.value = started.movementId
  emit('issued')
})

watch(modelValue, (open) => {
  if (!open) return
  pickedId.value = props.entry.stock.length === 1 ? String(props.entry.stock[0]!.id) : ''
  movementId.value = null
  clearError()
}, {immediate: true})
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-4">
      <SubHeader>{{ t('procurement.fromStockTitle') }}</SubHeader>

      <template v-if="movementRoute">
        <Alert variant="success">{{ t('procurement.fromStockDone') }}</Alert>
        <AppLink :href="movementRoute" :icon="['fas', 'arrow-right']" data-testid="procurement-stock-movement">
          {{ t('procurement.openMovement') }}
        </AppLink>
        <div class="flex justify-end">
          <SecondaryButton data-testid="procurement-stock-close" @click="close">{{ t('common.close') }}</SecondaryButton>
        </div>
      </template>
      <template v-else>
        <p class="text-sm">{{ t('procurement.fromStockHint') }}</p>
        <FailureAlert :failure="failure"/>

        <div class="space-y-1">
          <FieldLabel>{{ t('procurement.piece') }}</FieldLabel>
          <SelectInput v-model="pickedId" data-testid="procurement-stock-piece">
            <option value="">{{ t('procurement.selectPiece') }}</option>
            <option v-for="piece in entry.stock" :key="piece.id" :value="String(piece.id)">{{ pieceLabel(piece) }}</option>
          </SelectInput>
        </div>

        <ButtonRow pair align="end">
          <SecondaryButton @click="close">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton
              :disabled="running || !pickedId"
              data-testid="procurement-stock-submit"
              @click="submit"
          >
            {{ running ? t('common.loading') : t('procurement.fromStockSubmit') }}
          </PrimaryButton>
        </ButtonRow>
      </template>
    </div>
  </Modal>
</template>
