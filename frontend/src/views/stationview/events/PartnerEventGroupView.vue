/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import EventGroupOverview from './eventgroupdetailview/EventGroupOverview.vue'
import EventGroupHouseholdPanel from './eventgroupdetailview/EventGroupHouseholdPanel.vue'
import {partnerEventGroupPort} from './eventgroupdetailview/eventGroupSignUpPort'
import type {EventGroupDetail} from '@/api/generated/schema'
import {partnerEventGroups} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

/**
 * An event group a partner station shares with this one: its parts by set and the sign-up of the reader and
 * everybody they look after, sent to the partner that holds it.
 */
const {t} = useI18n()
const route = useRoute()
const {loaded} = useSession()

const stationUid = computed(() => route.params.stationUid as string)
const eventGroupId = computed(() => Number(route.params.eventGroupId))
const detail = ref<EventGroupDetail | null>(null)
const port = computed(() => partnerEventGroupPort(stationUid.value, eventGroupId.value))

const {loading, failure, reload} = useAsyncLoader(async () => {
  detail.value = await partnerEventGroups.getEventGroup(stationUid.value, eventGroupId.value)
}, {autoLoad: loaded.value})

watch(loaded, ready => { if (ready) reload() })

function partLink(eventId: number) {
  return {name: 'federated-event-detail', params: {stationUid: stationUid.value, eventId}}
}
</script>

<template>
  <ViewContent :title="detail?.eventGroup.name ?? t('pages.federated-event-group.title')"
               :subtitle="t('pages.federated-event-group.subtitle')">
    <AsyncSection :loading="loading && !detail" :failure="failure">
      <div v-if="detail" class="space-y-6">
        <EventGroupOverview :detail="detail" :part-link="partLink"/>
        <EventGroupHouseholdPanel :detail="detail" :port="port" :on-changed="reload"/>
      </div>
    </AsyncSection>
  </ViewContent>
</template>
