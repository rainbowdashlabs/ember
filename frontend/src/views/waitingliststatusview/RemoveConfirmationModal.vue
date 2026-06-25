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
import ErrorButton from '@/components/button/ErrorButton.vue'

const props = defineProps<{ removing: boolean }>()
const emit = defineEmits<{
  (e: 'confirm'): void
}>()

const open = defineModel<boolean>({required: true})

const { t } = useI18n()
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <SectionHeader>{{ t('waitingList.publicStatus.removeTitle') }}</SectionHeader>
      <p class="text-sm">{{ t('waitingList.publicStatus.removeConfirm') }}</p>
      <p class="text-xs text-(--text-muted)">{{ t('waitingList.publicStatus.removeHint') }}</p>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton :disabled="props.removing" @click="emit('confirm')">
          {{ props.removing ? t('common.loading') : t('waitingList.publicStatus.removeFromList') }}
        </ErrorButton>
      </div>
    </div>
  </Modal>
</template>
