/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SubHeader from '@/components/typography/SubHeader.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import {formatDateTime} from '@/util/format'
import type {NewsEntry} from '@/api/news'

/**
 * Who wrote an entry and when, with the actions a manager has on it beside them.
 */
defineProps<{
  entry: NewsEntry
  canManage: boolean
}>()
</script>

<template>
  <div class="flex items-start justify-between gap-3">
    <div class="flex items-center gap-2">
      <UserAvatar :identity="entry.author" :name="entry.author?.name ?? entry.authorName" size="md"/>
      <div>
        <SubHeader class="flex items-center gap-1">
          {{ entry.title }}
          <font-awesome-icon v-if="entry.restricted" :icon="['fas', 'lock']"
                             class="ml-1 h-3 w-3 text-[var(--text-muted)]"/>
        </SubHeader>
        <p class="text-xs text-(--text-muted)">
          {{ entry.author?.name ?? entry.authorName }} &middot; {{ formatDateTime(entry.publishedAt) }}
        </p>
      </div>
    </div>
    <div v-if="canManage" class="flex items-center gap-1 shrink-0">
      <slot name="actions"/>
    </div>
  </div>
</template>
