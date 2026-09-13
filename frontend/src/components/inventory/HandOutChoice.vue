/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import RadioInput from '@/components/input/toggle/RadioInput.vue'

/** Whether a piece goes over the counter now, or is written down as promised and tracked. */
export type HandOutMode = 'NOW' | 'PLANNED'

/**
 * The choice every screen that hands a piece to a member offers.
 *
 * <p>Over the counter there is nothing to follow: the piece changes hands and the record says so.
 * Planned is the other case, where somebody decides today what a member gets and the handing over
 * happens later, so there has to be something open to look at in the meantime.
 *
 * <p>One component for all of it, because a choice that reads differently on each screen is read as
 * a different choice.
 */
const mode = defineModel<HandOutMode>({default: 'NOW'})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-1" data-testid="hand-out-choice">
    <span class="text-sm font-medium">{{ t('inventory.handOut.label') }}</span>
    <div class="flex flex-wrap gap-4">
      <label class="flex items-center gap-2 text-sm">
        <RadioInput v-model="mode" data-testid="hand-out-now" value="NOW"/>
        <span>{{ t('inventory.handOut.now') }}</span>
      </label>
      <label class="flex items-center gap-2 text-sm">
        <RadioInput v-model="mode" data-testid="hand-out-planned" value="PLANNED"/>
        <span>{{ t('inventory.handOut.planned') }}</span>
      </label>
    </div>
    <p class="text-xs text-(--text-muted)">
      {{ mode === 'PLANNED' ? t('inventory.handOut.hintPlanned') : t('inventory.handOut.hintNow') }}
    </p>
  </div>
</template>
