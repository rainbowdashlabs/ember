/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import {PaperState} from '@/api/generated/schema'
import RequirementStatusBadge from './RequirementStatusBadge.vue'
import ScanRejection from './ScanRejection.vue'
import SignatureFieldList from '../eventshared/SignatureFieldList.vue'
import AgreementActions from './AgreementActions.vue'
import {SCAN_TYPES, type ParticipantCopy} from './documentTiles'

/**
 * The download of one participant's copy, with where it stands, and the hand-in of its signed scan until
 * a scan is confirmed. Named after the participant where the reader acts for more than one, since the
 * copies differ by whose data they hold. Where signatures were asked for on the copy, each field shows
 * where it stands, and the fields the reader can sign open the signing screen; its agreement can be signed
 * from here on an appointment without registrations, and withdrawn ({@link AgreementActions}).
 */
const props = defineProps<{
  eventId: number
  /** The date of the appointment on screen. */
  date: string
  copy: ParticipantCopy
  named: boolean
  busy: boolean
}>()

const emit = defineEmits<{
  fetch: []
  handIn: [file: File]
  changed: []
}>()

const {t} = useI18n()

const confirmed = computed(() => props.copy.document.paper?.state === PaperState.CONFIRMED)
</script>

<template>
  <div class="space-y-1" data-testid="document-to-bring">
    <div class="flex flex-wrap items-center gap-2">
      <RequirementStatusBadge :document="copy.document"/>
      <ButtonRow align="end" class="ml-auto">
        <SecondaryButton :icon="['fas', 'download']" :disabled="busy" data-testid="document-to-bring-download"
                         @click="emit('fetch')">
          {{ named ? t('events.documents.downloadFor', {name: copy.name}) : t('common.download') }}
        </SecondaryButton>
        <FileUploadButton v-if="!confirmed" :accept="SCAN_TYPES" :disabled="busy" data-testid="document-to-bring-scan"
                          @select="file => emit('handIn', file)">
          {{ t('events.documents.scanUpload') }}
        </FileUploadButton>
      </ButtonRow>
    </div>
    <SignatureFieldList v-if="copy.document.signature" :signature="copy.document.signature" offer-signing/>
    <AgreementActions :event-id="eventId" :date="date" :copy="copy" :busy="busy" @changed="emit('changed')"/>
    <ScanRejection :paper="copy.document.paper"/>
  </div>
</template>
