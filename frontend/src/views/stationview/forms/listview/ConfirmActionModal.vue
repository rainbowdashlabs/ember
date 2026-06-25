/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const open = defineModel<boolean>({required: true})

defineProps<{
  message: string
}>()

const emit = defineEmits<{
  (e: 'confirm'): void
}>()

const { t } = useI18n()
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <p class="text-sm">{{ message }}</p>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton @click="emit('confirm')">{{ t('forms.confirm') }}</PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
