/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import type {StationChatResponse} from '@/api/generated/schema'
import {listStationChats} from '@/api/messages'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import StationChatCard from './stationchatsview/StationChatCard.vue'
import OpenStationChatModal from './stationchatsview/OpenStationChatModal.vue'

/**
 * The station's chats for the people handling them: every chat with whom it is for, opening a new
 * one, and renaming, closing or reopening the others.
 */
const {t} = useI18n()
const chats = ref<StationChatResponse[]>([])
const opening = ref(false)

const {loading, failure, reload} = useAsyncLoader(async () => {
  chats.value = await listStationChats()
})
</script>

<template>
  <ViewContent :title="t('pages.message-station-chats.title')" :subtitle="t('pages.message-station-chats.subtitle')">
    <div class="space-y-4">
      <PrimaryButton :icon="['fas', 'plus']" data-testid="station-chat-open" @click="opening = true">
        {{ t('stationChats.open.title') }}
      </PrimaryButton>
      <Spinner v-if="loading" size="md"/>
      <FailureAlert :failure="failure"/>
      <EmptyState v-if="!loading && chats.length === 0" :message="t('stationChats.empty')"/>
      <StationChatCard v-for="chat in chats" :key="chat.uid" :chat="chat" :on-changed="reload"/>
    </div>
    <OpenStationChatModal v-model="opening" :on-opened="reload"/>
  </ViewContent>
</template>
