/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import RowLink from '@/components/navigation/RowLink.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import type {EventGroupSummary} from '@/api/generated/schema'
import {eventGroups} from '@/api'

/**
 * The event groups that hold no appointment yet. An event group is reached through the notice on its
 * appointments, so one without any would be out of reach; it is listed here until it has one. A
 * cancelled one is listed too, marked as such, so it can still be deleted. Shows nothing while there is
 * none.
 */
const groups = ref<EventGroupSummary[]>([])

const {t} = useI18n()

const empty = computed(() => groups.value.filter(group => group.partCount === 0))

onMounted(async () => {
  groups.value = await eventGroups.listEventGroups().catch(() => [])
})
</script>

<template>
  <NeutralContainer v-if="empty.length > 0" class="space-y-3" data-testid="empty-event-groups">
    <SectionHeader>{{ t('eventGroups.withoutAppointments') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('eventGroups.withoutAppointmentsHint') }}</MutedText>
    <ul class="space-y-2">
      <li v-for="group in empty" :key="group.id">
        <RowLink :to="{name: 'event-group-edit', params: {eventGroupId: group.id}}">
          <NeutralContainer class="flex items-center justify-between gap-2 hover:bg-(--bg-accent)" data-testid="empty-event-group-row">
            <p class="font-medium truncate">{{ group.name }}</p>
            <ErrorBadge v-if="group.cancelled">{{ t('eventGroups.cancelled') }}</ErrorBadge>
          </NeutralContainer>
        </RowLink>
      </li>
    </ul>
  </NeutralContainer>
</template>
