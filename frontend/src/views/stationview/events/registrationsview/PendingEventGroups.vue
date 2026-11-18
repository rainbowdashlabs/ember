/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import RowLink from '@/components/navigation/RowLink.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type {EventGroupSummary} from '@/api/generated/schema'

/**
 * The event groups whose sign-ups wait for a decision. A sign-up for an event group holds every part
 * it chose and is decided as a whole, so it is not listed under the dates of its parts but here, once,
 * leading to the event group where it is decided.
 */
defineProps<{
  /** The event groups to list: those with sign-ups waiting, as `waitingEventGroups` picks them. */
  groups: EventGroupSummary[]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer v-if="groups.length > 0" class="space-y-3" data-testid="pending-event-groups">
    <SectionHeader>{{ t('eventsRegistrations.eventGroups') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('eventsRegistrations.eventGroupsHint') }}</MutedText>
    <ul class="space-y-2">
      <li v-for="group in groups" :key="group.id">
        <RowLink :to="{name: 'event-group', params: {eventGroupId: group.id}}">
          <NeutralContainer class="flex items-center justify-between gap-2 hover:bg-(--bg-accent)" data-testid="pending-event-group-row">
            <p class="font-medium truncate">{{ group.details.name }}</p>
            <InfoBadge>{{ t('eventsRegistrations.eventGroupPending', {count: group.pending}) }}</InfoBadge>
          </NeutralContainer>
        </RowLink>
      </li>
    </ul>
  </NeutralContainer>
</template>
