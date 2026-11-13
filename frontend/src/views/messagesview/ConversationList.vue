/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import BareButton from '@/components/button/BareButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import type {ConversationSummaryResponse} from '@/api/generated/schema'
import type {Failure} from '@/util/failure'
import {useMessageWords} from '@/composables/useMessageWords'
import ConversationRow from './conversationlist/ConversationRow.vue'
import NewConversationModal from './NewConversationModal.vue'
import MessagesSettingsModal from './MessagesSettingsModal.vue'

/**
 * The left column: a search over the reader's conversations, the way to start a new one and the
 * settings, then every conversation by last activity.
 */
const props = defineProps<{
  conversations: readonly ConversationSummaryResponse[]
  loading: boolean
  failure: Failure | null
  openUid: string | null
  onChanged: () => Promise<void> | void
}>()

const {t} = useI18n()
const {titleOf} = useMessageWords()

/** Which conversations the list shows: all of them, or only the station chats. */
const FILTERS = ['all', 'station'] as const
type Filter = typeof FILTERS[number]

const search = ref('')
const filter = ref<Filter>('all')
const starting = ref(false)
const settings = ref(false)

const hasStationChats = computed(() => props.conversations.some(conversation => conversation.station))

const shown = computed(() => {
  const needle = search.value.trim().toLocaleLowerCase()
  return props.conversations
    .filter(conversation => !hasStationChats.value || filter.value === 'all' || conversation.station)
    .filter(conversation => !needle || titleOf(conversation).toLocaleLowerCase().includes(needle))
})
</script>

<template>
  <div class="flex items-center gap-2 border-b border-(--border) p-3">
    <TextInput v-model="search" :placeholder="t('messages.search')" class="min-w-0 flex-1"/>
    <IconButton :icon="['fas', 'pen-to-square']" :label="t('messages.newMessage')" data-testid="messages-new"
                class="text-primary hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent" @click="starting = true"/>
    <IconButton :icon="['fas', 'gear']" :label="t('messages.settings.title')"
                class="text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent" @click="settings = true"/>
  </div>
  <div v-if="hasStationChats" role="group" :aria-label="t('messages.filter.label')" class="flex gap-1 px-3 pt-2">
    <BareButton v-for="choice in FILTERS" :key="choice" :aria-pressed="filter === choice"
                :data-testid="`messages-filter-${choice}`"
                :class="filter === choice ? 'bg-primary/10 text-primary' : 'text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent'"
                class="rounded-full px-3 py-1 text-xs font-medium" @click="filter = choice">
      {{ t(`messages.filter.${choice}`) }}
    </BareButton>
  </div>
  <nav :aria-label="t('messages.listLabel')" class="flex-1 overflow-y-auto p-2 space-y-1">
    <Spinner v-if="loading" size="md"/>
    <FailureAlert :failure="failure"/>
    <EmptyState v-if="!loading && conversations.length === 0" compact :message="t('messages.empty')"/>
    <EmptyState v-else-if="!loading && shown.length === 0" compact :message="t('messages.noMatch')"/>
    <ConversationRow v-for="conversation in shown" :key="conversation.uid" :conversation="conversation"
                     :selected="conversation.uid === openUid"/>
  </nav>
  <NewConversationModal v-model="starting" :on-started="onChanged"/>
  <MessagesSettingsModal v-model="settings"/>
</template>
