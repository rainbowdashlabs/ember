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
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const modelValue = defineModel<boolean>({required: true})
const name = defineModel<string>('name', {required: true})
const description = defineModel<string>('description', {required: true})

defineProps<{
  creating: boolean
}>()

const emit = defineEmits<{
  submit: []
}>()

const { t } = useI18n()
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-4">
      <SectionHeader>{{ t('waitingList.createTitle') }}</SectionHeader>
      <div class="space-y-1">
        <FieldLabel>{{ t('waitingList.name') }}</FieldLabel>
        <TextInput v-model="name" :placeholder="t('waitingList.namePlaceholder')" />
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('waitingList.description') }}</FieldLabel>
        <TextAreaInput v-model="description" :placeholder="t('waitingList.descriptionPlaceholder')" />
      </div>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="creating || !name.trim()" @click="emit('submit')">
          {{ creating ? t('common.loading') : t('waitingList.create') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
