/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const {t} = useI18n()

const open = defineModel<boolean>({required: true})

defineProps<{label: string}>()

const emit = defineEmits<{
  (e: 'confirm'): void
}>()
</script>

<template>
  <Modal v-model="open" size="sm">
    <div class="space-y-4 p-4">
      <SubHeader>{{ t('passkeys.section.removeTitle') }}</SubHeader>
      <p class="text-sm">{{ t('passkeys.section.removeConfirm', {label}) }}</p>
      <div class="flex justify-between gap-2">
        <SecondaryButton type="button" @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton type="button" @click="emit('confirm')">{{ t('passkeys.section.remove') }}</PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
