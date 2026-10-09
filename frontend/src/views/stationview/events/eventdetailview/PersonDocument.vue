/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import {PaperState} from '@/api/generated/schema'
import RequirementStatusBadge from './RequirementStatusBadge.vue'
import ScanRejection from './ScanRejection.vue'
import SignatureFieldList from '../eventshared/SignatureFieldList.vue'
import {fieldsToSign} from '../eventshared/requirementSignatures'
import AgreementActions from './AgreementActions.vue'
import {SCAN_TYPES, type ParticipantCopy} from './documentTiles'

/**
 * One document of one person: its name, then the person's copy to download and sign by hand, its signed
 * scan to upload ("Ersetzen" while one waits, which can also be taken back, and nothing once one is
 * confirmed) and, where the reader may sign
 * fields of the copy now, signing them online in one go. Below, where the copy stands and each of its
 * signature fields; its agreement can be signed from here on an appointment without registrations, and
 * withdrawn ({@link AgreementActions}).
 */
const props = defineProps<{
  eventId: number
  /** The date of the appointment on screen. */
  date: string
  copy: ParticipantCopy
  busy: boolean
}>()

const emit = defineEmits<{
  fetch: []
  handIn: [file: File]
  withdrawScan: []
  changed: []
}>()

const {t} = useI18n()
const router = useRouter()

const paper = computed(() => props.copy.document.paper?.state ?? null)
const toSign = computed(() => paper.value === PaperState.SUBMITTED ? [] : fieldsToSign(props.copy.document.signature))

function signOnline() {
  void router.push({name: 'station-signing-all', query: {fields: toSign.value.map(field => field.id).join(',')}})
}
</script>

<template>
  <div class="space-y-1" data-testid="document-to-bring">
    <span class="block text-sm font-medium">{{ copy.document.name }}</span>
    <ButtonRow>
      <SecondaryButton compact :icon="['fas', 'download']" :disabled="busy" data-testid="document-to-bring-download"
                       @click="emit('fetch')">
        {{ t('common.download') }}
      </SecondaryButton>
      <FileUploadButton v-if="paper !== PaperState.CONFIRMED" compact :accept="SCAN_TYPES" :disabled="busy"
                        data-testid="document-to-bring-scan" @select="file => emit('handIn', file)">
        {{ paper === PaperState.SUBMITTED ? t('events.documents.scanReplace') : t('common.upload') }}
      </FileUploadButton>
      <SecondaryButton v-if="paper === PaperState.SUBMITTED" compact :icon="['fas', 'rotate-left']" :disabled="busy"
                       data-testid="document-to-bring-scan-withdraw" @click="emit('withdrawScan')">
        {{ t('events.documents.scanWithdraw') }}
      </SecondaryButton>
      <PrimaryButton v-if="toSign.length > 0" compact :icon="['fas', 'file-signature']" :disabled="busy"
                     data-testid="document-to-bring-sign" @click="signOnline">
        {{ t('events.documents.signOnline') }}
      </PrimaryButton>
    </ButtonRow>
    <RequirementStatusBadge :document="copy.document"/>
    <SignatureFieldList v-if="copy.document.signature" :signature="copy.document.signature" :offer-signing="false"/>
    <AgreementActions :event-id="eventId" :date="date" :copy="copy" :busy="busy" @changed="emit('changed')"/>
    <ScanRejection :paper="copy.document.paper"/>
  </div>
</template>
