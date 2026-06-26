/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

defineProps<{
  saving: boolean
}>()

const emit = defineEmits<{
  confirm: []
}>()

const open = defineModel<boolean>({required: true})
const keepSource = defineModel<boolean>('keepSource', {required: true})

const {t} = useI18n()
</script>

<template>
  <Modal v-model="open" size="md">
    <div class="space-y-4">
      <SubHeader>{{ t('adminStorageBackend.confirm.title') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('adminStorageBackend.confirm.body') }}</MutedText>
      <FieldLabel inline>
        <ToggleInput v-model="keepSource"/>
        <span>{{ t('adminStorageBackend.confirm.keepSource') }}</span>
      </FieldLabel>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="open = false">
          {{ t('adminStorageBackend.confirm.cancel') }}
        </SecondaryButton>
        <PrimaryButton :disabled="saving" @click="emit('confirm')">
          {{ t('adminStorageBackend.confirm.confirm') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
