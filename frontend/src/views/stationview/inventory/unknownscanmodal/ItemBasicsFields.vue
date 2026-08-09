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
import {ItemSource} from '@/api/inventory'

const itemName = defineModel<string>('itemName', {required: true})
const pickedSize = defineModel<string>('pickedSize', {required: true})
const itemSource = defineModel<'INTERNAL' | 'EXTERNAL'>('itemSource', {required: true})

defineProps<{
  showSizePicker: boolean
  sizeLabels: string[]
  showSourcePicker: boolean
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

  <div v-if="showSourcePicker" class="space-y-1">
    <FieldLabel>{{ t('inventory.unknownScan.itemSource') }}</FieldLabel>
    <SelectInput
        :model-value="itemSource"
        @update:model-value="(v: string | number | null | undefined) => itemSource = String(v ?? '') as 'INTERNAL' | 'EXTERNAL'"
    >
      <option :value="ItemSource.INTERNAL">{{ t('inventory.unknownScan.sources.INTERNAL') }}</option>
      <option :value="ItemSource.EXTERNAL">{{ t('inventory.unknownScan.sources.EXTERNAL') }}</option>
    </SelectInput>
  </div>
</template>
