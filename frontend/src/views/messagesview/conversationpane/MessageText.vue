/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {segmentsOf} from '@/util/messageText'

/**
 * A message's text as written: line breaks kept, addresses as links that open in a new tab, and
 * mentions highlighted, the reader's own more strongly.
 */
const props = defineProps<{
  content: string
  readerUid: string | null
}>()

const segments = computed(() => segmentsOf(props.content))
</script>

<template>
  <p class="whitespace-pre-wrap break-words text-sm">
    <template v-for="(segment, index) in segments" :key="index">
      <a v-if="segment.kind === 'link'" :href="segment.href" target="_blank" rel="noopener noreferrer nofollow"
         class="underline">{{ segment.text }}</a>
      <span v-else-if="segment.kind === 'mention'"
            :class="segment.uid === readerUid ? 'bg-primary/20 text-primary' : 'text-primary'"
            class="font-semibold" data-testid="mention">@{{ segment.name }}</span>
      <template v-else>{{ segment.text }}</template>
    </template>
  </p>
</template>
