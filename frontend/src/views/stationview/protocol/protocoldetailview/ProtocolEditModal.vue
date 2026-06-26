/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const visible = defineModel<boolean>('visible', { required: true })
const name = defineModel<string>('name', { required: true })
const description = defineModel<string>('description', { required: true })
const passThreshold = defineModel<number | undefined>('passThreshold')

defineEmits<{
  (e: 'submit'): void
}>()

const { t } = useI18n()
</script>

<template>
  <Modal v-model="visible">
    <SubHeader class="mb-3">{{ t('common.edit') }}</SubHeader>
    <form class="space-y-3" @submit.prevent="$emit('submit')">
      <TextInput v-model="name" :placeholder="t('protocol.name')" required />
      <TextAreaInput v-model="description" :placeholder="t('protocol.description')" />
      <div>
        <FieldLabel class="mb-1">{{ t('protocol.passThreshold') }}</FieldLabel>
        <NumberInput v-model="passThreshold" />
        <p class="text-xs text-[var(--text-muted)] mt-1">{{ t('protocol.passThresholdHint') }}</p>
      </div>
      <div class="flex gap-2 justify-end">
        <PrimaryButton type="submit">{{ t('common.save') }}</PrimaryButton>
      </div>
    </form>
  </Modal>
</template>
