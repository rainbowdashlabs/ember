/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps<{
  name: string
  type: string
  withSizes?: boolean
  /** What the card says the inventory is offered as, where it can be offered at all. */
  shareState?: string
}>()
</script>

<template>
  <NeutralContainer clickable>
    <div class="flex items-center justify-between">
      <div>
        <span class="font-medium">{{ name }}</span>
        <MutedText class="ml-2">{{ type }}</MutedText>
        <span v-if="withSizes" class="ml-2 text-xs text-secondary-accent dark:text-secondary">
          <slot name="sizesLabel"/>
        </span>
        <SecondaryBadge v-if="shareState" class="ml-2">{{ shareState }}</SecondaryBadge>
      </div>
      <div class="flex items-center gap-2">
        <MutedIconButton
            v-if="shareState"
            :icon="['fas', 'share-nodes']"
            :label="t('lendingShare.edit')"
        />
        <EditButton />
        <DeleteButton />
      </div>
    </div>
    <MutedText tag="div" class="mt-1">
      <slot name="meta"/>
    </MutedText>
  </NeutralContainer>
</template>
