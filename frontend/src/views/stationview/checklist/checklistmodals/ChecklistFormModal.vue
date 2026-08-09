/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const visible = defineModel<boolean>({required: true})
const name = defineModel<string>('name', {required: true})
const description = defineModel<string>('description', {required: true})

withDefaults(defineProps<{
  title: string
  size?: 'sm' | 'md' | 'lg' | 'xl' | '2xl' | 'full'
  descriptionRows?: number
  submitDisabled?: boolean
}>(), {
  size: 'md',
  descriptionRows: 2,
  submitDisabled: false,
})

const emit = defineEmits<{
  (e: 'submit'): void
}>()

const {t} = useI18n()
</script>

<template>
  <Modal v-model="visible" :size="size">
    <div class="space-y-4">
      <SubHeader>{{ title }}</SubHeader>

      <div>
        <FieldLabel>{{ t('checklist.name') }}</FieldLabel>
        <TextInput v-model="name" :placeholder="t('checklist.namePlaceholder')"/>
      </div>

      <div>
        <FieldLabel>{{ t('checklist.description') }}</FieldLabel>
        <TextAreaInput
            v-model="description"
            :placeholder="t('checklist.descriptionPlaceholder')"
            :rows="descriptionRows"
        />
      </div>

      <slot/>

      <div class="flex justify-end gap-2 pt-2">
        <SecondaryButton @click="visible = false">{{ t('checklist.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="submitDisabled" @click="emit('submit')">
          {{ t('checklist.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
