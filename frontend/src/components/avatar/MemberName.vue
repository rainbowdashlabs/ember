/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import UserAvatar from './UserAvatar.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import type {MemberIdentity} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {computed} from 'vue'

const props = withDefaults(defineProps<{
  identity: MemberIdentity | null | undefined
  size?: 'sm' | 'md' | 'lg'
}>(), {
  size: 'sm',
})

const {sessionInfo} = useSession()
const isExternal = computed(() => {
  if (!props.identity?.stationUid) return false
  return props.identity.stationUid !== sessionInfo.value?.stationId
})
// Fall back to the station name for federated members whose display name couldn't be resolved
// (no local mapping, no cached name) so the comment still shows a meaningful author label
// instead of an empty span next to a "?" avatar.
const displayName = computed(() => {
  if (props.identity?.name) return props.identity.name
  if (isExternal.value && props.identity?.stationName) return props.identity.stationName
  return ''
})
</script>

<template>
  <span class="inline-flex items-center gap-1.5">
    <UserAvatar :identity="identity" :name="displayName" :size="size"/>
    <span :style="identity?.nameColor ? { color: identity.nameColor } : {}"><slot>{{ displayName }}</slot></span>
    <span v-if="identity?.displayTag"
          class="inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-medium leading-none"
          :style="{ backgroundColor: identity.displayTag.color + '20', color: identity.displayTag.color }">
      {{ identity.displayTag.name }}
    </span>
    <StationBadge v-if="isExternal && identity?.name" :station-name="identity?.stationName ?? ''" />
  </span>
</template>
