/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type { InventoryItem, InventorySize } from '@/api/types'
import { inventory } from '@/api'

const props = defineProps<{
  item: InventoryItem | null
  hasSizes: boolean
  sizes: InventorySize[]
}>()

const show = defineModel<boolean>({ default: false })

const emit = defineEmits<{
  saved: []
}>()

const { t } = useI18n()

const itemName = ref('')
const internalId = ref('')
const sizeId = ref('')
const error = ref('')

watch(() => props.item, (item) => {
  if (item) {
    itemName.value = item.name ?? ''
    internalId.value = item.internalId ?? ''
    sizeId.value = item.sizeId != null ? String(item.sizeId) : ''
  }
})

async function save() {
  if (!props.item) return
  error.value = ''
  try {
    await inventory.updateItem(props.item.id, {
      name: itemName.value,
      internalId: internalId.value || undefined,
      sizeId: sizeId.value ? Number(sizeId.value) : undefined,
    })
    show.value = false
    emit('saved')
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}
</script>

<template>
  <Modal v-model="show">
    <div class="space-y-4">
      <SectionHeader>{{ t('inventory.edit.editItem') }}</SectionHeader>
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.edit.itemName') }}</FieldLabel>
        <TextInput v-model="itemName" :placeholder="t('inventory.edit.itemNamePlaceholder')" />
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.edit.itemInternalId') }}</FieldLabel>
        <TextInput v-model="internalId" :placeholder="t('inventory.edit.itemInternalIdPlaceholder')" />
      </div>
      <div v-if="props.hasSizes" class="space-y-1">
        <FieldLabel>{{ t('inventory.edit.itemSize') }}</FieldLabel>
        <SelectInput v-model="sizeId">
          <option value="">&#x2013;</option>
          <option v-for="size in props.sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
        </SelectInput>
      </div>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
        <SaveButton :disabled="!itemName.trim()" :action="save"/>
      </div>
    </div>
  </Modal>
</template>
