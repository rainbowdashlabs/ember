/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import TransferSection from '@/views/stationview/manage/stationview/TransferSection.vue'
import StationImportSection from '@/views/stationview/manage/stationview/StationImportSection.vue'
import ImportProgressChecklist from '@/components/transfer/ImportProgressChecklist.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {PitchTransfer} from './pitchTypes'
import type {Failure} from '@/util/failure'

/** Moving a station between instances, drawn by the application's own transfer sections. */
defineProps<{
  transfer: PitchTransfer
}>()

/**
 * What the two sections have to say, which nothing here used to listen for.
 *
 * <p>Both report everything they do through these two events and draw nothing themselves, so with no
 * listener bound a transfer token that could not be created and an import that failed outright said
 * absolutely nothing: the reader pressed the button and the page sat there. Nothing warned about it,
 * because an event nobody listens to is perfectly legal.
 */
const message = ref('')
const failure = ref<Failure | null>(null)

function onSuccess(text: string) {
  message.value = text
  failure.value = null
}

function onError(reported: Failure) {
  failure.value = reported
  message.value = ''
}
</script>

<template>
  <template v-if="transfer.progress">
    <ImportProgressChecklist :progress="transfer.progress"/>
  </template>
  <template v-else>
    <TransferSection @success="onSuccess" @error="onError"/>
    <StationImportSection @success="onSuccess" @error="onError"/>
    <Alert v-if="message" variant="success">{{ message }}</Alert>
    <FailureAlert :failure="failure"/>
  </template>
</template>
