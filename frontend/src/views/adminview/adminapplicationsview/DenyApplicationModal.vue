/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import type {StationApplication} from '@/api/stationApplications'

const open = defineModel<boolean>({required: true})
const reason = defineModel<string>('reason', {required: true})

defineProps<{
  target: StationApplication | null
  processing: boolean
}>()

const emit = defineEmits<{
  submit: []
}>()

const {t} = useI18n()
</script>

<template>
  <Modal v-model="open">
    <form class="space-y-4" @submit.prevent="emit('submit')">
      <SectionHeader>{{ t('adminApplications.denyTitle') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">
        {{ target?.firstName }} {{ target?.lastName }} — {{ target?.stationName }}
      </p>
      <div class="space-y-1">
        <FieldLabel>{{ t('adminApplications.denyReasonLabel') }}</FieldLabel>
        <TextInput v-model="reason" :placeholder="t('adminApplications.denyReasonPlaceholder')"/>
      </div>
      <div class="flex justify-end gap-3">
        <SecondaryButton type="button" @click="open = false">
          {{ t('common.cancel') }}
        </SecondaryButton>
        <ErrorButton :disabled="processing" type="submit">
          {{ processing ? t('common.loading') : t('adminApplications.deny') }}
        </ErrorButton>
      </div>
    </form>
  </Modal>
</template>
