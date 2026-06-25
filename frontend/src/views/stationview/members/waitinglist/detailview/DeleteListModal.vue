/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 *
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const props = defineProps<{
  modelValue: boolean
  listName: string | undefined
  deleting: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm'): void
}>()

const { t } = useI18n()

const open = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <SectionHeader>{{ t('waitingList.deleteListTitle') }}</SectionHeader>
      <p class="text-sm">{{ t('waitingList.deleteListConfirm', { name: listName }) }}</p>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton :disabled="deleting" @click="emit('confirm')">
          {{ deleting ? t('common.loading') : t('waitingList.deleteList') }}
        </ErrorButton>
      </div>
    </div>
  </Modal>
</template>
