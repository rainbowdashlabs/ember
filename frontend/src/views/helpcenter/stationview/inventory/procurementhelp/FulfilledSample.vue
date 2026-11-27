/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'

/** A fulfilled order as the procurement page keeps it: folded away below the open ones, here unfolded. */
const {t} = useI18n()
</script>

<template>
  <details open>
    <summary class="cursor-pointer text-sm text-(--text-muted)">
      {{ t('procurement.doneSummary', {count: 1}) }}
    </summary>
    <NeutralContainer class="mt-3">
      <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
        <div class="space-y-1">
          <div class="flex items-center gap-2 flex-wrap">
            <span class="font-medium">{{ t('helpCenter.sample.equipment.jackets') }}</span>
            <span class="text-sm text-(--text-muted)">(M)</span>
            <SuccessBadge>{{ t('procurement.fulfilled') }}</SuccessBadge>
          </div>
          <div class="text-sm text-(--text-muted)">{{ t('helpCenter.sample.people.erikaMusterfrau') }}, 01.05.2026</div>
          <div class="text-xs text-(--text-muted)">{{ t('procurement.fulfilledAt') }}: 08.05.2026</div>
        </div>
        <div class="flex items-center gap-2 shrink-0">
          <DeleteButton />
        </div>
      </div>
    </NeutralContainer>
  </details>
</template>
