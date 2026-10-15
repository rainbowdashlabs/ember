/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import MutedText from '@/components/typography/MutedText.vue'
import {formatDateLong} from '@/util/format'

/** The line between two days of a conversation, naming the day that starts. */
defineProps<{
  /** A moment of the day that starts. */
  day: string
}>()
</script>

<template>
  <div class="my-3 flex items-center gap-3" role="separator">
    <div class="h-px flex-1 bg-(--border)"/>
    <MutedText class="font-semibold">{{ formatDateLong(day) }}</MutedText>
    <div class="h-px flex-1 bg-(--border)"/>
  </div>
</template>
