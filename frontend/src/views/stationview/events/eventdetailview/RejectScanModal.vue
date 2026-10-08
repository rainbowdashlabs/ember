/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'

/**
 * Turns a scan handed in down with a short reason, which the participant and their guardians read in
 * their notification. The document is open again for a new scan.
 */
const open = defineModel<boolean>({required: true})
const reason = defineModel<string>('reason', {required: true})

defineProps<{
  processing: boolean
}>()

const emit = defineEmits<{
  submit: []
}>()

const {t} = useI18n()

const MAX_REASON_LENGTH = 300
</script>

<template>
  <Modal v-model="open">
    <form class="space-y-4" data-testid="document-scan-reject-form" @submit.prevent="emit('submit')">
      <SubHeader>{{ t('events.documents.rejectTitle') }}</SubHeader>
      <div class="space-y-1">
        <FieldLabel>{{ t('events.documents.rejectReason') }}</FieldLabel>
        <TextAreaInput v-model="reason" :maxlength="MAX_REASON_LENGTH"/>
        <MutedText tag="p" size="sm">{{ t('events.documents.rejectReasonHint') }}</MutedText>
      </div>
      <ButtonRow pair align="end">
        <SecondaryButton type="button" @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton :disabled="processing || !reason.trim()" type="submit">{{ t('events.documents.scanReject') }}</ErrorButton>
      </ButtonRow>
    </form>
  </Modal>
</template>
