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
import type {MemberGroup, StationChatResponse, UserTag} from '@/api/generated/schema'
import {listStationChats} from '@/api/messages'
import {listGroups} from '@/api/memberGroups'
import {listChoosableTags} from '@/api/userTags'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import StationChatCard from './stationchatsview/StationChatCard.vue'
import OpenStationChatModal from './stationchatsview/OpenStationChatModal.vue'

/**
 * The station's chats for the people handling them: every chat with whom it is for, opening a new
 * one, and changing, closing or reopening the others. One dialog opens and changes a chat. The
 * groups and tags are loaded once, to name the audiences and to pick from.
 *
 * <p>Private tags are not offered: a chat shows who is in it.
 */
const {t} = useI18n()
const chats = ref<StationChatResponse[]>([])
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])
const dialogOpen = ref(false)
const editing = ref<StationChatResponse | null>(null)

const {loading, failure, reload} = useAsyncLoader(async () => {
  const [loadedChats, loadedGroups, loadedTags] = await Promise.all([
    listStationChats(),
    listGroups().catch(() => []),
    listChoosableTags().catch(() => []),
  ])
  chats.value = loadedChats
  groups.value = loadedGroups
  tags.value = loadedTags
})

/** Opens the dialog for a new chat, or for changing the one given. */
function openDialog(chat: StationChatResponse | null) {
  editing.value = chat
  dialogOpen.value = true
}
</script>

<template>
  <ViewContent :title="t('pages.station-chats.title')" :subtitle="t('pages.station-chats.subtitle')">
    <div class="space-y-4">
      <PrimaryButton :icon="['fas', 'plus']" data-testid="station-chat-open" @click="openDialog(null)">
        {{ t('stationChats.open.title') }}
      </PrimaryButton>
      <Spinner v-if="loading" size="md"/>
      <FailureAlert :failure="failure"/>
      <EmptyState v-if="!loading && chats.length === 0" :message="t('stationChats.empty')"/>
      <StationChatCard v-for="chat in chats" :key="chat.uid" :chat="chat" :groups="groups" :tags="tags"
                       :on-changed="reload" @edit="openDialog"/>
    </div>
    <OpenStationChatModal v-model="dialogOpen" :chat="editing" :groups="groups" :tags="tags" :on-saved="reload"/>
  </ViewContent>
</template>
