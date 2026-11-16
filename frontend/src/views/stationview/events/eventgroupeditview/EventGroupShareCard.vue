/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FederationCard from '../eventeditview/FederationCard.vue'
import type {useEventFederationShare} from '../eventeditview/useEventFederationShare'

/** Sharing an event group with partner stations: every part goes with it, parts added later included. */
defineProps<{
  federationShare: ReturnType<typeof useEventFederationShare>
  canFederate: boolean
  /** Inside the event group's form: explanations behind hint icons, no heading where the control names itself. */
  compact?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <FederationCard
      :shared="federationShare.state.federationShared"
      :scope="federationShare.state.federationScope"
      :partner-ids="federationShare.state.federationPartnerIds"
      :partners="federationShare.partners.value"
      :can-federate="canFederate"
      :hint="t('eventGroups.federationHint')"
      :compact="compact"
      @update:shared="federationShare.handlers['update:federationShared']"
      @update:scope="federationShare.handlers['update:federationScope']"
      @update:partner-ids="federationShare.handlers['update:federationPartnerIds']"
  />
</template>
