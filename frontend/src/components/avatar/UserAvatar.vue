/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import client from '@/api/client'

const props = withDefaults(defineProps<{
  accountId?: number | null
  size?: 'sm' | 'md' | 'lg'
  name?: string
}>(), {
  size: 'md',
})

const hasAvatar = ref(false)
const imgSrc = ref('')

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
  if (parts.length >= 2) return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
  return parts[0][0]?.toUpperCase() ?? '?'
})

function loadAvatar() {
  if (!props.accountId) {
    hasAvatar.value = false
    return
  }
  const url = `${client.defaults.baseURL}/accounts/${props.accountId}/avatar`
  const img = new Image()
  img.onload = () => {
    imgSrc.value = url
    hasAvatar.value = true
  }
  img.onerror = () => {
    hasAvatar.value = false
  }
  img.src = url
}

watch(() => props.accountId, loadAvatar, {immediate: true})
</script>

<template>
  <div
      :class="sizeClasses"
      class="shrink-0 rounded-full overflow-hidden bg-primary/15 text-primary font-bold flex items-center justify-center"
  >
    <img v-if="hasAvatar" :src="imgSrc" alt="" class="h-full w-full object-cover"/>
    <span v-else>{{ initials }}</span>
  </div>
</template>
