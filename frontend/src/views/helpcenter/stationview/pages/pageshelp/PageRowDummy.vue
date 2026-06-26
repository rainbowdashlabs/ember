/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'

defineProps<{
  name: string
  slug: string
  status: 'published' | 'draft'
  toggleIcon: string
  toggleLabel: string
  indented?: boolean
  starred?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="flex items-center gap-3" :style="indented ? 'margin-left: 1.5rem' : ''">
    <div class="text-[var(--text-muted)] shrink-0">
      <font-awesome-icon :icon="['fas', 'grip-vertical']" class="h-4 w-4"/>
    </div>
    <div class="flex-1 min-w-0 flex items-center gap-2 flex-wrap">
      <span class="font-medium">{{ name }}</span>
      <span class="text-xs text-[var(--text-muted)]">/{{ slug }}</span>
      <SuccessBadge v-if="status === 'published'">{{ t('stationPages.published') }}</SuccessBadge>
      <SecondaryBadge v-else>{{ t('stationPages.draft') }}</SecondaryBadge>
      <font-awesome-icon v-if="starred" :icon="['fas', 'star']" class="h-4 w-4 text-info-accent"/>
    </div>
    <div class="flex items-center gap-1 shrink-0">
      <EditButton/>
      <IconButton :icon="['fas', 'clone']" :label="t('stationPages.duplicate')"/>
      <IconButton :icon="['fas', toggleIcon]" :label="toggleLabel"/>
      <DeleteButton/>
    </div>
  </NeutralContainer>
</template>
