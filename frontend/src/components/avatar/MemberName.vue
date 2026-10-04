/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import UserAvatar from './UserAvatar.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import DisplayTagBadge from '@/components/badge/DisplayTagBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {PersonIdentity} from '@/util/personIdentity'
import {useSession} from '@/composables/useSession'
import {computed} from 'vue'

/**
 * A member's avatar and name, with an optional second line (such as the address) that stands under
 * the name rather than under the avatar, so it lines up whatever size the picture has. The name keeps
 * to one line, cut short with an ellipsis only where the space runs out.
 */
const props = withDefaults(defineProps<{
  identity: PersonIdentity | null | undefined
  size?: 'sm' | 'md' | 'lg'
  detail?: string | null
}>(), {
  size: 'sm',
  detail: null,
})

const {sessionInfo} = useSession()
const isExternal = computed(() => {
  if (!props.identity?.stationUid) return false
  return props.identity.stationUid !== sessionInfo.value?.stationId
})
/**
 * The name to show, falling back to the station's name for a member of another station whose own
 * name could not be resolved, so the author never reads as an empty label beside a "?" avatar.
 */
const displayName = computed(() => {
  if (props.identity?.name) return props.identity.name
  if (isExternal.value && props.identity?.stationName) return props.identity.stationName
  return ''
})
</script>

<template>
  <span class="inline-flex min-w-0 max-w-full items-center gap-1.5">
    <UserAvatar :identity="identity" :name="displayName" :size="size"/>
    <span class="inline-flex min-w-0 flex-col">
      <span class="inline-flex min-w-0 items-center gap-1.5">
        <span data-testid="member-name" class="truncate" :style="identity?.nameColor ? { color: identity.nameColor } : {}"><slot>{{ displayName }}</slot></span>
        <DisplayTagBadge v-if="identity?.displayTag" :tag="identity.displayTag"/>
        <StationBadge v-if="isExternal && identity?.name" :station-name="identity?.stationName ?? ''" />
      </span>
      <MutedText v-if="detail" data-testid="member-detail" class="truncate font-normal">{{ detail }}</MutedText>
    </span>
  </span>
</template>
