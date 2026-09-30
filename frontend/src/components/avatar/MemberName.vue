/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import UserAvatar from './UserAvatar.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import DisplayTagBadge from '@/components/badge/DisplayTagBadge.vue'
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
    <span data-testid="member-name" :style="identity?.nameColor ? { color: identity.nameColor } : {}"><slot>{{ displayName }}</slot></span>
    <DisplayTagBadge v-if="identity?.displayTag" :tag="identity.displayTag"/>
    <StationBadge v-if="isExternal && identity?.name" :station-name="identity?.stationName ?? ''" />
  </span>
</template>
