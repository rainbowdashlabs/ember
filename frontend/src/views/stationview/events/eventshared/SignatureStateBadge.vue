/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import {RequirementSignatureState} from '@/api/generated/schema'

/**
 * Where a signature stands: signed online, confirmed on paper, waived, withdrawn by a signer, still open,
 * or, for the issuer's field as participants and guardians see it, signed by the station, which is neutral
 * and asks nothing of them.
 */
defineProps<{
  state: RequirementSignatureState
}>()

const {t} = useI18n()
</script>

<template>
  <SuccessBadge v-if="state === RequirementSignatureState.SIGNED">{{ t('events.documents.signed') }}</SuccessBadge>
  <SuccessBadge v-else-if="state === RequirementSignatureState.PAPER_CONFIRMED">
    {{ t('events.documents.paperConfirmed') }}
  </SuccessBadge>
  <SecondaryBadge v-else-if="state === RequirementSignatureState.WAIVED">
    {{ t('events.documents.signatureWaived') }}
  </SecondaryBadge>
  <ErrorBadge v-else-if="state === RequirementSignatureState.REVOKED">
    {{ t('events.documents.signatureRevoked') }}
  </ErrorBadge>
  <SecondaryBadge v-else-if="state === RequirementSignatureState.BY_STATION" data-testid="signature-by-station">
    {{ t('events.documents.signatureByStation') }}
  </SecondaryBadge>
  <InfoBadge v-else>{{ t('events.documents.signatureOpen') }}</InfoBadge>
</template>
