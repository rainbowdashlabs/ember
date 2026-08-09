/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {ProfileFieldChange} from '@/api/profileFieldChanges'

defineProps<{
  acknowledgements: ProfileFieldChange['acknowledgements']
  formatDate: (dateStr?: string) => string
}>()
</script>

<template>
  <div v-if="acknowledgements.length > 0" class="text-xs text-(--text-muted) space-y-1">
    <div v-for="ack in acknowledgements" :key="ack.id" class="flex items-center gap-1">
      <font-awesome-icon :icon="['fas', 'check']" class="h-3 w-3 text-success"/>
      <span>{{ ack.acknowledgedByName }} ({{ formatDate(ack.acknowledgedAt) }})</span>
      <span v-if="ack.comment" class="italic ml-1">
        <font-awesome-icon :icon="['fas', 'comment']" class="mr-1"/>
        {{ ack.comment }}
      </span>
    </div>
  </div>
</template>
