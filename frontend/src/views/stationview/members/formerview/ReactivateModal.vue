/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

defineProps<{
  modelValue: boolean
  targetName: string
}>()

defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm'): void
}>()

const { t } = useI18n()
</script>

<template>
  <Modal :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)">
    <div class="space-y-4">
      <SectionHeader>{{ t('formerMembers.reactivateTitle') }}</SectionHeader>
      <p class="text-sm">
        {{ t('formerMembers.reactivateConfirm', { name: targetName }) }}
      </p>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="$emit('update:modelValue', false)">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton @click="$emit('confirm')">
          {{ t('formerMembers.reactivate') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
