/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import ArtPicker from '../../ArtPicker.vue'
import type {InventoryDetail} from '@/api/inventory'
import type {InventoryArt} from '@/api/inventoryArts'

/**
 * What is asked when a piece is written down: its identifier, its name, what kind of thing it is,
 * its size and how many of it there are.
 *
 * <p>The kind sits beside the name and never in place of it, and it is offered only where kinds
 * exist at all. Leaving it empty is ordinary rather than unfinished.
 */
defineProps<{
  detail: InventoryDetail
  arts: InventoryArt[]
  heterogeneous: boolean
}>()

const internalId = defineModel<string>('internalId', {default: ''})
const name = defineModel<string>('name', {default: ''})
const sizeId = defineModel<string>('sizeId', {default: ''})
const quantity = defineModel<number>('quantity', {default: 1})
const artId = defineModel<number | null>('artId', {default: null})
const artDraft = defineModel<string>('artDraft', {default: ''})

const {t} = useI18n()

/** The identifier is what a scanner fills, so the cursor starts there. */
const internalIdInput = ref<InstanceType<typeof TextInput> | null>(null)
onMounted(() => internalIdInput.value?.$el?.focus())
</script>

<template>
  <div class="space-y-4">
    <div class="space-y-1">
      <FieldLabel>{{ t('inventory.edit.itemInternalId') }}</FieldLabel>
      <div class="flex items-center gap-2">
        <TextInput ref="internalIdInput" v-model="internalId" class="flex-1"
                   :placeholder="t('inventory.edit.itemInternalIdPlaceholder')"/>
        <ScanButton @decoded="internalId = $event"/>
      </div>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('inventory.edit.itemName') }}</FieldLabel>
      <TextInput v-model="name" :placeholder="t('inventory.edit.itemNamePlaceholder')"/>
    </div>
    <div v-if="heterogeneous" class="space-y-1">
      <FieldLabel>{{ t('inventory.art.field') }}</FieldLabel>
      <ArtPicker v-model:artId="artId" v-model:draft="artDraft" :arts="arts"/>
      <p class="text-xs text-(--text-muted)">{{ t('inventory.art.fieldHint') }}</p>
    </div>
    <div v-if="detail.hasSizes" class="space-y-1">
      <FieldLabel>{{ t('inventory.edit.itemSize') }}</FieldLabel>
      <SelectInput v-model="sizeId">
        <option value="">&#x2013;</option>
        <option v-for="size in detail.sizes ?? []" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
      </SelectInput>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('inventory.edit.itemQuantity') }}</FieldLabel>
      <NumberInput v-model="quantity" :max="100" :min="1"/>
      <p class="text-xs text-(--text-muted)">{{ t('inventory.edit.itemQuantityHint') }}</p>
    </div>
  </div>
</template>
