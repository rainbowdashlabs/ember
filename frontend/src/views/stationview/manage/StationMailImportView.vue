/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MailboxCard from './stationmailimportview/MailboxCard.vue'
import MailboxEditor from './stationmailimportview/MailboxEditor.vue'
import MailImportLogPanel from './stationmailimportview/MailImportLogPanel.vue'
import {mailImport, stationMembers} from '@/api'
import type {Mailbox, MailImportSettings, MailboxRequest} from '@/api/mailImport'
import type {StationMember} from '@/api/types'

/**
 * What a station reads for paperwork: the mailboxes it watches, the rules under each of them, and what
 * actually happened.
 *
 * <p>The log is the feature rather than a diagnostic. A rule that quietly imports nothing is the likeliest
 * complaint this will produce, and the log is the only thing that can answer it.
 */
const {t} = useI18n()

const settings = ref<MailImportSettings | null>(null)
const mailboxes = ref<Mailbox[]>([])
const members = ref<StationMember[]>([])
const loading = ref(true)
const error = ref('')

const editing = ref<Mailbox | null>(null)
const adding = ref(false)

const switchedOff = computed(() => settings.value !== null && !settings.value.enabled)
const cannotStorePasswords = computed(() => settings.value !== null && !settings.value.canStorePasswords)

async function reload() {
  error.value = ''
  try {
    const [loadedSettings, loadedMailboxes] = await Promise.all([mailImport.settings(), mailImport.listMailboxes()])
    settings.value = loadedSettings
    mailboxes.value = loadedMailboxes
  } catch {
    error.value = t('common.error')
  }
  loading.value = false
}

async function loadMembers() {
  try {
    members.value = await stationMembers.listMembers()
  } catch {
    members.value = []
  }
}

async function act(action: Promise<unknown>) {
  error.value = ''
  try {
    await action
    await reload()
  } catch (e) {
    error.value = messageOf(e)
  }
}

/** The server's own words where it has any: a refused rule says exactly what is wrong with it. */
function messageOf(e: unknown): string {
  const message = (e as {response?: {data?: {message?: string}}})?.response?.data?.message
  return message && message.trim() ? message : t('common.error')
}

async function saveMailbox(request: MailboxRequest) {
  const existing = editing.value
  await act(existing ? mailImport.updateMailbox(existing.id, request) : mailImport.createMailbox(request))
  editing.value = null
  adding.value = false
}

function edit(mailbox: Mailbox) {
  editing.value = mailbox
  adding.value = false
}

function add() {
  editing.value = null
  adding.value = true
}

loadMembers()
reload()
</script>

<template>
  <ViewContent :title="t('pages.station-mail-import.title')" :subtitle="t('pages.station-mail-import.subtitle')">
    <div class="space-y-6">
      <FailureAlert :message="error"/>

      <Alert v-if="switchedOff" variant="info">{{ t('mailImport.switchedOffForInstance') }}</Alert>
      <Alert v-else-if="cannotStorePasswords" variant="error">{{ t('mailImport.noEncryptionKey') }}</Alert>

      <Spinner v-if="loading" size="md"/>

      <template v-else>
        <div class="flex flex-wrap items-center justify-between gap-3">
          <MutedText v-if="settings" size="sm" tag="p">
            {{ t('mailImport.floorHint', {minutes: settings.minimumIntervalMinutes}) }}
          </MutedText>
          <PrimaryButton :disabled="cannotStorePasswords" :icon="['fas', 'plus']" @click="add">
            {{ t('mailImport.addMailbox') }}
          </PrimaryButton>
        </div>

        <MailboxEditor
            v-if="adding || editing"
            :mailbox="editing"
            :minimum-interval="settings?.minimumIntervalMinutes ?? 15"
            @cancel="editing = null; adding = false"
            @save="saveMailbox"
        />

        <MutedText v-if="mailboxes.length === 0 && !adding" size="sm">{{ t('mailImport.noMailboxes') }}</MutedText>

        <MailboxCard
            v-for="mailbox in mailboxes"
            :key="mailbox.id"
            :mailbox="mailbox"
            :members="members"
            :supported-types="settings?.supportedTypes ?? []"
            @edit="edit"
            @changed="reload"
            @error="error = $event"
        />

        <MailImportLogPanel/>
      </template>
    </div>
  </ViewContent>
</template>
