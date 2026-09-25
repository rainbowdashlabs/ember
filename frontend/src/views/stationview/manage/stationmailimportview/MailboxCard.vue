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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {Mailbox, MailboxTestResult} from '@/api/mailImport'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {describeFailure, type Failure} from '@/util/failure'

/** One mailbox: how it is doing, what it is set to, and the rules under it. */
const props = defineProps<{
  mailbox: Mailbox
  supportedTypes: string[]
}>()

const emit = defineEmits<{
  edit: [mailbox: Mailbox]
  changed: []
  error: [failure: Failure]
}>()

const {t} = useI18n()

const testResult = ref<MailboxTestResult | null>(null)
const cycleSummary = ref('')

/**
 * Tries the mailbox and keeps whatever the provider said about it.
 *
 * <p>This is the one control on the page that exists to diagnose, so the provider's own refusal,
 * a rejected password, a folder that is not there, a host that will not answer, is the whole
 * answer. It used to be replaced by "that did not work", and then not shown at all.
 */
const {running: testing, failure: testFailure, run: test} = useAsyncAction(async () => {
  cycleSummary.value = ''
  testResult.value = await mailImport.testMailbox(props.mailbox.id)
})

const {running: importing, failure: importFailure, run: runNow} = useAsyncAction(async () => {
  testResult.value = null
  const cycle = await mailImport.runNow(props.mailbox.id)
  cycleSummary.value = t('mailImport.cycleSummary', {
    looked: cycle.looked,
    imported: cycle.imported,
    refused: cycle.refused,
  })
  emit('changed')
})

const {running: resuming, failure: resumeFailure, run: resume} = useAsyncAction(async () => {
  await mailImport.resumeMailbox(props.mailbox.id)
  emit('changed')
})

const busy = computed(() => testing.value || importing.value || resuming.value)

async function remove() {
  try {
    await mailImport.deleteMailbox(props.mailbox.id)
    emit('changed')
  } catch (e) {
    emit('error', describeFailure(e, t))
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

      <FailureAlert :failure="testFailure"/>
      <FailureAlert :failure="importFailure"/>
      <FailureAlert :failure="resumeFailure"/>

      <Alert v-if="cycleSummary" variant="success">{{ cycleSummary }}</Alert>

      <MailboxTestReport v-if="testResult" :folder="mailbox.folder" :result="testResult"/>

      <MailRuleList
          :mailbox-id="mailbox.id"
          :supported-types="supportedTypes"
          @error="emit('error', $event)"
      />
    </div>
  </NeutralContainer>
</template>
