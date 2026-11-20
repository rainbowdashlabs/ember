/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FormSection from '@/components/container/FormSection.vue'
import FederationCard from '../eventeditview/FederationCard.vue'
import {sharingSummary} from '../eventshared/sectionSummaries'
import type {useEventFederationShare} from '../eventeditview/useEventFederationShare'

/**
 * Sharing an event group with partner stations: every part goes with it, parts added later included.
 * Each partner it is shared with can be left to decide its own sign-ups, within places counted for the
 * whole event group. It is a folded section of the event group's form that says with whom it is shared.
 */
defineProps<{
  federationShare: ReturnType<typeof useEventFederationShare>
  canFederate: boolean
  /** Inside the event group's form: explanations behind hint icons, no heading where the control names itself. */
  compact?: boolean
  /** Whether the share holds something the reader has to see, such as after a refused save, which opens it. */
  attention?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <FormSection
      :title="t('formSections.sharing')"
      :summary="sharingSummary(federationShare.state.federationShared, federationShare.state.federationScope,
                               federationShare.state.federationPartnerIds, t)"
      :attention="attention"
      data-testid="form-section-sharing"
  >
    <FederationCard
        :shared="federationShare.state.federationShared"
        :scope="federationShare.state.federationScope"
        :partner-ids="federationShare.state.federationPartnerIds"
        :partners="federationShare.partners.value"
        :can-federate="canFederate"
        :hint="t('eventGroups.federationHint')"
        :places-hint="t('eventGroups.partnerPlacesHint')"
        :compact="compact"
        :places="federationShare.state.federationPlaces"
        @update:shared="federationShare.handlers['update:federationShared']"
        @update:scope="federationShare.handlers['update:federationScope']"
        @update:partner-ids="federationShare.handlers['update:federationPartnerIds']"
        @update:places="federationShare.handlers['update:federationPlaces']"
    />
  </FormSection>
</template>
