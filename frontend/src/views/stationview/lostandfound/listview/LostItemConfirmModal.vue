/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'

const visible = defineModel<boolean>({required: true})

defineProps<{
  title: string
  message: string
  confirmLabel: string
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'confirm'): void
}>()

const {t} = useI18n()
</script>

<template>
  <Modal v-model="visible">
    <div class="space-y-4 p-4">
      <SubHeader>{{ title }}</SubHeader>
      <p class="text-sm text-(--text-muted)">{{ message }}</p>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="visible = false">{{ t('common.cancel') }}</SecondaryButton>
        <SuccessButton :disabled="loading" @click="emit('confirm')">
          {{ loading ? t('common.loading') : confirmLabel }}
        </SuccessButton>
      </div>
    </div>
  </Modal>
</template>
