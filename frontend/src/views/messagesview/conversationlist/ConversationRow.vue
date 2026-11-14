/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import RowLink from '@/components/navigation/RowLink.vue'
import CountBadge from '@/components/badge/CountBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import AppIcon from '@/components/display/AppIcon.vue'
import type {ConversationSummaryResponse} from '@/api/generated/schema'
import {useMessageWords} from '@/composables/useMessageWords'
import {formatRelative} from '@/util/format'
import ConversationAvatar from './ConversationAvatar.vue'
import StationChatAvatar from './StationChatAvatar.vue'

/**
 * One conversation in the list: who it is with, the start of its newest line, when, and how much
 * waits unread. A muted conversation is greyed out but keeps its place and its count. A station chat
 * shows its station's picture and name, and whether it is closed.
 */
const props = defineProps<{
  conversation: ConversationSummaryResponse
  selected: boolean
}>()

const {t} = useI18n()
const {titleOf, nameOf, systemLine} = useMessageWords()

const title = computed(() => titleOf(props.conversation))

const preview = computed(() => {
  const newest = props.conversation.newest
  if (!newest) return t('messages.nothingYet')
  if (newest.systemKind) return systemLine(newest)
  if (newest.removedByStation) return t('messages.removedByStation')
  if (newest.deleted) return t('messages.deleted')
  if (props.conversation.kind !== 'DIRECT') return `${newest.author?.firstName ?? nameOf(null)}: ${newest.text}`
  return newest.text
})
</script>

<template>
  <RowLink :to="`/messages/${conversation.uid}`">
    <div :class="[selected ? 'bg-primary/10' : 'hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent', conversation.muted ? 'opacity-60' : '']"
         :data-testid="`conversation-${conversation.uid}`"
         class="flex items-center gap-3 rounded-theme px-2 py-2">
      <StationChatAvatar v-if="conversation.station" :station-uid="conversation.station.stationUid"/>
      <ConversationAvatar v-else :people="conversation.others"/>
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2">
          <span class="truncate text-sm" :class="conversation.unread > 0 ? 'font-bold' : 'font-medium'">{{ title }}</span>
          <AppIcon v-if="conversation.muted" :icon="['fas', 'bell-slash']" :title="t('messages.muted')"
                   class="h-3 w-3 shrink-0 text-(--text-muted)"/>
        </div>
        <MutedText v-if="conversation.station" tag="p" class="flex items-center gap-1 truncate text-[11px]"
                   :data-testid="`conversation-station-${conversation.uid}`">
          <AppIcon :icon="['fas', 'house-flag']" class="h-3 w-3 shrink-0"/>
          {{ t('messages.station.marker', {station: conversation.station.stationName}) }}
          <template v-if="conversation.station.closed"> · {{ t('messages.station.closed') }}</template>
        </MutedText>
        <MutedText tag="p" class="truncate">{{ preview }}</MutedText>
      </div>
      <div class="flex shrink-0 flex-col items-end gap-1">
        <MutedText>{{ formatRelative(conversation.lastActivityAt) }}</MutedText>
        <CountBadge v-if="conversation.unread > 0" :count="conversation.unread" small/>
      </div>
    </div>
  </RowLink>
</template>
