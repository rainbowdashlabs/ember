/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import MutedText from '@/components/typography/MutedText.vue'
import AppIcon from '@/components/display/AppIcon.vue'
import type {ConversationMessageResponse} from '@/api/generated/schema'
import {useMessageWords} from '@/composables/useMessageWords'
import {formatTime} from '@/util/format'

/** A quiet line in a group saying who joined, left, was removed, renamed it or took it over. */
defineProps<{
  message: ConversationMessageResponse
}>()

const {systemLine} = useMessageWords()
</script>

<template>
  <p class="my-1 flex items-center gap-2 px-2" :data-testid="`system-${message.id}`">
    <AppIcon :icon="['fas', 'circle-info']" class="h-3 w-3 text-(--text-muted)"/>
    <MutedText>{{ systemLine({...message, text: message.content}) }}</MutedText>
    <MutedText class="text-[10px]">{{ formatTime(message.createdAt) }}</MutedText>
  </p>
</template>
