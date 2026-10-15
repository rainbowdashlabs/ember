/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import MessagesFrame from '@/views/messagesview/MessagesFrame.vue'
import ConversationList from '@/views/messagesview/ConversationList.vue'
import ConversationPane from '@/views/messagesview/ConversationPane.vue'
import {useConversationList} from '@/composables/useConversationList'

/**
 * Messages between people: the list of the reader's conversations on the left and the open one on
 * the right, outside any station. On a phone the list is the page and a conversation opens over it.
 */
const {t} = useI18n()
const route = useRoute()
const list = useConversationList()

const openUid = computed(() => {
  const value = route.params.conversationUid
  return typeof value === 'string' && value ? value : null
})
</script>

<template>
  <ViewContent :title="t('pages.messages.title')" :subtitle="t('pages.messages.subtitle')">
    <MessagesFrame :open="openUid !== null">
      <template #list>
        <ConversationList :conversations="list.conversations.value" :loading="list.loading.value"
                          :failure="list.failure.value" :open-uid="openUid" :on-changed="list.refresh"/>
      </template>
      <template #conversation>
        <ConversationPane v-if="openUid" :key="openUid" :uid="openUid" :on-changed="list.refresh"/>
        <EmptyState v-else :message="t('messages.chooseConversation')"/>
      </template>
    </MessagesFrame>
  </ViewContent>
</template>
