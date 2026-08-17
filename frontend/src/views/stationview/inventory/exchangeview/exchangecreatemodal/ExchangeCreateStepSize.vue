/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type { InventorySize } from '@/api/inventory'
import type { MemberItemOption } from './ExchangeCreateStepItem.vue'

const { t } = useI18n()

const newSizeId = defineModel<string>({ required: true })

defineProps<{
  selectedItem: MemberItemOption | null
  sizes: InventorySize[]
}>()

const emit = defineEmits<{
  next: []
  back: []
}>()
</script>

<template>
  <p v-if="selectedItem" class="text-sm">
    {{ selectedItem.inventoryName }} - {{ selectedItem.name }}
    <span class="text-(--text-muted)">{{ selectedItem.sizeName ?? t('common.unisize') }}</span>
  </p>
  <div class="space-y-1">
    <FieldLabel>{{ t('exchanges.newSize') }}</FieldLabel>
    <SelectInput v-model="newSizeId">
      <option value="" disabled>{{ t('exchanges.noSize') }}</option>
      <option v-for="size in sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
    </SelectInput>
  </div>
  <div class="flex justify-between">
    <SecondaryButton @click="emit('back')">{{ t('common.back') }}</SecondaryButton>
    <PrimaryButton :disabled="!newSizeId" @click="emit('next')">
      {{ t('exchanges.stepNext') }}
    </PrimaryButton>
  </div>
</template>
