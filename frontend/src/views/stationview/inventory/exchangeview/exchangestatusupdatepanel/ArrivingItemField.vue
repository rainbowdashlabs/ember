/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import NewItemFields from './NewItemFields.vue'
import {InventoryTypes, type InventoryItem, type InventorySize} from '@/api/inventory'

/**
 * Which piece arrived: one the station already has, or one it writes down as it comes.
 *
 * <p>Picking is the ordinary case. Recording is for gear whose owner keeps nothing on this instance:
 * there is nothing to pick, and the step that asks cannot be got past without it. An inventory that
 * only ever holds the station's own gear is never offered it, and one that holds nothing else is
 * never offered a procurement instead.
 */
defineProps<{
  inventoryType: string
  availableItems: InventoryItem[]
  sizes: InventorySize[]
  procurementCreated: boolean
}>()

const emit = defineEmits<{
  createProcurement: []
}>()

const pickedItemId = defineModel<string>('pickedItemId', {required: true})
const recording = defineModel<boolean>('recording', {required: true})
const name = defineModel<string>('name', {required: true})
const internalId = defineModel<string>('internalId', {required: true})
const sizeId = defineModel<string>('sizeId', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-1 sm:col-span-2">
    <FieldLabel hint>{{ t('exchanges.exchangedItem') }}</FieldLabel>

    <template v-if="!recording">
      <SelectInput v-model="pickedItemId" data-testid="exchange-item">
        <option value="">{{ t('exchanges.noItem') }}</option>
        <option v-for="item in availableItems" :key="item.id" :value="String(item.id)">
          {{ item.name }} {{ item.internalId ? `(${item.internalId})` : '' }}
        </option>
      </SelectInput>
      <div class="flex gap-2 mt-1">
        <SecondaryButton
            v-if="inventoryType !== InventoryTypes.INTERNAL"
            :icon="['fas', 'plus']"
            data-testid="exchange-record-new"
            @click="recording = true"
        >
          {{ t('exchanges.createNewItem') }}
        </SecondaryButton>
        <template v-if="inventoryType !== InventoryTypes.EXTERNAL && availableItems.length === 0">
          <span v-if="procurementCreated" class="text-xs text-success">
            <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
            {{ t('exchanges.procurementCreated') }}
          </span>
          <SecondaryButton v-else :icon="['fas', 'folder-plus']" @click="emit('createProcurement')">
            {{ t('exchanges.createProcurement') }}
          </SecondaryButton>
        </template>
      </div>
    </template>

    <template v-else>
      <NewItemFields
          v-model:internal-id="internalId"
          v-model:name="name"
          v-model:size-id="sizeId"
          :sizes="sizes"
      />
      <SecondaryButton class="text-xs mt-1" @click="recording = false">
        {{ t('exchanges.selectExisting') }}
      </SecondaryButton>
    </template>
  </div>
</template>
