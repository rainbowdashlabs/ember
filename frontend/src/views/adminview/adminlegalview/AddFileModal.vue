/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'

const {t} = useI18n()

const show = defineModel<boolean>('show', {required: true})
const name = defineModel<string>('name', {required: true})

const emit = defineEmits<{
  confirm: []
}>()
</script>

<template>
  <Modal v-model="show">
    <div class="space-y-4">
      <SubHeader>{{ t('adminSettings.legal.addFileTitle') }}</SubHeader>
      <TextInput v-model="name" :placeholder="t('adminSettings.legal.fileNamePlaceholder')"/>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!name.trim()" @click="emit('confirm')">
          {{ t('adminSettings.legal.addFile') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
