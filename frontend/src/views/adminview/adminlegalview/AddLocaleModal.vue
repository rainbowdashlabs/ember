/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'

const {t} = useI18n()

const show = defineModel<boolean>('show', {required: true})
const code = defineModel<string>('code', {required: true})

const emit = defineEmits<{
  confirm: []
}>()
</script>

<template>
  <Modal v-model="show">
    <div class="space-y-4">
      <SectionHeader>{{ t('adminSettings.legal.addLocaleTitle') }}</SectionHeader>
      <TextInput v-model="code" :placeholder="t('adminSettings.legal.localeCodePlaceholder')"/>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!code.trim()" @click="emit('confirm')">
          {{ t('adminSettings.legal.addLocale') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
