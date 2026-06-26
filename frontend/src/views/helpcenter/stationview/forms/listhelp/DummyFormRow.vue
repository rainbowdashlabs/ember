/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps<{
  title: string
  description?: string
  managerView: boolean
  showLock?: boolean
}>()
</script>

<template>
  <NeutralContainer>
    <div class="flex items-center justify-between">
      <div class="space-y-1">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="font-medium">{{ title }}</span>
          <slot name="lock"/>
          <slot name="badge"/>
        </div>
        <p v-if="description" class="text-xs text-(--text-muted)">{{ description }}</p>
      </div>
      <div class="flex gap-2">
        <slot name="actions"/>
      </div>
    </div>
  </NeutralContainer>
</template>
