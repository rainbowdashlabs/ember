/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ExchangeReasonPicker from '@/components/movement/ExchangeReasonPicker.vue'
import type {InventorySize} from '@/api/generated/schema'

/**
 * Why, in the words of whoever is starting it, and the size where a swap asks for a different one.
 *
 * <p>Every chain carries the reason, and it is the only part of a movement nobody else can write later.
 * A swap asks it the way every swap does, with the size of the piece handed in beside the one wanted.
 */
const props = defineProps<{
  /** Whether a different size can be asked for, which only a swap does. */
  asksForASize: boolean
  sizes: InventorySize[]
  /** The size of the piece handed in, for a swap. */
  currentSizeId: number | null
}>()

const reason = defineModel<string>('reason', {required: true})
const newSizeId = defineModel<number | null>('newSizeId', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <SubHeader>{{ t('movements.wizard.reason.title') }}</SubHeader>

    <ExchangeReasonPicker
        v-if="props.asksForASize"
        v-model:new-size-id="newSizeId"
        v-model:text="reason"
        :sizes="props.sizes"
        :current-size-id="props.currentSizeId"
        test-id="wizard"
    />

    <div v-else class="space-y-1">
      <FieldLabel>{{ t('movements.wizard.reason.label') }}</FieldLabel>
      <TextAreaInput v-model="reason" data-testid="wizard-reason"
                     :placeholder="t('movements.wizard.reason.placeholder')"/>
    </div>
  </div>
</template>
