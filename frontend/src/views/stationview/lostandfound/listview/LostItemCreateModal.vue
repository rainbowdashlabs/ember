/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import LostItemImageField from './LostItemImageField.vue'
import {todayIsoDate} from '@/util/format'

export interface LostItemCreatePayload {
  description: string
  foundAt: string
  imageFile: File | null
}

const visible = defineModel<boolean>({required: true})

const props = defineProps<{
  creating: boolean
  /**
   * True once the entry itself is saved and only its picture is still missing. The report is not
   * offered a second time then: pressing again used to file a second entry while the first stood
   * in the list without a picture.
   */
  savedWithoutImage: boolean
  error?: string
}>()

const emit = defineEmits<{
  (e: 'submit', payload: LostItemCreatePayload): void
  (e: 'close'): void
}>()

const {t} = useI18n()

const newDescription = ref('')
const newFoundAt = ref(todayIsoDate())
const newImageFile = ref<File | null>(null)
const imageField = ref<InstanceType<typeof LostItemImageField> | null>(null)

function resetForm() {
  newDescription.value = ''
  newFoundAt.value = todayIsoDate()
  imageField.value?.clear()
  newImageFile.value = null
}

function cancel() {
  emit('close')
  resetForm()
}

function submit() {
  emit('submit', {
    description: newDescription.value,
    foundAt: newFoundAt.value,
    imageFile: newImageFile.value,
  })
}

const submitLabel = computed(() => {
  if (props.creating) return t('common.loading')
  return props.savedWithoutImage ? t('lostAndFound.retryImage') : t('lostAndFound.create')
})

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

      <Alert v-if="savedWithoutImage" variant="info" data-testid="saved-without-image">
        {{ t('lostAndFound.savedWithoutImage') }}
      </Alert>
      <Alert v-if="error" variant="error" data-testid="create-error">{{ error }}</Alert>

      <LostItemImageField ref="imageField" v-model="newImageFile"/>

      <div class="space-y-1">
        <FieldLabel>{{ t('lostAndFound.description') }}</FieldLabel>
        <TextAreaInput v-model="newDescription" :disabled="savedWithoutImage"
                       :placeholder="t('lostAndFound.descriptionPlaceholder')"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('lostAndFound.foundAt') }}</FieldLabel>
        <DateInput v-model="newFoundAt" :disabled="savedWithoutImage"/>
      </div>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="cancel">
          {{ savedWithoutImage ? t('lostAndFound.keepWithoutImage') : t('common.cancel') }}
        </SecondaryButton>
        <PrimaryButton :disabled="creating || (savedWithoutImage && !newImageFile)" @click="submit">
          {{ submitLabel }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
