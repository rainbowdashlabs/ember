/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import type {ConversationMessageResponse, ReportedMessageResponse} from '@/api/generated/schema'
import {dismissReport, removeReported} from '@/api/messages'
import {useMessageWords} from '@/composables/useMessageWords'
import {plainTextOf} from '@/util/messageText'
import {formatDateTime} from '@/util/format'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * One reported message: the chat it is in, who reported it and why, the text as it was reported,
 * and the conversation around it, with the reported message marked. It is removed for the station,
 * after asking, or the report is dismissed; either handles every report of that message.
 */
const props = defineProps<{
  report: ReportedMessageResponse
  onHandled: () => Promise<void> | void
}>()

const {t} = useI18n()
const {nameOf, systemLine} = useMessageWords()
const failure = ref<Failure | null>(null)
const busy = ref(false)
const confirmingRemoval = ref(false)

function lineOf(message: ConversationMessageResponse): string {
  if (message.systemKind) return systemLine({...message, text: message.content})
  if (message.removedByStation) return t('messages.removedByStation')
  if (message.deleted) return t('messages.deleted')
  return plainTextOf(message.content)
}

async function handle(action: (reportId: number) => Promise<void>) {
  if (busy.value) return
  busy.value = true
  failure.value = null
  try {
    await action(props.report.id)
    await props.onHandled()
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <NeutralContainer class="space-y-3" :data-testid="`message-report-${report.id}`">
    <div>
      <p class="font-semibold">{{ report.chatName }}</p>
      <MutedText tag="p">
        {{ t('messageReports.reportedBy', {name: nameOf(report.reporter), date: formatDateTime(report.reportedAt)}) }}
      </MutedText>
    </div>
    <p v-if="report.reason" class="text-sm">{{ t('messageReports.reason', {reason: report.reason}) }}</p>
    <div class="space-y-1">
      <MutedText tag="p">{{ t('messageReports.reportedText') }}</MutedText>
      <p class="whitespace-pre-line rounded-theme border-l-4 border-error px-3 py-1 text-sm">{{ plainTextOf(report.reportedText) }}</p>
    </div>
    <div class="space-y-1">
      <MutedText tag="p">{{ t('messageReports.context') }}</MutedText>
      <ul class="space-y-1">
        <li v-for="message in report.context" :key="message.id"
            :class="message.id === report.messageId ? 'bg-error/10 font-medium' : ''"
            class="rounded-theme px-2 py-1 text-sm">
          <span v-if="!message.systemKind" class="font-semibold">{{ nameOf(message.author) }}: </span>
          <span class="whitespace-pre-line">{{ lineOf(message) }}</span>
        </li>
      </ul>
    </div>
    <FailureAlert :failure="failure"/>
    <ButtonRow pair align="end">
      <SecondaryButton :icon="['fas', 'check']" :disabled="busy" :data-testid="`message-report-dismiss-${report.id}`"
                       @click="handle(dismissReport)">
        {{ t('messageReports.dismiss') }}
      </SecondaryButton>
      <ErrorButton :icon="['fas', 'trash']" :disabled="busy" :data-testid="`message-report-remove-${report.id}`"
                   @click="confirmingRemoval = true">
        {{ t('messageReports.remove') }}
      </ErrorButton>
    </ButtonRow>
    <ConfirmDeleteModal v-model="confirmingRemoval" :title="t('messages.moderateConfirm.title')"
                        :message="t('messageReports.removeConfirm')" :confirm-label="t('messageReports.remove')"
                        @confirm="handle(removeReported)"/>
  </NeutralContainer>
</template>
