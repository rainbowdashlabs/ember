/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import EventGroupListItem from './eventgroupsview/EventGroupListItem.vue'
import type {EventGroupSummary} from '@/api/generated/schema'
import {StationPermission} from '@/api/generated/schema'
import {eventGroups} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

/**
 * The event groups of the station: appointments that belong together and are signed up for once. Members
 * see the event groups meant for them or for somebody they look after; whoever edits appointments sees
 * every event group and creates new ones.
 */
const {t} = useI18n()
const {loaded, hasPermission} = useSession()

const groupList = ref<EventGroupSummary[]>([])

const {loading, failure, reload} = useAsyncLoader(async () => {
  groupList.value = await eventGroups.listEventGroups()
}, {autoLoad: loaded.value})

watch(loaded, ready => { if (ready) reload() })
</script>

<template>
  <ViewContent :title="t('pages.event-groups.title')" :subtitle="t('pages.event-groups.subtitle')">
    <div v-if="hasPermission(StationPermission.EVENT_EDIT)" class="flex items-center justify-end mb-4">
      <PrimaryButton :icon="['fas', 'plus']" :to="{name: 'event-group-new'}" data-testid="event-group-create">
        {{ t('eventGroups.create') }}
      </PrimaryButton>
    </div>
    <AsyncSection
        :loading="loading"
        :failure="failure"
        :empty="groupList.length === 0"
        :empty-message="t('eventGroups.empty')"
        empty-compact
    >
      <ul class="space-y-2">
        <li v-for="eventGroup in groupList" :key="eventGroup.id">
          <EventGroupListItem :event-group="eventGroup"/>
        </li>
      </ul>
    </AsyncSection>
  </ViewContent>
</template>
