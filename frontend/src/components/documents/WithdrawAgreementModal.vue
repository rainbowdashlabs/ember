/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import {signing} from '@/api'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * Withdraws a signed agreement, with an optional reason. What was signed stays as evidence; the withdrawal
 * is sealed into the document, and whoever runs the appointment or asked for the signatures is told.
 */
const open = defineModel<boolean>({required: true})

const props = defineProps<{
  requestUid: string
}>()

const emit = defineEmits<{
  withdrawn: []
}>()

const {t} = useI18n()

const MAX_REASON_LENGTH = 500

const reason = ref('')

const withdrawing = useAsyncAction(async () => {
  await signing.withdrawAgreement(props.requestUid, reason.value)
  open.value = false
  emit('withdrawn')
})
</script>

<template>
  <Modal v-model="open">
    <form class="space-y-4" data-testid="agreement-withdraw-form" @submit.prevent="withdrawing.run()">
      <SubHeader>{{ t('events.documents.withdraw.title') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('events.documents.withdraw.hint') }}</MutedText>
      <FailureAlert :failure="withdrawing.failure.value"/>
      <div class="space-y-1">
        <FieldLabel>{{ t('events.documents.withdraw.reason') }}</FieldLabel>
        <TextAreaInput v-model="reason" :maxlength="MAX_REASON_LENGTH"/>
      </div>
      <ButtonRow pair align="end">
        <SecondaryButton type="button" @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton :disabled="withdrawing.running.value" type="submit" data-testid="agreement-withdraw-confirm">
          {{ t('events.documents.withdraw.confirm') }}
        </ErrorButton>
      </ButtonRow>
    </form>
  </Modal>
</template>
