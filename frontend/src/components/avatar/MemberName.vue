/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import UserAvatar from './UserAvatar.vue'

withDefaults(defineProps<{
  name: string
  memberId?: number | null
  size?: 'sm' | 'md' | 'lg'
}>(), {
  size: 'sm',
})
</script>

<template>
  <span class="inline-flex items-center gap-1.5">
    <UserAvatar :member-id="memberId" :name="name" :size="size"/>
    <span><slot>{{ name }}</slot></span>
  </span>
</template>
