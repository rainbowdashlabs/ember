/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import type {InventorySize} from '@/api/types'
import type {MyInventoryItem} from '@/api/inventory'
import {useModelProxy} from '@/composables/useModelProxy'

const props = defineProps<{
  modelValue: boolean
  item: MyInventoryItem | null
  sizes: InventorySize[]
  reason: string
  newSizeId: string
  saving: boolean
  success: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'update:reason', value: string): void
  (e: 'update:newSizeId', value: string): void
  (e: 'submit'): void
}>()

const {t} = useI18n()

const open = useModelProxy(() => props.modelValue, emit, 'modelValue')

const submitDisabled = computed(() =>
    props.saving || !props.reason.trim() || (props.sizes.length > 0 && !props.newSizeId))
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <SectionHeader>{{ t('memberDetail.requestExchange') }}</SectionHeader>
      <template v-if="success">
        <Alert variant="success">{{ t('profile.exchangeCreated') }}</Alert>
        <div class="flex justify-end">
          <SecondaryButton @click="open = false">{{ t('common.close') }}</SecondaryButton>
        </div>
      </template>
      <template v-else>
        <p v-if="item" class="text-sm">
          {{ item.inventoryName }} — {{ item.name }}
          <span class="text-(--text-muted)">{{ item.sizeName ?? t('common.unisize') }}</span>
        </p>
        <div v-if="sizes.length > 0" class="space-y-1">
          <FieldLabel>{{ t('exchanges.newSize') }}</FieldLabel>
          <SelectInput :model-value="newSizeId" @update:model-value="emit('update:newSizeId', String($event))">
            <option value="" disabled>{{ t('exchanges.selectNewSize') }}</option>
            <option v-for="size in sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
          </SelectInput>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('exchanges.reason') }}</FieldLabel>
          <TextAreaInput
              :model-value="reason"
              @update:model-value="emit('update:reason', String($event))"
              :placeholder="t('exchanges.reasonPlaceholder')"
              :rows="3" />
        </div>
        <div class="flex justify-end gap-2">
          <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="submitDisabled" @click="emit('submit')">
            {{ saving ? t('common.loading') : t('exchanges.submit') }}
          </PrimaryButton>
        </div>
      </template>
    </div>
  </Modal>
</template>
