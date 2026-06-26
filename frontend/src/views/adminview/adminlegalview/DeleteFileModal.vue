/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const {t} = useI18n()

defineProps<{
  displayName: string
}>()

const show = defineModel<boolean>('show', {required: true})

const emit = defineEmits<{
  confirm: []
}>()
</script>

<template>
  <Modal v-model="show">
    <div class="space-y-4">
      <SectionHeader>{{ t('adminSettings.legal.deleteFileTitle') }}</SectionHeader>
      <p class="text-sm">{{ t('adminSettings.legal.deleteFileConfirm', { name: displayName }) }}</p>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
        <DeleteButton @click="emit('confirm')">{{ t('common.delete') }}</DeleteButton>
      </div>
    </div>
  </Modal>
</template>
