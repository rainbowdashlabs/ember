/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

const editing = defineModel<number | null>('editing', {required: true})
const editAlt = defineModel<string>('editAlt', {required: true})
const editDesc = defineModel<string>('editDesc', {required: true})

const emit = defineEmits<{
  (e: 'save'): void
  (e: 'cancel'): void
}>()

const {t} = useI18n()
</script>

<template>
  <Modal v-if="editing != null" :model-value="editing != null" size="md"
         @update:model-value="(v: boolean) => { if (!v) emit('cancel') }">
    <div class="space-y-3">
      <SubHeader>{{ t('stationPages.editor.editFileMeta') }}</SubHeader>
      <TextInput v-model="editAlt" :placeholder="t('stationPages.editor.altText')"/>
      <TextInput v-model="editDesc" :placeholder="t('stationPages.editor.imageDescription')"/>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton @click="emit('save')">{{ t('common.save') }}</PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
