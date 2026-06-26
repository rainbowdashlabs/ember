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

const visible = defineModel<boolean>('visible', { required: true })
const name = defineModel<string>('name', { required: true })
const description = defineModel<string>('description', { required: true })
const maxPoints = defineModel<number | undefined>('maxPoints')
const passThreshold = defineModel<number | undefined>('passThreshold')

defineProps<{
  editing: boolean
}>()

defineEmits<{
  (e: 'submit'): void
}>()

const { t } = useI18n()
</script>

<template>
  <Modal v-model="visible">
    <SubHeader class="mb-3">{{ editing ? t('protocol.editSection') : t('protocol.addSection') }}</SubHeader>
    <form class="space-y-3" @submit.prevent="$emit('submit')">
      <TextInput v-model="name" :placeholder="t('protocol.sectionName')" required />
      <TextAreaInput v-model="description" :placeholder="t('protocol.description')" />
      <div>
        <label class="block text-sm mb-1">{{ t('protocol.maxPoints') }}</label>
        <NumberInput v-model="maxPoints" />
      </div>
      <div>
        <label class="block text-sm mb-1">{{ t('protocol.passThreshold') }}</label>
        <NumberInput v-model="passThreshold" />
      </div>
      <div class="flex gap-2 justify-end">
        <PrimaryButton type="submit">{{ t('common.save') }}</PrimaryButton>
      </div>
    </form>
  </Modal>
</template>
