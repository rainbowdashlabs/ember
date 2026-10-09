/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import {partnerAgreements} from '@/api'
import {PartnerAgreementState, type PartnerSigner, type PartnerSignerDocument} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {downloadAuthed} from '@/util/downloadAuthed'
import PartnerAgreementBadge from './PartnerAgreementBadge.vue'

/**
 * Where the members partner stations registered stand with one document, for an event manager. They sign
 * at their own station, and the sealed copy comes back here to be read. Where a partner never took the
 * document on, because its installation cannot sign, the signature is missing, and a signed paper copy can
 * be confirmed instead.
 */
const props = defineProps<{
  eventId: number
  templateId: number
  signers: PartnerSigner[]
  /** Called once a paper copy was confirmed. */
  onChanged: () => void
}>()

const {t} = useI18n()

interface Row {
  signer: PartnerSigner
  document: PartnerSignerDocument
}

const rows = computed<Row[]>(() => props.signers.flatMap(signer => {
  const document = signer.documents.find(candidate => candidate.templateId === props.templateId)
  return document ? [{signer, document}] : []
}))

const viewing = useAsyncAction(async (agreementId: number) => {
  await downloadAuthed(partnerAgreements.copyUrl(props.eventId, agreementId))
})

const confirming = useAsyncAction(async (row: Row) => {
  await partnerAgreements.confirmPaper(props.eventId, row.signer.registrationId, props.templateId)
  props.onChanged()
})

function viewCopy(document: PartnerSignerDocument) {
  if (document.agreementId !== null) void viewing.run(document.agreementId)
}

function confirmable(document: PartnerSignerDocument): boolean {
  return document.state === PartnerAgreementState.MISSING || document.state === PartnerAgreementState.ASKED
}
</script>

<template>
  <div v-if="rows.length > 0" class="space-y-2" data-testid="partner-signers">
    <FieldLabel>{{ t('events.documents.partners.title') }}</FieldLabel>
    <MutedText size="sm" tag="p">{{ t('events.documents.partners.hint') }}</MutedText>
    <FailureAlert :failure="confirming.failure.value ?? viewing.failure.value"/>
    <ul class="space-y-2">
      <li v-for="row in rows" :key="row.signer.registrationId" class="flex flex-wrap items-center gap-2"
          data-testid="partner-signer">
        <span class="flex-1">
          <MemberName v-if="row.signer.member" :identity="row.signer.member"/>
          <template v-else>{{ t('events.documents.partners.unknown') }}</template>
        </span>
        <PartnerAgreementBadge :document="row.document"/>
        <IconButton v-if="row.document.copies > 0" :icon="['fas', 'eye']"
                    :label="t('events.documents.partners.viewCopy')" data-testid="partner-signer-copy"
                    @click="viewCopy(row.document)"/>
        <SecondaryButton v-if="confirmable(row.document)" :disabled="confirming.running.value" compact
                         data-testid="partner-signer-paper" @click="confirming.run(row)">
          {{ t('events.documents.partners.confirmPaper') }}
        </SecondaryButton>
        <MutedText v-if="row.document.confirmedByName" size="sm" class="w-full">
          {{ t('events.documents.partners.confirmedBy', {name: row.document.confirmedByName}) }}
        </MutedText>
      </li>
    </ul>
  </div>
</template>
