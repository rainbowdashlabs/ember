/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import IconButton from '@/components/button/IconButton.vue'

defineProps<{
  name: string
  boardKey: string
  description: string
  fullAccess?: boolean
  bookmarked?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="cursor-pointer hover:ring-2 hover:ring-primary transition-all">
    <div class="flex items-start justify-between gap-2">
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="font-semibold">{{ name }}</span>
          <code class="text-xs font-mono bg-bg-light-accent dark:bg-bg-dark-accent px-1.5 py-0.5 rounded">{{ boardKey }}</code>
        </div>
        <p class="text-sm mt-1 text-text-light-secondary dark:text-text-dark-secondary">{{ description }}</p>
      </div>
      <IconButton
        v-if="bookmarked"
        :icon="['fas', 'star']"
        :label="t('boards.bookmarked')"
        class="text-yellow-500 hover:text-yellow-600" />
      <IconButton
        v-else
        :icon="['fas', 'star']"
        :label="t('boards.bookmark')"
        class="text-text-light-secondary dark:text-text-dark-secondary hover:text-yellow-500" />
    </div>
    <div class="flex items-center gap-2 mt-3">
      <SuccessBadge v-if="fullAccess" class="inline-flex items-center gap-1">
        <font-awesome-icon :icon="['fas', 'pen']" class="text-[0.65rem]" />
        {{ t('boards.fullAccessBadge') }}
      </SuccessBadge>
      <SecondaryBadge v-else class="inline-flex items-center gap-1">
        <font-awesome-icon :icon="['fas', 'lock']" class="text-[0.65rem]" />
        {{ t('boards.readOnlyBadge') }}
      </SecondaryBadge>
    </div>
  </NeutralContainer>
</template>
