/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {InventoryItem, RequiredInventoryItem} from '@/api/inventory'

/**
 * One inventory the member is short of, with the two ways to put that right: hand over a piece
 * that is lying in stock, or write a new one down and hand that over.
 */
const props = defineProps<{
  requirement: RequiredInventoryItem
  available: InventoryItem[]
}>()

const emit = defineEmits<{
  handOut: [itemId: number]
  handOutNew: [sizeId: number | null]
}>()

const {t} = useI18n()

const pickedItem = ref('')
const pickedSize = ref('')

const missing = computed(() => props.requirement.requiredQuantity - props.requirement.assignedQuantity)
const needsSize = computed(() => props.requirement.hasSizes && props.requirement.sizes.length > 0)

function itemLabel(item: InventoryItem): string {
  const size = props.requirement.sizes.find(s => s.id === item.sizeId)?.label
  const name = item.internalId ?? item.name ?? `#${item.id}`
  return size ? `${name} (${size})` : name
}

function handOut() {
  if (!pickedItem.value) return
  emit('handOut', Number(pickedItem.value))
  pickedItem.value = ''
}

function handOutNew() {
  emit('handOutNew', pickedSize.value ? Number(pickedSize.value) : null)
  pickedSize.value = ''
}
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="missing-requirement">
    <div class="flex items-center justify-between gap-2">
      <SubHeader>{{ requirement.inventoryName }}</SubHeader>
      <MutedText size="sm" class="shrink-0">
        {{ requirement.assignedQuantity }} / {{ requirement.requiredQuantity }}
        <span class="text-error">{{ t('memberDetail.missingCount', {count: missing}) }}</span>
      </MutedText>
    </div>

    <div v-if="available.length > 0" class="flex flex-col gap-2 sm:flex-row">
      <SelectInput v-model="pickedItem" class="flex-1">
        <option value="" disabled>{{ t('memberDetail.pickStockItem') }}</option>
        <option v-for="item in available" :key="item.id" :value="String(item.id)">{{ itemLabel(item) }}</option>
      </SelectInput>
      <PrimaryButton :disabled="!pickedItem" @click="handOut">{{ t('memberDetail.handOut') }}</PrimaryButton>
    </div>
    <MutedText v-else tag="div" size="sm">{{ t('memberDetail.nothingInStock') }}</MutedText>

    <div class="flex flex-col gap-2 sm:flex-row">
      <SelectInput v-if="needsSize" v-model="pickedSize" class="flex-1">
        <option value="" disabled>{{ t('memberDetail.pickSize') }}</option>
        <option v-for="size in requirement.sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
      </SelectInput>
      <SecondaryButton :icon="['fas', 'plus']" :disabled="needsSize && !pickedSize" @click="handOutNew">
        {{ t('memberDetail.createAndHandOut') }}
      </SecondaryButton>
    </div>
  </NeutralContainer>
</template>
