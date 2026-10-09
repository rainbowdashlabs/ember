/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import Alert from '@/components/feedback/Alert.vue'
import {RELAY_PROVIDER_NAMES} from '@/util/mailProviders'
import type {PoolOverview, ProviderStanding} from '@/api/generated/schema'

/**
 * How the instance lends its providers to stations, on its own mail log.
 *
 * <p>The share stations may use is a part of a provider's daily limit, so a provider without one holds
 * nothing back for the instance's own mail. That is worth a warning, but only while some station may
 * actually send through the instance.
 */
const props = defineProps<{
  pool: PoolOverview
  providers: ProviderStanding[]
}>()

const {t} = useI18n()

const unlimited = computed(() => props.providers
    .filter(standing => standing.dailySendLimit <= 0)
    .map(standing => `${standing.position + 1}. ${RELAY_PROVIDER_NAMES[standing.provider] ?? t('mailChain.ownServer')}`))
</script>

<template>
  <MutedText tag="p" size="sm">
    {{ t('instanceMail.pool.overview', {share: pool.sharePercent, count: pool.grantedStations}) }}
  </MutedText>
  <Alert v-if="pool.grantedStations > 0 && unlimited.length > 0" variant="error" data-testid="mail-pool-no-limit">
    {{ t('instanceMail.pool.noLimitWarning', {providers: unlimited.join(', ')}) }}
  </Alert>
</template>
