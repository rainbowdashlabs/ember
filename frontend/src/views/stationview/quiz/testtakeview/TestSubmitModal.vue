/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'

const open = defineModel<boolean>({ required: true })

const emit = defineEmits<{
  confirm: []
}>()

const { t } = useI18n()
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <SectionHeader>{{ t('quiz.attempt.confirmSubmitTitle') }}</SectionHeader>
      <p class="text-sm">{{ t('quiz.attempt.confirmSubmitMessage') }}</p>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <SuccessButton @click="emit('confirm')">{{ t('quiz.attempt.submit') }}</SuccessButton>
      </div>
    </div>
  </Modal>
</template>
