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

const showFolder = defineModel<boolean>('showFolder', {required: true})
const showFile = defineModel<boolean>('showFile', {required: true})

const emit = defineEmits<{
  confirmFolder: []
  confirmFile: []
}>()

const {t} = useI18n()
</script>

<template>
  <Modal v-model="showFolder">
    <SubHeader class="mb-3">{{ t('kb.deleteFolder') }}</SubHeader>
    <p class="mb-4">{{ t('kb.deleteFolderConfirm') }}</p>
    <div class="flex gap-2 justify-end">
      <SecondaryButton @click="showFolder = false">{{ t('common.cancel') }}</SecondaryButton>
      <DeleteButton :label="t('common.delete')" @click="emit('confirmFolder')">
        {{ t('common.delete') }}
      </DeleteButton>
    </div>
  </Modal>

  <Modal v-model="showFile">
    <SubHeader class="mb-3">{{ t('kb.deleteFile') }}</SubHeader>
    <p class="mb-4">{{ t('kb.deleteFileConfirm') }}</p>
    <div class="flex gap-2 justify-end">
      <SecondaryButton @click="showFile = false">{{ t('common.cancel') }}</SecondaryButton>
      <DeleteButton :label="t('common.delete')" @click="emit('confirmFile')">
        {{ t('common.delete') }}
      </DeleteButton>
    </div>
  </Modal>
</template>
