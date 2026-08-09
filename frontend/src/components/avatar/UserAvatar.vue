/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import type {MemberIdentity} from '@/api/types'
import {useAuthImage} from '@/composables/useAuthImage'

const props = withDefaults(defineProps<{
  identity?: MemberIdentity | null
  size?: 'sm' | 'md' | 'lg'
  name?: string
}>(), {
  size: 'md',
})

const sizeClasses = computed(() => {
  switch (props.size) {
    case 'sm':
      return 'h-6 w-6 text-xs'
    case 'lg':
      return 'h-12 w-12 text-lg'
    default:
      return 'h-8 w-8 text-sm'
  }
})

const initials = computed(() => {
  if (!props.name) return '?'
  const parts = props.name.trim().split(/\s+/)
  const first = parts[0]?.[0] ?? ''
  const last = parts.length >= 2 ? (parts[parts.length - 1]?.[0] ?? '') : ''
  return (first + last).toUpperCase() || '?'
})

function resolveUrl(): string | null {
  if (props.identity?.accountUid) {
    return `/accounts/${props.identity.accountUid}/avatar?size=64`
  }
  if (props.identity?.memberUid && props.identity?.stationUid) {
    return `/members/${props.identity.stationUid}/${props.identity.memberUid}/avatar?size=64`
  }
  return null
}

const {src: imgSrc} = useAuthImage(() => resolveUrl())
</script>

<template>
  <div
      :class="sizeClasses"
      :title="name"
      class="shrink-0 rounded-full overflow-hidden bg-primary/15 text-primary font-bold flex items-center justify-center"
  >
    <img v-if="imgSrc" :src="imgSrc" alt="" loading="lazy" class="h-full w-full object-cover"/>
    <span v-else>{{ initials }}</span>
  </div>
</template>
