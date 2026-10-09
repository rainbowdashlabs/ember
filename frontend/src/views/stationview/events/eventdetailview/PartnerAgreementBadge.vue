/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import {PartnerAgreementState, type PartnerSignerDocument} from '@/api/generated/schema'

/**
 * Where a document stands for a member of a partner station: missing where the partner never took it on,
 * being signed at the partner, signed in full or in part, confirmed on paper here, or withdrawn there.
 */
defineProps<{
  document: PartnerSignerDocument
}>()

const {t} = useI18n()
</script>

<template>
  <ErrorBadge v-if="document.state === PartnerAgreementState.MISSING">
    {{ t('events.documents.partners.missing') }}
  </ErrorBadge>
  <InfoBadge v-else-if="document.state === PartnerAgreementState.ASKED">
    {{ t('events.documents.partners.asked') }}
  </InfoBadge>
  <SuccessBadge v-else-if="document.state === PartnerAgreementState.SIGNED && document.complete">
    {{ t('events.documents.signed') }}
  </SuccessBadge>
  <InfoBadge v-else-if="document.state === PartnerAgreementState.SIGNED">
    {{ t('events.documents.partners.partlySigned') }}
  </InfoBadge>
  <SuccessBadge v-else-if="document.state === PartnerAgreementState.PAPER_CONFIRMED">
    {{ t('events.documents.paperConfirmed') }}
  </SuccessBadge>
  <ErrorBadge v-else>{{ t('events.documents.partners.withdrawn') }}</ErrorBadge>
</template>
