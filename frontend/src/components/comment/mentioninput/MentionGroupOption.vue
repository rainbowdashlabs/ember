/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {MemberGroup} from '@/api/generated/schema'

const props = defineProps<{
  data: MemberGroup
}>()

const {t} = useI18n()

const tint = computed(() => props.data.color ?? 'var(--color-secondary)')
</script>

<template>
  <div class="h-6 w-6 shrink-0 rounded-full flex items-center justify-center"
       :style="{ backgroundColor: `color-mix(in srgb, ${tint} 12.5%, transparent)`, color: tint }">
    <font-awesome-icon :icon="['fas', 'layer-group']" class="h-3 w-3" />
  </div>
  <span class="font-medium truncate">{{ data.name }}</span>
  <span class="text-[10px] text-(--text-muted) shrink-0">{{ t('comments.mentionGroup') }}</span>
</template>
