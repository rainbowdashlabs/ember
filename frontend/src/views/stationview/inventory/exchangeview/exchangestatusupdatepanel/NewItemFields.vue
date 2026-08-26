/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {InventorySize} from '@/api/inventory'

/**
 * The piece that arrived, written down where nobody could name it.
 *
 * <p>A body that keeps no gear on this instance sends something without recording it anywhere, so the
 * station has nothing to pick from and writes down what turned up. The size starts out as the one the
 * exchange asked for and stays editable, because what arrives is not always what was asked for and a
 * station made to record the wrong size to get past the step would be worse off than with none.
 */
defineProps<{
  /** The sizes the inventory keeps. Empty where it keeps none, and then the field is absent. */
  sizes: InventorySize[]
}>()

const name = defineModel<string>('name', {required: true})
const internalId = defineModel<string>('internalId', {required: true})
const sizeId = defineModel<string>('sizeId', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="grid grid-cols-1 gap-2 sm:grid-cols-3">
    <div class="space-y-1">
      <FieldLabel hint>{{ t('exchanges.newItemName') }}</FieldLabel>
      <TextInput v-model="name" class="w-full" :placeholder="t('exchanges.newItemName')"/>
    </div>
    <div class="space-y-1">
      <FieldLabel hint>{{ t('exchanges.newItemInternalId') }}</FieldLabel>
      <TextInput v-model="internalId" class="w-full" :placeholder="t('exchanges.newItemInternalIdPlaceholder')"/>
    </div>
    <div v-if="sizes.length > 0" class="space-y-1">
      <FieldLabel hint>{{ t('exchanges.newItemSize') }}</FieldLabel>
      <SelectInput v-model="sizeId" class="w-full">
        <option value="">{{ t('common.unisize') }}</option>
        <option v-for="size in sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
      </SelectInput>
    </div>
  </div>
</template>
