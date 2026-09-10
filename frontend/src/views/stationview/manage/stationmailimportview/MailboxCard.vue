/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Alert from '@/components/feedback/Alert.vue'
import MailboxHeader from './MailboxHeader.vue'
import MailboxTestReport from './MailboxTestReport.vue'
import MailRuleList from './MailRuleList.vue'
import {mailImport} from '@/api'
import type {Mailbox, MailboxTestResult} from '@/api/mailImport'
import type {StationMember} from '@/api/types'
import {useAsyncAction} from '@/composables/useAsyncAction'

/** One mailbox: how it is doing, what it is set to, and the rules under it. */
const props = defineProps<{
  mailbox: Mailbox
  members: StationMember[]
  supportedTypes: string[]
}>()

const emit = defineEmits<{
  edit: [mailbox: Mailbox]
  changed: []
  error: [message: string]
}>()

const {t} = useI18n()

const testResult = ref<MailboxTestResult | null>(null)
const cycleSummary = ref('')

const {running: testing, run: test} = useAsyncAction(async () => {
  cycleSummary.value = ''
  testResult.value = await mailImport.testMailbox(props.mailbox.id)
  return true
}, {formatError: () => t('common.error')})

const {running: importing, run: runNow} = useAsyncAction(async () => {
  testResult.value = null
  const cycle = await mailImport.runNow(props.mailbox.id)
  cycleSummary.value = t('mailImport.cycleSummary', {
    looked: cycle.looked,
    imported: cycle.imported,
    refused: cycle.refused,
  })
  emit('changed')
  return true
}, {formatError: () => t('common.error')})

const {running: resuming, run: resume} = useAsyncAction(async () => {
  await mailImport.resumeMailbox(props.mailbox.id)
  emit('changed')
  return true
}, {formatError: () => t('common.error')})

const busy = computed(() => testing.value || importing.value || resuming.value)

async function remove() {
  try {
    await mailImport.deleteMailbox(props.mailbox.id)
    emit('changed')
  } catch {
    emit('error', t('common.error'))
  }
}
</script>

<template>
  <NeutralContainer>
    <div class="space-y-4">
      <MailboxHeader
          :busy="busy"
          :mailbox="mailbox"
          @edit="emit('edit', mailbox)"
          @remove="remove"
          @resume="resume"
          @run="runNow"
          @test="test"
      />

      <ErrorContainer v-if="mailbox.lastError" padded>
        <MutedText size="sm" tag="p" class="break-words whitespace-pre-wrap">{{ mailbox.lastError }}</MutedText>
      </ErrorContainer>

      <Alert v-if="cycleSummary" variant="success">{{ cycleSummary }}</Alert>

      <MailboxTestReport v-if="testResult" :folder="mailbox.folder" :result="testResult"/>

      <MailRuleList
          :mailbox-id="mailbox.id"
          :members="members"
          :supported-types="supportedTypes"
          @error="emit('error', $event)"
      />
    </div>
  </NeutralContainer>
</template>
