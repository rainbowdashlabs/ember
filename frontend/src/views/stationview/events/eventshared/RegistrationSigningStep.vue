/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import Modal from '@/components/feedback/Modal.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import {appointmentDocuments} from '@/api'
import {PaperState, type PaperSubmission} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import type {SigningStep, SigningStepCopy} from '@/composables/useRegistrationSigningStep'
import {SCAN_TYPES} from '../eventdetailview/documentTiles'
import {scanReceiptKey} from '../eventdetailview/scanReceipt'
import SignatureFieldList from './SignatureFieldList.vue'
import SignatureStateBadge from './SignatureStateBadge.vue'
import {fieldsToSign} from './requirementSignatures'

/**
 * The step a registration ends on, where the appointment's documents to bring ask for signatures: each
 * copy of the people just registered that still waits, with its fields. "Jetzt unterschreiben" opens the
 * signing screen with every field the reader may sign on these copies ticked, all in one go; instead, a
 * scan of the signed paper copy can be handed in, which waits for the
 * event managers to confirm it; or the step is closed and the signatures are done later from the open
 * tasks or the appointment's page. Fields for somebody else, such as a second guardian, are asked of
 * them and only shown here.
 */
const open = defineModel<boolean>({required: true})

const props = defineProps<{
  step: SigningStep
}>()

const {t} = useI18n()
const router = useRouter()

const handedIn = ref<string[]>([])
const received = ref<PaperSubmission | null>(null)
const named = computed(() => new Set(props.step.copies.map(copy => copy.memberId)).size > 1)

function keyOf(copy: SigningStepCopy): string {
  return `${copy.memberId}-${copy.document.templateId}`
}

/** Whether a scan of the copy waits for the event managers, which asks nobody to sign it meanwhile. */
function scanWaits(copy: SigningStepCopy): boolean {
  return handedIn.value.includes(keyOf(copy)) || copy.document.paper?.state === PaperState.SUBMITTED
}

/** Every field on the copies the reader can sign now, for themselves or a member in their care. */
const signable = computed(() => props.step.copies
    .filter(copy => !scanWaits(copy))
    .flatMap(copy => fieldsToSign(copy.document.signature)))

/** Opens the signing screen with exactly these fields ticked, every document in one go. */
function signNow() {
  void router.push({name: 'station-signing-all', query: {fields: signable.value.map(field => field.id).join(',')}})
}

const handingIn = useAsyncAction(async (copy: SigningStepCopy, file: File) => {
  const target = {
    eventId: props.step.eventId,
    date: props.step.date,
    templateId: copy.document.templateId,
    memberId: copy.memberId,
  }
  received.value = null
  received.value = await appointmentDocuments.submitScan(target, file, t('events.documents.scanTitle', {name: copy.document.name}))
  handedIn.value = [...handedIn.value, keyOf(copy)]
})
</script>

<template>
  <Modal v-model="open" size="md">
    <div class="space-y-3" data-testid="registration-signing-step">
      <SubHeader>{{ t('events.documents.step.title') }}</SubHeader>
      <MutedText size="sm" tag="p">{{ t('events.documents.step.hint') }}</MutedText>
      <FailureAlert :failure="handingIn.failure.value"/>
      <Alert v-if="received" variant="success" data-testid="document-scan-received">
        {{ t(scanReceiptKey(received)) }}
      </Alert>
      <NeutralContainer v-for="copy in step.copies" :key="keyOf(copy)" class="space-y-2"
                        data-testid="registration-signing-copy">
        <div class="flex flex-wrap items-center gap-2">
          <FieldLabel class="flex-1">
            {{ named ? t('events.documents.step.copyFor', {document: copy.document.name, name: copy.name}) : copy.document.name }}
          </FieldLabel>
          <InfoBadge v-if="scanWaits(copy)">{{ t('events.documents.scanWaiting') }}</InfoBadge>
          <SignatureStateBadge v-else-if="copy.document.signature" :state="copy.document.signature.state"/>
        </div>
        <SignatureFieldList v-if="copy.document.signature" :signature="copy.document.signature" :offer-signing="false"/>
        <MutedText v-if="!scanWaits(copy) && fieldsToSign(copy.document.signature).length === 0" size="sm" tag="p">
          {{ t('events.documents.step.othersAsked') }}
        </MutedText>
        <div v-if="!handedIn.includes(keyOf(copy))" class="flex justify-end">
          <FileUploadButton :accept="SCAN_TYPES" :disabled="handingIn.running.value"
                            data-testid="registration-signing-scan" @select="file => handingIn.run(copy, file)">
            {{ t('events.documents.scanUpload') }}
          </FileUploadButton>
        </div>
      </NeutralContainer>
      <ButtonRow align="end">
        <SecondaryButton data-testid="registration-signing-later" @click="open = false">
          {{ t('events.documents.step.later') }}
        </SecondaryButton>
        <PrimaryButton v-if="signable.length > 0" :icon="['fas', 'file-signature']" data-testid="registration-signing-now"
                       @click="signNow">
          {{ t('events.documents.step.signNow') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
