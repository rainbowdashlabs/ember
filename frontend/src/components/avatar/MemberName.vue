/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import UserAvatar from './UserAvatar.vue'

withDefaults(defineProps<{
  name: string
  accountId?: number | null
  size?: 'sm' | 'md' | 'lg'
}>(), {
  size: 'sm',
})
</script>

<template>
  <span class="inline-flex items-center gap-1.5">
    <UserAvatar :account-id="accountId" :name="name" :size="size"/>
    <span><slot>{{ name }}</slot></span>
  </span>
</template>
