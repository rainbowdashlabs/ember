/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import type {MemberGroup} from '@/api/types'

defineProps<{
  data: MemberGroup
}>()

const {t} = useI18n()
</script>

<template>
  <div class="h-6 w-6 shrink-0 rounded-full flex items-center justify-center"
       :style="{ backgroundColor: (data.color ?? 'var(--secondary)') + '20', color: data.color ?? 'var(--secondary)' }">
    <font-awesome-icon :icon="['fas', 'layer-group']" class="h-3 w-3" />
  </div>
  <span class="font-medium truncate">{{ data.name }}</span>
  <span class="text-[10px] text-(--text-muted) shrink-0">{{ t('comments.mentionGroup') }}</span>
</template>
