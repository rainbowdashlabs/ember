/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import {appointmentDocuments} from '@/api'
import type {AgreementSigner} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {formatDateTime} from '@/util/format'
import SignatureStateBadge from '../eventshared/SignatureStateBadge.vue'

/**
 * Who said "I will come" on an appointment without registrations by signing its agreement, for whoever
 * runs it: per participant and document where it stands, when it was signed or withdrawn, and whether they
 * said they will not come since. Nothing shows where the appointment asks for no agreement.
 */
const props = defineProps<{
  eventId: number
  /** The date of the appointment on screen. */
  date: string
}>()

const {t} = useI18n()

const signers = ref<AgreementSigner[] | null>(null)

const {failure, reload} = useAsyncLoader(async isCurrent => {
  const loaded = await appointmentDocuments.agreementSigners(props.eventId, props.date)
  if (isCurrent()) signers.value = loaded
}, {autoLoad: false})

watch(() => [props.eventId, props.date], reload, {immediate: true})
</script>

<template>
  <NeutralContainer v-if="failure || (signers && signers.length > 0)" class="space-y-3" data-testid="agreement-signers">
    <SubHeader>{{ t('events.documents.signers.title') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('events.documents.signers.hint') }}</MutedText>
    <FailureAlert :failure="failure"/>
    <ul class="space-y-1 text-sm">
      <li v-for="signer in signers ?? []" :key="`${signer.memberId}-${signer.templateId}`"
          class="flex flex-wrap items-center gap-2" data-testid="agreement-signer">
        <span class="flex-1 font-medium">{{ signer.name }}</span>
        <span class="text-(--text-muted)">{{ signer.documentName }}</span>
        <SignatureStateBadge :state="signer.state"/>
        <MutedText v-if="signer.withdrawnAt" size="sm">
          {{ t('events.documents.withdrawnAt', {date: formatDateTime(signer.withdrawnAt)}) }}
        </MutedText>
        <MutedText v-else size="sm">{{ t('events.documents.signers.signedAt', {date: formatDateTime(signer.signedAt)}) }}</MutedText>
        <ErrorBadge v-if="signer.refused">{{ t('events.documents.signers.refused') }}</ErrorBadge>
      </li>
    </ul>
  </NeutralContainer>
</template>
