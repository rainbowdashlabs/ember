/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IntakeMemberHeader from './IntakeMemberHeader.vue'
import IntakeTableRow from './IntakeTableRow.vue'
import {compareSortValues, type SortDirection} from '@/composables/useSortable'
import type {InventorySize} from '@/api/inventory'
import type {InventoryFieldDefinition} from '@/api/inventoryFields'
import type {IntakeLine} from './intakeLines'

/**
 * The stock-taking itself: one line per member, filled in and saved once.
 *
 * <p>Above it stands what most stock-takings need most: a size chosen once and written into every
 * line that is still empty. Thirty identical jackets are then one choice and one press rather than
 * thirty.
 */
const lines = defineModel<IntakeLine[]>('lines', {required: true})
const bulkSize = defineModel<string>('bulkSize', {required: true})

const props = defineProps<{
  sizes: InventorySize[]
  fields: InventoryFieldDefinition[]
  hasSizes: boolean
}>()

const emit = defineEmits<{
  (e: 'applyToEmpty'): void
}>()

const {t} = useI18n()

const sortKey = ref<'firstName' | 'lastName'>('lastName')
const direction = ref<SortDirection>('asc')

/**
 * Puts the table in order there and then, rather than rendering a sorted view of it.
 *
 * <p>Every row is a form the reader is filling in, and a view that re-sorts itself while they type
 * would move the row under their hands the moment a name changed. Reordering the lines themselves
 * happens once, when the button is pressed, and stays put afterwards.
 */
function sortBy(key: 'firstName' | 'lastName') {
  if (sortKey.value === key) direction.value = direction.value === 'asc' ? 'desc' : 'asc'
  else {
    sortKey.value = key
    direction.value = 'asc'
  }
  const factor = direction.value === 'asc' ? 1 : -1
  lines.value = [...lines.value].sort((a, b) =>
      (compareSortValues(a[sortKey.value], b[sortKey.value])
          || compareSortValues(a.memberName, b.memberName)) * factor)
}

function remove(index: number) {
  lines.value = lines.value.filter((_, at) => at !== index)
}

function update(index: number, line: IntakeLine) {
  lines.value = lines.value.map((known, at) => (at === index ? line : known))
}
</script>

<template>
  <div class="space-y-3">
    <div v-if="props.hasSizes && props.sizes.length > 0" class="flex flex-wrap items-end gap-2">
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.intake.bulkSize') }}</FieldLabel>
        <SelectInput v-model="bulkSize" class="w-40" data-testid="intake-bulk-size">
          <option value="">{{ t('inventory.intake.chooseSize') }}</option>
          <option v-for="size in props.sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
        </SelectInput>
      </div>
      <SecondaryButton :disabled="!bulkSize" data-testid="intake-apply-size" @click="emit('applyToEmpty')">
        {{ t('inventory.intake.applyToEmpty') }}
      </SecondaryButton>
    </div>

    <MutedText v-if="lines.length === 0" size="sm" tag="p">{{ t('inventory.intake.nobodyYet') }}</MutedText>

    <div v-else class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <tr class="text-left text-(--text-muted)">
            <th class="py-1 pr-3 font-medium" :title="t('inventory.intake.askedForHint')">
              {{ t('inventory.intake.askedFor') }}
            </th>
            <IntakeMemberHeader :active-key="sortKey" :direction="direction" @sort="sortBy"/>
            <th v-if="props.hasSizes" class="py-1 pr-3 font-medium">{{ t('inventory.intake.size') }}</th>
            <th class="py-1 pr-3 font-medium">{{ t('inventory.intake.number') }}</th>
            <th v-for="field in props.fields" :key="field.id" class="py-1 pr-3 font-medium">{{ field.label }}</th>
            <th class="py-1"></th>
          </tr>
        </thead>
        <tbody>
          <IntakeTableRow
              v-for="(line, index) in lines"
              :key="line.memberId ?? `row-${index}`"
              :model-value="line"
              :index="index"
              :sizes="props.sizes"
              :fields="props.fields"
              :has-sizes="props.hasSizes"
              @update:model-value="update(index, $event)"
              @remove="remove(index)"
          />
        </tbody>
      </table>
    </div>
  </div>
</template>
