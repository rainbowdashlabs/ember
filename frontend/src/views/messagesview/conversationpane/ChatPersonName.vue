/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import DisplayTagBadge from '@/components/badge/DisplayTagBadge.vue'
import type {MemberIdentity} from '@/api/generated/schema'

/**
 * A person's name in a conversation. In a station chat the station knows them as a member, and the
 * name then takes the color of their group and carries their tag, the way the rest of the station
 * shows them. Elsewhere, and for somebody no longer at the station, it is the plain name.
 */
defineProps<{
  name: string
  member?: MemberIdentity | null
}>()
</script>

<template>
  <span class="inline-flex min-w-0 items-center gap-1.5">
    <span data-testid="chat-person-name" class="truncate" :style="member?.nameColor ? {color: member.nameColor} : {}">{{ name }}</span>
    <DisplayTagBadge v-if="member?.displayTag" :tag="member.displayTag"/>
  </span>
</template>
