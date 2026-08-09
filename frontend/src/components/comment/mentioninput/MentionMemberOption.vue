/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import type {MemberCompletion} from '@/api/stationMembers'
import UserAvatar from '@/components/avatar/UserAvatar.vue'

defineProps<{
  data: MemberCompletion
}>()
</script>

<template>
  <UserAvatar :identity="{ stationUid: data.stationUid, memberUid: data.memberUid }" :name="data.name" size="sm" />
  <span class="font-medium truncate" :style="data.nameColor ? { color: data.nameColor } : {}">{{ data.name }}</span>
  <span v-if="data.displayTag"
        class="inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-medium leading-none shrink-0"
        :style="{ backgroundColor: data.displayTag.color + '20', color: data.displayTag.color }">
    {{ data.displayTag.name }}
  </span>
</template>
