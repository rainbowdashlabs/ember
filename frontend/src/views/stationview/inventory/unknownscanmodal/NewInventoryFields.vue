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
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import {InventoryTypes} from '@/api/types'

const name = defineModel<string>('name', {required: true})
const type = defineModel<'INTERNAL' | 'EXTERNAL' | 'MIXED'>('type', {required: true})
const hasSizes = defineModel<boolean>('hasSizes', {required: true})
const sizes = defineModel<string[]>('sizes', {required: true})

const emit = defineEmits<{
  addSize: []
  removeSize: [index: number]
}>()

const {t} = useI18n()

function onSizeInput(index: number, value: string, current: string[]) {
  const next = [...current]
  next[index] = value
  sizes.value = next
}
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>{{ t('inventory.unknownScan.newInventoryName') }}</FieldLabel>
    <TextInput
        v-model="name"
        :placeholder="t('inventory.unknownScan.newInventoryPlaceholder')"
    />
  </div>
  <div class="space-y-1">
    <FieldLabel>{{ t('inventory.unknownScan.newInventoryType') }}</FieldLabel>
    <SelectInput
        :model-value="type"
        @update:model-value="(v: string) => type = v as 'INTERNAL' | 'EXTERNAL' | 'MIXED'"
    >
      <option :value="InventoryTypes.INTERNAL">{{ t('inventory.unknownScan.types.INTERNAL') }}</option>
      <option :value="InventoryTypes.EXTERNAL">{{ t('inventory.unknownScan.types.EXTERNAL') }}</option>
      <option :value="InventoryTypes.MIXED">{{ t('inventory.unknownScan.types.MIXED') }}</option>
    </SelectInput>
  </div>
  <label class="flex items-center gap-2 text-sm">
    <ToggleInput v-model="hasSizes" />
    <span>{{ t('inventory.unknownScan.newInventoryHasSizes') }}</span>
  </label>
  <div v-if="hasSizes" class="space-y-1">
    <FieldLabel>{{ t('inventory.unknownScan.newInventorySizes') }}</FieldLabel>
    <p class="text-xs text-(--text-muted)">{{ t('inventory.unknownScan.newInventorySizesHint') }}</p>
    <div v-for="(value, idx) in sizes" :key="idx" class="flex items-center gap-2">
      <TextInput
          :model-value="value"
          :placeholder="t('inventory.unknownScan.newInventorySizePlaceholder')"
          class="flex-1"
          @update:model-value="(v: string) => onSizeInput(idx, v, sizes)"
      />
      <IconButton
          v-if="sizes.length > 1"
          :icon="['fas', 'xmark']"
          :label="t('common.remove')"
          @click="emit('removeSize', idx)"
      />
    </div>
    <SecondaryButton compact @click="emit('addSize')">
      <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
      {{ t('inventory.unknownScan.addSize') }}
    </SecondaryButton>
  </div>
</template>
