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
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const modelValue = defineModel<boolean>({required: true})
const name = defineModel<string>('name', {required: true})
const description = defineModel<string>('description', {required: true})
const trainingEnabled = defineModel<boolean>('trainingEnabled', {required: true})

const emit = defineEmits<{
  submit: []
}>()

const { t } = useI18n()
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-4">
      <SubHeader>{{ t('quiz.catalogs.create') }}</SubHeader>
      <TextInput v-model="name" :placeholder="t('quiz.catalogs.name')" />
      <TextAreaInput v-model="description" :placeholder="t('quiz.catalogs.description')" />
      <FieldLabel inline>
        <ToggleInput v-model="trainingEnabled" />
        {{ t('quiz.catalogs.trainingEnabled') }}
      </FieldLabel>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!name.trim()" @click="emit('submit')">{{ t('common.save') }}</PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
