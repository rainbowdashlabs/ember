/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {ConversationMessageResponse} from '@/api/generated/schema'
import {reportMessage} from '@/api/messages'
import {showToast} from '@/util/toast'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * Reports a message of a station chat to the people handling the station's chats, with a reason if
 * the reader wants to give one.
 */
const open = defineModel<boolean>({required: true})

const props = defineProps<{
  conversationUid: string
  message: ConversationMessageResponse
}>()

const {t} = useI18n()
const reason = ref('')
const sending = ref(false)
const failure = ref<Failure | null>(null)

watch(open, isOpen => {
  if (!isOpen) return
  reason.value = ''
  failure.value = null
})

async function send() {
  sending.value = true
  failure.value = null
  try {
    await reportMessage(props.conversationUid, props.message.id, reason.value.trim() || null)
    open.value = false
    showToast(t('messages.report.sent'), 'success')
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <Modal v-model="open" size="md">
    <div class="space-y-3 p-4">
      <SubHeader>{{ t('messages.report.title') }}</SubHeader>
      <MutedText tag="p">{{ t('messages.report.text') }}</MutedText>
      <FieldLabel for="report-reason">{{ t('messages.report.reason') }}</FieldLabel>
      <TextAreaInput id="report-reason" v-model="reason" :rows="3" maxlength="500" data-testid="report-reason"/>
      <FailureAlert :failure="failure"/>
      <ButtonRow pair align="end">
        <SecondaryButton @click="open = false">{{ t('messages.composer.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="sending" :icon="['fas', 'flag']" data-testid="report-send" @click="send">
          {{ t('messages.report.send') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
