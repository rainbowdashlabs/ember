/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PublicEventList from '@/components/display/PublicEventList.vue'
import type {PublicEvent} from '@/api/publicEvents'
import {getIcalFeedUrl, getIcalSubscribeUrl, listPublicEvents} from '@/api/publicEvents'

const {t} = useI18n()
const route = useRoute()

const stationUid = computed(() => route.params.stationUid as string)
const events = ref<PublicEvent[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    events.value = await listPublicEvents(stationUid.value)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <ViewContent>
    <div class="space-y-4">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <SectionHeader>{{ t('publicStation.upcomingEvents') }}</SectionHeader>
        <a :href="getIcalSubscribeUrl(stationUid)" class="text-sm text-primary hover:underline">
          <font-awesome-icon :icon="['fas', 'calendar-plus']" class="mr-1"/>
          {{ t('publicStation.subscribeCal') }}
        </a>
      </div>

      <NeutralContainer class="text-xs text-(--text-muted) space-y-1">
        <p>{{ t('publicStation.icalHint') }}</p>
        <code class="block bg-(--bg-accent) rounded px-2 py-1 select-all break-all">{{ getIcalFeedUrl(stationUid) }}</code>
      </NeutralContainer>

      <Spinner v-if="loading"/>
      <PublicEventList v-else :events="events"/>
    </div>
  </ViewContent>
</template>
