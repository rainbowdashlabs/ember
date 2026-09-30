/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import type {MemberCompletion} from '@/api/stationMembers'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import DisplayTagBadge from '@/components/badge/DisplayTagBadge.vue'

defineProps<{
  data: MemberCompletion
}>()
</script>

<template>
  <UserAvatar :identity="{ stationUid: data.stationUid, memberUid: data.memberUid }" :name="data.name" size="sm" />
  <span class="font-medium truncate" :style="data.nameColor ? { color: data.nameColor } : {}">{{ data.name }}</span>
  <DisplayTagBadge v-if="data.displayTag" :tag="data.displayTag" class="shrink-0"/>
</template>
