/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="flex flex-col sm:flex-row sm:items-start justify-between gap-2">
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="font-medium">20.06.2026 – 22.06.2026</span>
          <PrimaryBadge>{{ t('helpCenter.inventoryLendingBlocks.dummyScopeAll') }}</PrimaryBadge>
        </div>
        <MutedText tag="div" size="sm" class="mt-1">Stadtfest 2026</MutedText>
      </div>
      <DeleteButton />
    </div>
  </NeutralContainer>
</template>
