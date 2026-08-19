/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'

/**
 * The two ages a list keeps. Both need the list to have a date of birth field: without one there is
 * nothing to work an age out from, and both are simply never reached.
 */
const minAgeRegister = defineModel<number | null>('minAgeRegister', { required: true })
const minAgeJoin = defineModel<number | null>('minAgeJoin', { required: true })

const { t } = useI18n()
</script>

<template>
  <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
    <div class="space-y-1">
      <FieldLabel>{{ t('waitingList.minAgeRegister') }}</FieldLabel>
      <NumberInput :model-value="minAgeRegister ?? undefined" @update:model-value="minAgeRegister = $event ?? null"/>
      <p class="text-xs text-(--text-muted)">{{ t('waitingList.minAgeRegisterHint') }}</p>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('waitingList.minAgeJoin') }}</FieldLabel>
      <NumberInput :model-value="minAgeJoin ?? undefined" @update:model-value="minAgeJoin = $event ?? null"/>
      <p class="text-xs text-(--text-muted)">{{ t('waitingList.minAgeJoinHint') }}</p>
    </div>
  </div>
</template>
