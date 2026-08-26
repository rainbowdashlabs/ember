/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import {ExchangeStatus, type ExchangeRequestEntry, type ExchangeStatusName} from '@/api/exchanges'
import {InventoryTypes, ItemOwner, type InventoryItem, type InventorySize} from '@/api/inventory'
import { exchanges, inventory, procurement } from '@/api'
import { useAsyncAction } from '@/composables/useAsyncAction'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ArrivingItemField from './exchangestatusupdatepanel/ArrivingItemField.vue'

const { t } = useI18n()

const props = defineProps<{
  request: ExchangeRequestEntry
  nextStatuses: ExchangeStatusName[]
  availableItems: InventoryItem[]
}>()

const emit = defineEmits<{
  done: []
  cancel: []
  error: [msg: string]
}>()

const updateTargetStatus = ref<string>('')
const updateNote = ref('')
const updateExchangedItemId = ref<string>('')
const createNewItemForExchange = ref(false)
const newItemName = ref(props.request.inventoryName)
const newItemInternalId = ref('')
const newItemSizeId = ref<string>(String(props.request.newSizeId ?? props.request.oldSizeId ?? ''))
const sizes = ref<InventorySize[]>([])
const procurementCreatedForExchange = ref(false)

/**
 * The sizes the inventory keeps, for the piece being written down.
 *
 * <p>The size asked for is filled in, because that is what the exchange was raised about, and it stays
 * editable: what an association actually sends is not always what was asked for, and a station writing
 * down the wrong size to get past the step would be worse than no size at all.
 */
onMounted(async () => {
  try {
    sizes.value = await inventory.listSizes(props.request.inventoryId)
  } catch {
    sizes.value = []
  }
})

function statusLabel(status: ExchangeStatusName): string {
  return t(`exchanges.status.${status}`)
}

/**
 * Whether getting to the chosen status walks past the step that says which piece arrived.
 *
 * <p>Both of them do. Which step asks depends on the chain: for the station's own gear it is the one
 * that hands the replacement over, and for the body above it the one where that body sends it. Asking
 * only at the last status left the walk to stop halfway with nothing said, and the row looked stuck.
 */
const namesTheArrival = computed(() =>
    updateTargetStatus.value === ExchangeStatus.DONE || updateTargetStatus.value === ExchangeStatus.ARRIVED)

const {running: updateSaving, run: runStatusUpdate} = useAsyncAction(async () => {
  let exchangedItemId: number | undefined = updateExchangedItemId.value ? Number(updateExchangedItemId.value) : undefined
  if (createNewItemForExchange.value && newItemName.value.trim()) {
    const newItem = await inventory.createItem(props.request.inventoryId, {
      name: newItemName.value.trim(),
      internalId: newItemInternalId.value.trim(),
      sizeId: newItemSizeId.value ? Number(newItemSizeId.value) : undefined,
      ownerKind: props.request.ownerKind ?? ItemOwner.CLUSTER,
    })
    exchangedItemId = newItem.id
  }
  await exchanges.updateStatus(props.request.id, {
    status: updateTargetStatus.value,
    note: updateNote.value || undefined,
    exchangedItemId,
  })
  createNewItemForExchange.value = false
  newItemName.value = ''
  emit('done')
  return true
})

async function submitStatusUpdate() {
  if (!updateTargetStatus.value) return
  const ok = await runStatusUpdate()
  if (!ok) emit('error', t('common.error'))
}

async function createProcurementFromExchange() {
  try {
    procurementCreatedForExchange.value = false
    await procurement.createProcurement({
      inventoryId: props.request.inventoryId,
      memberId: props.request.memberId,
      sizeId: props.request.newSizeId ?? props.request.oldSizeId ?? undefined,
      notes: t('exchanges.procurementFromExchange', { reason: props.request.reason }),
    })
    procurementCreatedForExchange.value = true
  } catch {
    emit('error', t('common.error'))
  }
}
</script>

<template>
  <div class="flex flex-col gap-2 items-stretch">
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
      <div class="space-y-1">
        <FieldLabel hint>{{ t('exchanges.newStatus') }}</FieldLabel>
        <SelectInput v-model="updateTargetStatus" data-testid="exchange-status">
          <option value="" disabled>{{ t('exchanges.selectStatus') }}</option>
          <option v-for="s in nextStatuses" :key="s" :value="s">{{ statusLabel(s) }}</option>
        </SelectInput>
      </div>
      <ArrivingItemField
          v-if="namesTheArrival"
          v-model:internal-id="newItemInternalId"
          v-model:name="newItemName"
          v-model:picked-item-id="updateExchangedItemId"
          v-model:recording="createNewItemForExchange"
          v-model:size-id="newItemSizeId"
          :available-items="availableItems"
          :inventory-type="request.inventoryType"
          :procurement-created="procurementCreatedForExchange"
          :sizes="sizes"
          @create-procurement="createProcurementFromExchange"
      />
      <div class="space-y-1">
        <FieldLabel hint>{{ t('exchanges.note') }}</FieldLabel>
        <TextInput v-model="updateNote" :placeholder="t('exchanges.notePlaceholder')" />
      </div>
    </div>
    <div class="flex gap-2 justify-end">
      <PrimaryButton :disabled="updateSaving || !updateTargetStatus" @click="submitStatusUpdate">
        {{ updateSaving ? t('common.loading') : t('exchanges.updateStatus') }}
      </PrimaryButton>
      <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
    </div>
  </div>
</template>
