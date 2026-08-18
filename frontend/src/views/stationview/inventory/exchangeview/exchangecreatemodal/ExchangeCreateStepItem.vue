/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * Item option presented in the step's select.
 */
export interface MemberItemOption {
  id: number
  inventoryId: number
  name: string
  internalId: string
  sizeId: number | null
  sizeName: string | null
  inventoryName: string
}

const { t } = useI18n()

const itemId = defineModel<string>({ required: true })

defineProps<{
  loading: boolean
  items: MemberItemOption[]
}>()

const emit = defineEmits<{
  next: []
  back: []
}>()
</script>

<template>
  <Spinner v-if="loading" size="md" />
  <template v-else>
    <MutedText tag="div" size="sm" class="py-2" v-if="items.length === 0">
      {{ t('exchanges.noItemsForMember') }}
    </MutedText>
    <div v-else class="space-y-1">
      <FieldLabel>{{ t('exchanges.selectItem') }}</FieldLabel>
      <SelectInput v-model="itemId">
        <option value="" disabled>{{ t('exchanges.selectItem') }}</option>
        <option v-for="item in items" :key="item.id" :value="String(item.id)">
          {{ item.inventoryName }} - {{ item.name }}
          {{ item.sizeName ?? '' }}
          {{ item.internalId ? `(${item.internalId})` : '' }}
        </option>
      </SelectInput>
    </div>
  </template>
  <div class="flex justify-between">
    <SecondaryButton @click="emit('back')">{{ t('common.back') }}</SecondaryButton>
    <PrimaryButton :disabled="!itemId" @click="emit('next')">
      {{ t('exchanges.stepNext') }}
    </PrimaryButton>
  </div>
</template>
