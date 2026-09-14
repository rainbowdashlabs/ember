/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'

const modelValue = defineModel<boolean>({required: true})

defineProps<{
  message: string
}>()

const emit = defineEmits<{
  confirm: []
}>()

const { t } = useI18n()
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-4">
      <p class="text-sm">{{ message }}</p>
      <ButtonRow pair align="end">
        <SecondaryButton @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton @click="emit('confirm')">{{ t('common.confirm') }}</ErrorButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
