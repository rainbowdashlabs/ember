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
import {ItemOwner} from '@/api/generated/schema'

/** The owners an unknown item can be recorded under, in the order the picker offers them. */
const OWNER_CHOICES: readonly ItemOwner[] = [ItemOwner.STATION, ItemOwner.CLUSTER]

const itemName = defineModel<string>('itemName', {required: true})
const pickedSize = defineModel<string>('pickedSize', {required: true})
const ownerKind = defineModel<ItemOwner>('ownerKind', {required: true})

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
        @update:model-value="(v: string | number | null | undefined) => ownerKind = String(v ?? '') as ItemOwner"
    >
      <option v-for="owner in OWNER_CHOICES" :key="owner" :value="owner">{{ t(`inventory.unknownScan.owners.${owner}`) }}</option>
    </SelectInput>
  </div>
</template>
