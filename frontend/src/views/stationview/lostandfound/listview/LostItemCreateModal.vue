/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import {todayIsoDate} from '@/util/format'

export interface LostItemCreatePayload {
  description: string
  foundAt: string
  imageFile: File | null
}

const visible = defineModel<boolean>({required: true})

defineProps<{
  creating: boolean
}>()

const emit = defineEmits<{
  (e: 'submit', payload: LostItemCreatePayload): void
}>()

const {t} = useI18n()

const newDescription = ref('')
const newFoundAt = ref(todayIsoDate())
const newImageFile = ref<File | null>(null)
const newImagePreview = ref<string | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const cameraInputRef = ref<HTMLInputElement | null>(null)

function handleImageSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  newImageFile.value = file
  if (newImagePreview.value) URL.revokeObjectURL(newImagePreview.value)
  newImagePreview.value = URL.createObjectURL(file)
  input.value = ''
}

function clearNewImage() {
  newImageFile.value = null
  if (newImagePreview.value) {
    URL.revokeObjectURL(newImagePreview.value)
    newImagePreview.value = null
  }
}

function resetForm() {
  newDescription.value = ''
  newFoundAt.value = todayIsoDate()
  clearNewImage()
}

function cancel() {
  visible.value = false
  resetForm()
}

function submit() {
  emit('submit', {
    description: newDescription.value,
    foundAt: newFoundAt.value,
    imageFile: newImageFile.value,
  })
}

watch(visible, (value, previous) => {
  if (previous && !value) {
    resetForm()
  }
})
</script>

<template>
  <Modal v-model="visible">
    <div class="space-y-4 p-4">
      <SubHeader>{{ t('lostAndFound.createTitle') }}</SubHeader>

      <div class="space-y-2">
        <FieldLabel>{{ t('lostAndFound.image') }}</FieldLabel>
        <div v-if="newImagePreview" class="relative">
          <img :src="newImagePreview" alt="" class="w-full max-h-48 object-cover rounded-lg"/>
          <IconButton
            :icon="['fas', 'xmark']"
            label="Remove image"
            class="absolute top-2 right-2 bg-error text-error-text rounded-full h-6 w-6 hover:bg-error/80"
            @click="clearNewImage"
          />
        </div>
        <div v-else class="flex gap-2">
          <SecondaryButton :icon="['fas', 'upload']" class="flex-1" @click="fileInputRef?.click()">
            {{ t('lostAndFound.uploadImage') }}
          </SecondaryButton>
          <SecondaryButton :icon="['fas', 'camera']" class="flex-1" @click="cameraInputRef?.click()">
            {{ t('lostAndFound.takePhoto') }}
          </SecondaryButton>
        </div>
        <input ref="fileInputRef" type="file" accept="image/png,image/jpeg,image/webp" class="hidden"
               @change="handleImageSelected"/>
        <input ref="cameraInputRef" type="file" accept="image/*" capture="environment" class="hidden"
               @change="handleImageSelected"/>
      </div>

      <div class="space-y-1">
        <FieldLabel>{{ t('lostAndFound.description') }}</FieldLabel>
        <TextAreaInput v-model="newDescription" :placeholder="t('lostAndFound.descriptionPlaceholder')"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('lostAndFound.foundAt') }}</FieldLabel>
        <DateInput v-model="newFoundAt"/>
      </div>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="cancel">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="creating" @click="submit">
          {{ creating ? t('common.loading') : t('lostAndFound.create') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
