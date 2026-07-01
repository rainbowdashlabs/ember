/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { WaitingListField } from '@/api/types'

defineProps<{
  modelValue: boolean
  target: WaitingListField | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm'): void
}>()

const { t } = useI18n()

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <Modal :model-value="modelValue" @update:model-value="emit('update:modelValue', $event)">
    <div class="space-y-4">
      <SubHeader>{{ t('waitingList.deleteFieldTitle') }}</SubHeader>
      <p class="text-sm">{{ t('waitingList.deleteFieldConfirm', { name: target?.name }) }}</p>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="close">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton @click="emit('confirm')">{{ t('common.delete') }}</ErrorButton>
      </div>
    </div>
  </Modal>
</template>
