/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {watchEffect} from 'vue'
import {useI18n} from 'vue-i18n'
import RadioInput from '@/components/input/toggle/RadioInput.vue'
import {StationModule} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import HintIcon from '@/components/typography/HintIcon.vue'

/** Whether a piece goes over the counter now, or is written down as promised and tracked. */
export type HandOutMode = 'NOW' | 'PLANNED'

/**
 * The choice every screen that hands a piece to a member offers.
 *
 * <p>Over the counter there is nothing to follow: the piece changes hands and the record shows that.
 * Planned is the other case, where somebody decides today what a member gets and the handing over
 * happens later, so there has to be something open to look at in the meantime.
 *
 * <p>One component for all of it, because a choice that reads differently on each screen is read as
 * a different choice.
 *
 * <p>A planned hand-out is a movement, so a station that switched its movements off is offered no
 * choice: the piece goes over the counter, whatever the screen asked for as its default.
 *
 * <p>What each option does is shown behind a hint beside it rather than under the pair.
 */
const mode = defineModel<HandOutMode>({default: 'NOW'})

const {t} = useI18n()
const {isModuleEnabled} = useSession()

watchEffect(() => {
  if (mode.value === 'PLANNED' && !isModuleEnabled(StationModule.INVENTORY_MOVEMENTS)) mode.value = 'NOW'
})
</script>

<template>
  <div v-if="isModuleEnabled(StationModule.INVENTORY_MOVEMENTS)" class="space-y-1" data-testid="hand-out-choice">
    <span class="text-sm font-medium">{{ t('inventory.handOut.label') }}</span>
    <div class="flex flex-wrap gap-4">
      <label class="flex items-center gap-2 text-sm">
        <RadioInput v-model="mode" data-testid="hand-out-now" value="NOW"/>
        <span>{{ t('inventory.handOut.now') }}</span>
        <HintIcon :text="t('inventory.handOut.hintNow')"/>
      </label>
      <label class="flex items-center gap-2 text-sm">
        <RadioInput v-model="mode" data-testid="hand-out-planned" value="PLANNED"/>
        <span>{{ t('inventory.handOut.planned') }}</span>
        <HintIcon :text="t('inventory.handOut.hintPlanned')"/>
      </label>
    </div>
  </div>
</template>
