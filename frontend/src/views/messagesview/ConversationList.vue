/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
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

const search = ref('')
const starting = ref(false)
const settings = ref(false)

const shown = computed(() => {
  const needle = search.value.trim().toLocaleLowerCase()
  if (!needle) return props.conversations
  return props.conversations.filter(conversation => titleOf(conversation).toLocaleLowerCase().includes(needle))
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
