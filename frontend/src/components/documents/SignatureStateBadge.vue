/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import AppIcon from '@/components/display/AppIcon.vue'
import type {SignatureSummary} from '@/api/generated/schema'
import {SignatureDisplay, signatureDisplayOf, waitsForNobody} from './signatureState'

/**
 * How the signatures asked for on a document stand, as one badge: signed, partly signed with how many of
 * how many, open, withdrawn or replaced by a corrected document. A request that waits for a field nobody can
 * sign carries a second badge saying so, since only a manager can settle such a field.
 */
const props = defineProps<{
  summary: SignatureSummary
}>()

const {t} = useI18n()

const display = computed(() => signatureDisplayOf(props.summary))

const badge = computed(() => {
  switch (display.value) {
    case SignatureDisplay.SIGNED:
      return SuccessBadge
    case SignatureDisplay.PARTLY_SIGNED:
      return InfoBadge
    case SignatureDisplay.OPEN:
      return PrimaryBadge
    default:
      return SecondaryBadge
  }
})

/** The state in words, with how many of how many fields are signed while the request still waits. */
const label = computed(() => {
  const state = t(`signing.state.${display.value}`)
  if (display.value !== SignatureDisplay.PARTLY_SIGNED && display.value !== SignatureDisplay.OPEN) return state
  const count = t('signing.state.count', {signed: props.summary.signed, expected: props.summary.expected})
  return `${state} · ${count}`
})
</script>

<template>
  <span class="inline-flex flex-wrap gap-1" data-testid="signature-state">
    <component :is="badge" :data-state="display">
      <AppIcon :icon="['fas', 'file-signature']" class="mr-1"/>{{ label }}
    </component>
    <ErrorBadge v-if="waitsForNobody(props.summary)" data-testid="signature-nobody">
      {{ t('signing.state.nobodyCanSign') }}
    </ErrorBadge>
  </span>
</template>
