/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {EventCategory} from '@/api/types'

const {t} = useI18n()

const modelValue = defineModel<boolean>({required: true})

const props = defineProps<{
  category: EventCategory | null
}>()

const emit = defineEmits<{
  save: [name: string]
}>()

const categoryName = ref('')

watch(modelValue, (open) => {
  if (!open) return
  categoryName.value = props.category?.name ?? ''
})

function submit() {
  emit('save', categoryName.value)
}
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-4">
      <SubHeader>{{ category ? t('events.editCategory') : t('events.addCategory') }}</SubHeader>
      <div class="space-y-1">
        <FieldLabel>{{ t('events.categoryName') }}</FieldLabel>
        <TextInput v-model="categoryName" :placeholder="t('events.categoryNamePlaceholder')"/>
      </div>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!categoryName" @click="submit">
          {{ t('common.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
