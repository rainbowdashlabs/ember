/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {partnerAgreements} from '@/api'
import {PartnerAgreementState, type PartnerSigner, type PartnerSignerDocument} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {downloadAuthed} from '@/util/downloadAuthed'
import PartnerAgreementBadge from './PartnerAgreementBadge.vue'

/**
 * Where a member a partner station registered stands with one document, for a manager of the
 * registrations. They sign at their own station, and the sealed copy that came back downloads here. Where
 * the partner never took the document on, because its installation cannot sign, the signature is missing
 * and a signed paper copy can be confirmed instead.
 */
const props = defineProps<{
  eventId: number
  signer: PartnerSigner
  document: PartnerSignerDocument
  /** Called once a paper copy was confirmed, so the documents are read again. */
  onChanged: () => void
}>()

const {t} = useI18n()

const confirmable = computed(() =>
    props.document.state === PartnerAgreementState.MISSING || props.document.state === PartnerAgreementState.ASKED)

const downloading = useAsyncAction(async () => {
  const agreementId = props.document.agreementId
  if (agreementId !== null) await downloadAuthed(partnerAgreements.copyUrl(props.eventId, agreementId))
})

const confirming = useAsyncAction(async () => {
  await partnerAgreements.confirmPaper(props.eventId, props.signer.registrationId, props.document.templateId)
  props.onChanged()
})
</script>

<template>
  <div class="space-y-2" data-testid="partner-signer">
    <PartnerAgreementBadge :document="document"/>
    <FailureAlert :failure="confirming.failure.value ?? downloading.failure.value"/>
    <ButtonRow>
      <SecondaryButton v-if="document.agreementId !== null && document.copies > 0" compact :icon="['fas', 'download']"
                       :disabled="downloading.running.value" data-testid="partner-signer-copy"
                       @click="downloading.run()">
        {{ t('common.download') }}
      </SecondaryButton>
      <SecondaryButton v-if="confirmable" compact :icon="['fas', 'check']" :disabled="confirming.running.value"
                       data-testid="partner-signer-paper" @click="confirming.run()">
        {{ t('events.documents.partners.confirmPaper') }}
      </SecondaryButton>
    </ButtonRow>
    <MutedText v-if="document.confirmedByName" size="sm" tag="p">
      {{ t('events.documents.partners.confirmedBy', {name: document.confirmedByName}) }}
    </MutedText>
  </div>
</template>
