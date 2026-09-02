/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'

/** One card of the example list, so the help page shows the three states an entry can be in. */
defineProps<{
  name: string
  foundAt: string
  /** What the badge says about the claim, or nothing at all where the entry is still free. */
  claimLabel?: string
  /** True where the card stands for an entry that carries a picture. */
  hasImage: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="w-full h-28 rounded bg-bg-light-accent dark:bg-bg-dark-accent flex items-center justify-center text-(--text-muted)">
      <font-awesome-icon :icon="['fas', hasImage ? 'image' : 'box-open']" class="text-2xl"/>
    </div>
    <div class="space-y-1">
      <p class="text-sm font-medium">{{ name }}</p>
      <p class="text-xs text-(--text-muted)">{{ t('lostAndFound.foundAt') }}: {{ foundAt }}</p>
      <SuccessBadge v-if="claimLabel">{{ claimLabel }}</SuccessBadge>
    </div>
    <slot/>
  </NeutralContainer>
</template>
