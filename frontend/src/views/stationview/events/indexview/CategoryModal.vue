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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {EventCategory} from '@/api/types'

const {t} = useI18n()

const props = defineProps<{
  modelValue: boolean
  category: EventCategory | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  save: [name: string]
}>()

const categoryName = ref('')
const saving = ref(false)

watch(() => props.modelValue, (open) => {
  if (!open) return
  categoryName.value = props.category?.name ?? ''
})

function submit() {
  saving.value = true
  emit('save', categoryName.value)
  saving.value = false
}
</script>

<template>
  <Modal :model-value="modelValue" @update:model-value="emit('update:modelValue', $event)">
    <div class="space-y-4">
      <SectionHeader>{{ category ? t('events.editCategory') : t('events.addCategory') }}</SectionHeader>
      <div class="space-y-1">
        <label class="block text-sm font-medium">{{ t('events.categoryName') }}</label>
        <TextInput v-model="categoryName" :placeholder="t('events.categoryNamePlaceholder')"/>
      </div>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="saving || !categoryName" @click="submit">
          {{ saving ? t('common.loading') : t('common.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
