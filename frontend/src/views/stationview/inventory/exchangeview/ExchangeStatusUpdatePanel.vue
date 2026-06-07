/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import type { ExchangeRequestEntry, ExchangeStatusName, InventoryItem } from '@/api/types'
import { ExchangeStatus, InventoryTypes, ItemSource } from '@/api/types'
import { exchanges, inventory, procurement } from '@/api'
import FieldLabel from '@/components/typography/FieldLabel.vue'

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
const updateSaving = ref(false)
const updateExchangedItemId = ref<string>('')
const createNewItemForExchange = ref(false)
const newItemName = ref(props.request.inventoryName)
const procurementCreatedForExchange = ref(false)

function statusLabel(status: ExchangeStatusName): string {
  return t(`exchanges.status.${status}`)
}

async function submitStatusUpdate() {
  if (!updateTargetStatus.value) return
  updateSaving.value = true
  try {
    let exchangedItemId: number | undefined = updateExchangedItemId.value ? Number(updateExchangedItemId.value) : undefined
    if (createNewItemForExchange.value && newItemName.value.trim()) {
      const newItem = await inventory.createItem(props.request.inventoryId, {
        name: newItemName.value.trim(),
        internalId: '',
        sizeId: props.request.newSizeId ?? props.request.oldSizeId ?? undefined,
        itemSource: ItemSource.EXTERNAL,
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
  } catch {
    emit('error', t('common.error'))
  } finally {
    updateSaving.value = false
  }
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
      <div v-if="updateTargetStatus === ExchangeStatus.EXCHANGED" class="space-y-1 sm:col-span-2">
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
          <TextInput v-model="newItemName" :placeholder="t('exchanges.newItemName')" />
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
