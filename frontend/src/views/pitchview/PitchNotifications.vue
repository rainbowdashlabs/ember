/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NotificationsSection from '@/views/stationview/profile/settingsview/NotificationsSection.vue'
import FeedCard from '@/views/stationview/profile/settingsview/feedsection/FeedCard.vue'
import type {PitchNotifications} from './pitchTypes'

/** The notification settings and the personal feeds, drawn by the application's own panels. */
defineProps<{
  notifications: PitchNotifications
}>()
</script>

<template>
  <NotificationsSection v-if="notifications.settings" :settings="notifications.settings"/>

  <template v-if="notifications.feeds">
    <FeedCard v-for="feed in notifications.feeds" :key="feed.title" v-bind="feed" copied=""/>
  </template>
</template>
