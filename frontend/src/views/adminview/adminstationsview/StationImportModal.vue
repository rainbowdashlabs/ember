/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

defineProps<{
  importing: boolean
}>()

const emit = defineEmits<{
  start: []
}>()

const open = defineModel<boolean>({required: true})
const token = defineModel<string>('token', {required: true})

const {t} = useI18n()
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <p class="text-sm text-(--text-muted)">{{ t('adminStations.importHint') }}</p>
      <div class="space-y-1">
        <FieldLabel>{{ t('adminStations.importToken') }}</FieldLabel>
        <TextInput v-model="token" :placeholder="t('adminStations.importTokenPlaceholder')"/>
      </div>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="open = false">{{ t('adminStations.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="importing || !token" @click="emit('start')">
          {{ importing ? t('adminStations.importStarting') : t('adminStations.importStart') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
