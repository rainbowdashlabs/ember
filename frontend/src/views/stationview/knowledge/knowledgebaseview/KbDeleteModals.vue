/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

defineProps<{
  showFolder: boolean
  showFile: boolean
}>()

const emit = defineEmits<{
  'update:showFolder': [value: boolean]
  'update:showFile': [value: boolean]
  confirmFolder: []
  confirmFile: []
}>()

const {t} = useI18n()
</script>

<template>
  <!-- Delete Folder Confirmation -->
  <Modal :model-value="showFolder" @update:model-value="emit('update:showFolder', $event)">
    <SubHeader class="mb-3">{{ t('kb.deleteFolder') }}</SubHeader>
    <p class="mb-4">{{ t('kb.deleteFolderConfirm') }}</p>
    <div class="flex gap-2 justify-end">
      <SecondaryButton @click="emit('update:showFolder', false)">{{ t('common.cancel') }}</SecondaryButton>
      <DeleteButton :label="t('common.delete')" @click="emit('confirmFolder')">
        {{ t('common.delete') }}
      </DeleteButton>
    </div>
  </Modal>

  <!-- Delete File Confirmation -->
  <Modal :model-value="showFile" @update:model-value="emit('update:showFile', $event)">
    <SubHeader class="mb-3">{{ t('kb.deleteFile') }}</SubHeader>
    <p class="mb-4">{{ t('kb.deleteFileConfirm') }}</p>
    <div class="flex gap-2 justify-end">
      <SecondaryButton @click="emit('update:showFile', false)">{{ t('common.cancel') }}</SecondaryButton>
      <DeleteButton :label="t('common.delete')" @click="emit('confirmFile')">
        {{ t('common.delete') }}
      </DeleteButton>
    </div>
  </Modal>
</template>
