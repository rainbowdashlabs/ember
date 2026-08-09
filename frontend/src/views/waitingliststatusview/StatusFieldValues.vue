/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import type { WaitingListPublicStatus } from '@/api/waitingList'

const props = defineProps<{ status: WaitingListPublicStatus }>()
</script>

<template>
  <div class="border-t border-bg-light-accent dark:border-bg-dark-accent pt-3 space-y-2">
    <div v-for="field in props.status.fields" :key="field.id" class="text-sm">
      <span class="text-(--text-muted)">{{ field.name }}:</span>
      <span class="ml-1 font-medium">
        {{ props.status.values?.find(v => v.fieldId === field.id)?.value || '-' }}
      </span>
    </div>
  </div>
</template>
