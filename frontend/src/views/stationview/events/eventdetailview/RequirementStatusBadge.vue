/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {PaperState, RequirementSignatureState, RequirementStatus, type RequiredDocumentStatus} from '@/api/generated/schema'
import {formatDate} from '@/util/format'
import SignatureStateBadge from '../eventshared/SignatureStateBadge.vue'

/**
 * Where a participant's copy of a document stands. Signatures settled on the copy decide first: signed,
 * confirmed on paper or waived. Then a scan of the signed paper copy: confirmed, waiting for confirmation,
 * or turned down. Then signatures still open. Without any of these: generated on a day, outdated, or not
 * yet generated.
 */
const props = defineProps<{
  document: RequiredDocumentStatus
}>()

const {t} = useI18n()

const paper = computed(() => props.document.paper?.state ?? null)
const signature = computed(() => props.document.signature?.state ?? null)
const settled = computed(() => signature.value !== null && signature.value !== RequirementSignatureState.OPEN)
const generated = computed(() => props.document.status === RequirementStatus.GENERATED)
</script>

<template>
  <SignatureStateBadge v-if="settled && signature" :state="signature"/>
  <SuccessBadge v-else-if="paper === PaperState.CONFIRMED">{{ t('events.documents.paperConfirmed') }}</SuccessBadge>
  <InfoBadge v-else-if="paper === PaperState.SUBMITTED">{{ t('events.documents.scanWaiting') }}</InfoBadge>
  <ErrorBadge v-else-if="paper === PaperState.REJECTED">{{ t('events.documents.scanRejected') }}</ErrorBadge>
  <SignatureStateBadge v-else-if="signature" :state="signature"/>
  <InfoBadge v-else-if="generated && document.outdated">{{ t('events.documents.outdated') }}</InfoBadge>
  <SuccessBadge v-else-if="generated">
    {{ t('events.documents.generated', {date: formatDate(document.generatedAt)}) }}
  </SuccessBadge>
  <SecondaryBadge v-else>{{ t('events.documents.notGenerated') }}</SecondaryBadge>
</template>
