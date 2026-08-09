/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type { FederationContract, FederationPartner } from '@/api/federation'
import { featureCompatible, partnerCompatibility } from '@/util/federationVersion'

const { t } = useI18n()

const props = defineProps<{
  local: FederationContract | null
  partner: FederationPartner
  capability?: string
}>()

const state = computed(() => partnerCompatibility(props.local, props.partner))
const featureOk = computed(
  () => !props.capability || featureCompatible(props.local, props.partner, props.capability),
)
</script>

<template>
  <template v-if="capability">
    <ErrorBadge v-if="!featureOk" :title="t('federation.featureIncompatibleHint')">
      <font-awesome-icon :icon="['fas', 'triangle-exclamation']" class="mr-1"/>
      {{ t('federation.featureIncompatible') }}
    </ErrorBadge>
  </template>
  <template v-else>
    <InfoBadge v-if="state === 'unknown'" :title="t('federation.versionUnknownHint')">
      <font-awesome-icon :icon="['fas', 'triangle-exclamation']" class="mr-1"/>
      {{ t('federation.versionUnknown') }}
    </InfoBadge>
    <ErrorBadge v-else-if="state === 'incompatible'" :title="t('federation.incompatibleHint')">
      <font-awesome-icon :icon="['fas', 'triangle-exclamation']" class="mr-1"/>
      {{ t('federation.incompatible') }}
    </ErrorBadge>
    <InfoBadge v-else-if="state === 'partial'" :title="t('federation.partiallyCompatibleHint')">
      <font-awesome-icon :icon="['fas', 'triangle-exclamation']" class="mr-1"/>
      {{ t('federation.partiallyCompatible') }}
    </InfoBadge>
  </template>
</template>
