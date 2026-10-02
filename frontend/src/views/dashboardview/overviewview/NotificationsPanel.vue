/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import InfoContainer from '@/components/container/InfoContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NotificationInbox from '@/components/notifications/NotificationInbox.vue'
import {stationInbox} from '@/api/notifications'
import {getFeedStatus} from '@/api/feedToken'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import type {FeedStatusResponse} from '@/api/generated/schema'

/**
 * The station member's inbox on the dashboard, with a hint to set up the personal feed where the
 * reader has none running.
 */
const {t} = useI18n()
const router = useRouter()
const {refresh: refreshSidebarCounts} = useSidebarCounts()

const feedStatus = ref<FeedStatusResponse | null>(null)

const showFeedCta = computed(() => {
  if (!feedStatus.value) return false
  return !feedStatus.value.hasToken || !feedStatus.value.notificationActive
})

const feedCtaMessage = computed(() => {
  if (!feedStatus.value) return ''
  if (!feedStatus.value.hasToken) return t('dashboard.feedSetupHint')
  return t('dashboard.feedInactiveHint')
})

onMounted(async () => {
  feedStatus.value = await getFeedStatus().catch(() => null)
})
</script>

<template>
  <NotificationInbox :api="stationInbox" :settings="{ name: 'profile-notifications' }"
                     @changed="refreshSidebarCounts">
    <template #before-list>
      <InfoContainer v-if="showFeedCta" class="flex items-center justify-between gap-3 py-2 px-3">
        <div class="flex items-center gap-2">
          <font-awesome-icon :icon="['fas', 'rss']" class="text-info shrink-0"/>
          <p class="text-xs">{{ feedCtaMessage }}</p>
        </div>
        <SecondaryButton class="shrink-0 text-xs" compact @click="router.push({ name: 'profile-notifications' })">
          {{ t('dashboard.feedSetup') }}
        </SecondaryButton>
      </InfoContainer>
    </template>
  </NotificationInbox>
</template>
