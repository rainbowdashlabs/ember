/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SubHeader from '@/components/typography/SubHeader.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import {emberLogo} from '@/composables/useEmberLogo'
import {formatDateTime} from '@/util/format'
import type {NewsEntry} from '@/api/news'

/**
 * Who wrote an entry and when, with the actions a manager has on it beside them.
 *
 * <p>An entry the instance wrote is shown under the Ember logo rather than under an avatar: the
 * instance is nobody's member, so there is no face to draw and initials of a product name are not a
 * person. The actions are left off it as well, because editing, deleting and asking who has read it
 * are all a station's rights over its own entries, and a system entry belongs to no station: every
 * one of those buttons leads to a refusal.
 */
defineProps<{
  entry: NewsEntry
  canManage: boolean
}>()

const logo = emberLogo()
</script>

<template>
  <div class="flex items-start justify-between gap-3">
    <div class="flex items-center gap-2">
      <LayeredEmberLogo
          v-if="entry.systemEntry"
          :layers="logo.layers"
          :active-layers="logo.activeLayers"
          :pixel-size="64"
          data-testid="system-entry-logo"
          size="h-10 w-10 shrink-0"
      />
      <UserAvatar v-else :identity="entry.author" :name="entry.author?.name ?? entry.authorName" size="md"/>
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
    <div v-if="canManage && !entry.systemEntry" class="flex items-center gap-1 shrink-0">
      <slot name="actions"/>
    </div>
  </div>
</template>
