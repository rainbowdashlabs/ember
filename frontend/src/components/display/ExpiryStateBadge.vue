/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import type {FieldSettings} from '@/api/profileFields'
import {expirySettingsOf, ExpiryStates, readExpiry} from '@/util/expiry'

/**
 * How close an expiry date is, in words and in colour: yellow while it runs out, red once it has
 * passed, nothing while it is valid or empty.
 *
 * <p>Words as well as colour, so it reads without colour vision and on paper. The one place the
 * member list, the member page and the profile forms take it from.
 */
const props = defineProps<{
  value: unknown
  /** The field's settings, which say how many days ahead a date counts as running out. */
  config?: FieldSettings | null
}>()

const {t} = useI18n()

const reading = computed(() =>
    readExpiry(typeof props.value === 'string' ? props.value : null, expirySettingsOf(props.config).warnFromDays))

const words = computed(() => {
  const {state, days} = reading.value
  if (state === ExpiryStates.EXPIRED) {
    return days === 1 ? t('expiry.validUntilYesterday') : t('expiry.validUntilDaysAgo', {days})
  }
  if (days === 0) return t('expiry.expiresToday')
  return days === 1 ? t('expiry.expiresTomorrow') : t('expiry.expiresInDays', {days})
})
</script>

<template>
  <ErrorBadge v-if="reading.state === ExpiryStates.EXPIRED" data-testid="expiry-state" data-state="EXPIRED">
    {{ words }}
  </ErrorBadge>
  <InfoBadge v-else-if="reading.state === ExpiryStates.EXPIRING" data-testid="expiry-state" data-state="EXPIRING">
    {{ words }}
  </InfoBadge>
</template>
