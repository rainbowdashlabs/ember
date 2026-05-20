/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import client from '@/api/client'

const props = withDefaults(defineProps<{
  memberId?: number | null
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

function revokeOld() {
  if (imgSrc.value && imgSrc.value.startsWith('blob:')) {
    URL.revokeObjectURL(imgSrc.value)
  }
}

async function loadAvatar() {
  revokeOld()
  hasAvatar.value = false
  imgSrc.value = ''

  if (!props.memberId) return

  try {
    const res = await client.get(`/members/${props.memberId}/avatar`, {
      responseType: 'blob',
      validateStatus: (status) => status === 200 || status === 404,
    })
    if (res.status === 200 && res.data) {
      imgSrc.value = URL.createObjectURL(res.data)
      hasAvatar.value = true
    }
  } catch { /* no avatar */ }
}

watch(() => props.memberId, loadAvatar, {immediate: true})
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
