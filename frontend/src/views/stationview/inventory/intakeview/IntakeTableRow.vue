/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import FieldValueInput from '@/views/stationview/inventory/detailview/FieldValueInput.vue'
import type {InventorySize} from '@/api/inventory'
import type {InventoryFieldDefinition} from '@/api/inventoryFields'
import type {IntakeLine} from './intakeLines'

/** One member's line of the stock-taking: what they hold, in the columns the inventory keeps. */
const line = defineModel<IntakeLine>({required: true})

const props = defineProps<{
  index: number
  sizes: InventorySize[]
  fields: InventoryFieldDefinition[]
  hasSizes: boolean
}>()

const emit = defineEmits<{
  (e: 'remove'): void
}>()

const {t} = useI18n()

function setField(key: string, value: unknown) {
  line.value = {...line.value, fields: {...line.value.fields, [key]: value}}
}
</script>

<template>
  <tr data-testid="intake-row">
    <td class="py-1 pr-3">{{ line.memberName }}</td>
    <td v-if="props.hasSizes" class="py-1 pr-3">
      <SelectInput
          :model-value="line.sizeId"
          class="w-32"
          :data-testid="`intake-size-${props.index}`"
          @update:model-value="value => line = {...line, sizeId: String(value ?? '')}"
      >
        <option value="">-</option>
        <option v-for="size in props.sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
      </SelectInput>
    </td>
    <td class="py-1 pr-3">
      <TextInput
          :model-value="line.internalId"
          class="w-32"
          :placeholder="t('inventory.intake.numberPlaceholder')"
          :data-testid="`intake-number-${props.index}`"
          @update:model-value="value => line = {...line, internalId: value ?? ''}"
      />
    </td>
    <td v-for="field in props.fields" :key="field.id" class="py-1 pr-3">
      <FieldValueInput
          :field="field"
          :model-value="line.fields[field.key]"
          @update:model-value="value => setField(field.key, value)"
      />
    </td>
    <td class="py-1">
      <MutedIconButton
          :icon="['fas', 'xmark']"
          :label="t('inventory.intake.removeLine')"
          hover="error"
          :data-testid="`intake-remove-${props.index}`"
          @click="emit('remove')"
      />
    </td>
  </tr>
</template>
