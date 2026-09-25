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
import {mailImport} from '@/api'
import type {Mailbox, MailImportSettings, MailboxRequest} from '@/api/mailImport'
import {describeFailure, type Failure} from '@/util/failure'

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
const loading = ref(true)
const failure = ref<Failure | null>(null)

const editing = ref<Mailbox | null>(null)
const adding = ref(false)

const switchedOff = computed(() => settings.value !== null && !settings.value.enabled)
const cannotStorePasswords = computed(() => settings.value !== null && !settings.value.canStorePasswords)

/** Records the failure, with a sentence of its own where this screen has a better one. */
function record(e: unknown, message?: string) {
  const described = describeFailure(e, t)
  failure.value = message ? {...described, message} : described
}

async function reload(staleMessage?: string) {
  try {
    const [loadedSettings, loadedMailboxes] = await Promise.all([mailImport.settings(), mailImport.listMailboxes()])
    settings.value = loadedSettings
    mailboxes.value = loadedMailboxes
  } catch (e) {
    record(e, staleMessage)
  }
  loading.value = false
}

/**
 * Carries the change out, then reloads.
 *
 * <p>Answered for separately, because a mailbox that was saved and a page that then failed to come
 * back would otherwise both read as a mailbox that was not saved.
 */
async function act(action: Promise<unknown>) {
  failure.value = null
  try {
    await action
  } catch (e) {
    record(e)
    return
  }
  await reload(t('failure.staleAfterAction'))
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

reload()
</script>

<template>
  <ViewContent :title="t('pages.station-mail-import.title')" :subtitle="t('pages.station-mail-import.subtitle')">
    <div class="space-y-6">
      <FailureAlert :failure="failure"/>

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
            :supported-types="settings?.supportedTypes ?? []"
            @edit="edit"
            @changed="reload()"
            @error="failure = $event"
        />

        <MailImportLogPanel/>
      </template>
    </div>
  </ViewContent>
</template>
