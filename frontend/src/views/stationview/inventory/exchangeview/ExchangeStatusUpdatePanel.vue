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
import NewItemFields from './exchangestatusupdatepanel/NewItemFields.vue'

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
      ownerKind: ItemOwner.CLUSTER,
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
        <SelectInput v-model="updateTargetStatus">
          <option value="" disabled>{{ t('exchanges.selectStatus') }}</option>
          <option v-for="s in nextStatuses" :key="s" :value="s">{{ statusLabel(s) }}</option>
        </SelectInput>
      </div>
      <div v-if="namesTheArrival" class="space-y-1 sm:col-span-2">
        <FieldLabel hint>{{ t('exchanges.exchangedItem') }}</FieldLabel>
        <template v-if="!createNewItemForExchange">
          <SelectInput v-model="updateExchangedItemId">
            <option value="">{{ t('exchanges.noItem') }}</option>
            <option v-for="item in availableItems" :key="item.id" :value="String(item.id)">
              {{ item.name }} {{ item.internalId ? `(${item.internalId})` : '' }}
            </option>
          </SelectInput>
          <div class="flex gap-2 mt-1">
            <SecondaryButton :icon="['fas', 'plus']" v-if="request.inventoryType !== InventoryTypes.INTERNAL" @click="createNewItemForExchange = true">
              {{ t('exchanges.createNewItem') }}
            </SecondaryButton>
            <template v-if="request.inventoryType !== InventoryTypes.EXTERNAL && availableItems.length === 0">
              <span v-if="procurementCreatedForExchange" class="text-xs text-success">
                <font-awesome-icon :icon="['fas', 'check']" class="mr-1" />
                {{ t('exchanges.procurementCreated') }}
              </span>
              <SecondaryButton :icon="['fas', 'folder-plus']" v-else @click="createProcurementFromExchange">
                {{ t('exchanges.createProcurement') }}
              </SecondaryButton>
            </template>
          </div>
        </template>
        <template v-else>
          <NewItemFields
              v-model:internal-id="newItemInternalId"
              v-model:name="newItemName"
              v-model:size-id="newItemSizeId"
              :sizes="sizes"
          />
          <SecondaryButton class="text-xs mt-1" @click="createNewItemForExchange = false">
            {{ t('exchanges.selectExisting') }}
          </SecondaryButton>
        </template>
      </div>
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
