/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import type {InventorySize} from '@/api/types'
import type {MyInventoryItem} from '@/api/inventory'

const modelValue = defineModel<boolean>({required: true})
const reason = defineModel<string>('reason', {required: true})
const newSizeId = defineModel<string>('newSizeId', {required: true})

defineProps<{
  item: MyInventoryItem | null
  sizes: InventorySize[]
}>()

const emit = defineEmits<{
  cancel: []
  submit: []
}>()

const {t} = useI18n()
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-3">
      <SectionHeader>{{ t('profile.requestExchange') }}</SectionHeader>
      <p class="text-sm" v-if="item">
        {{ item.inventoryName }} — {{ item.name }}
        <SizeBadge>{{ item.sizeName ?? t('common.unisize') }}</SizeBadge>
      </p>
      <div v-if="sizes.length > 0" class="space-y-1">
        <FieldLabel>{{ t('exchanges.newSize') }}</FieldLabel>
        <SelectInput v-model="newSizeId" class="w-full">
          <option value="" disabled>{{ t('exchanges.selectNewSize') }}</option>
          <option v-for="size in sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
        </SelectInput>
      </div>
      <TextAreaInput v-model="reason" :placeholder="t('profile.exchangeReasonPlaceholder')" :rows="3" />
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!reason.trim() || (sizes.length > 0 && !newSizeId)" @click="emit('submit')">
          {{ t('profile.submitExchange') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
