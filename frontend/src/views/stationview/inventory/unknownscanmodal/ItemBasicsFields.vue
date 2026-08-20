/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {ItemOwner, type ItemOwnerName} from '@/api/inventory'

const itemName = defineModel<string>('itemName', {required: true})
const pickedSize = defineModel<string>('pickedSize', {required: true})
const ownerKind = defineModel<ItemOwnerName>('ownerKind', {required: true})

defineProps<{
  showSizePicker: boolean
  sizeLabels: string[]
  showOwnerPicker: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>{{ t('inventory.unknownScan.itemName') }}</FieldLabel>
    <TextInput
        v-model="itemName"
        :placeholder="t('inventory.unknownScan.itemNamePlaceholder')"
    />
  </div>

  <div v-if="showSizePicker" class="space-y-1">
    <FieldLabel>
      {{ t('inventory.unknownScan.itemSize') }}
      <span class="text-error">*</span>
    </FieldLabel>
    <SelectInput v-model="pickedSize">
      <option value="">{{ t('inventory.unknownScan.pickSize') }}</option>
      <option v-for="label in sizeLabels" :key="label" :value="label">{{ label }}</option>
    </SelectInput>
  </div>

  <div v-if="showOwnerPicker" class="space-y-1">
    <FieldLabel>{{ t('inventory.unknownScan.itemOwner') }}</FieldLabel>
    <SelectInput
        :model-value="ownerKind"
        @update:model-value="(v: string | number | null | undefined) => ownerKind = String(v ?? '') as ItemOwnerName"
    >
      <option :value="ItemOwner.STATION">{{ t('inventory.unknownScan.owners.STATION') }}</option>
      <option :value="ItemOwner.CLUSTER">{{ t('inventory.unknownScan.owners.CLUSTER') }}</option>
    </SelectInput>
  </div>
</template>
