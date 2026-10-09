/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import WithdrawAgreementModal from '@/components/documents/WithdrawAgreementModal.vue'
import {appointmentDocuments} from '@/api'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {formatDateTime} from '@/util/format'
import {fieldsToSign} from '../eventshared/requirementSignatures'
import type {ParticipantCopy} from './documentTiles'

/**
 * What a participant's copy offers beyond its download: on an appointment without registrations, signing
 * its agreement online, which counts as coming and leads straight to the signing screen; and wherever
 * something was signed that the reader may withdraw, withdrawing it. A withdrawn copy says when.
 */
const props = defineProps<{
  eventId: number
  /** The date of the appointment on screen. */
  date: string
  copy: ParticipantCopy
  busy: boolean
}>()

const emit = defineEmits<{
  changed: []
}>()

const {t} = useI18n()
const router = useRouter()

const withdrawing = ref(false)
const signature = computed(() => props.copy.document.signature)

const offering = useAsyncAction(async () => {
  const asked = await appointmentDocuments.offerAgreement(
      props.eventId, props.date, props.copy.document.templateId, props.copy.memberId)
  const first = fieldsToSign(asked)[0]
  if (first) {
    await router.push({name: 'station-signing', params: {fieldId: first.id}})
    return
  }
  emit('changed')
})
</script>

<template>
  <div v-if="copy.document.agreementOffered || signature?.withdrawable || signature?.withdrawnAt" class="space-y-1"
       data-testid="agreement-actions">
    <FailureAlert :failure="offering.failure.value"/>
    <MutedText v-if="signature?.withdrawnAt" size="sm" tag="p" data-testid="agreement-withdrawn-at">
      {{ t('events.documents.withdrawnAt', {date: formatDateTime(signature.withdrawnAt)}) }}
    </MutedText>
    <MutedText v-if="copy.document.agreementOffered" size="sm" tag="p">{{ t('events.documents.agreementHint') }}</MutedText>
    <ButtonRow align="end">
      <PrimaryButton v-if="copy.document.agreementOffered" :icon="['fas', 'file-signature']"
                     :disabled="busy || offering.running.value" data-testid="agreement-sign" @click="offering.run()">
        {{ t('events.documents.signOnline') }}
      </PrimaryButton>
      <SecondaryButton v-if="signature?.withdrawable" :icon="['fas', 'rotate-left']" :disabled="busy"
                       data-testid="agreement-withdraw" @click="withdrawing = true">
        {{ t('events.documents.withdraw.action') }}
      </SecondaryButton>
    </ButtonRow>
    <WithdrawAgreementModal v-if="withdrawing && signature" v-model="withdrawing" :request-uid="signature.requestUid"
                            @withdrawn="emit('changed')"/>
  </div>
</template>
