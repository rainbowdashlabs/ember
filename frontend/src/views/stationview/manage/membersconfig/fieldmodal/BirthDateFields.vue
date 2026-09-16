/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

/**
 * What a birth date does beyond holding a date.
 *
 * <p>It is written with the age behind it, which is the number a reader is usually after. A station
 * that also asks the age as a question of its own has it twice then, and the two can read
 * differently where the one is taken today and the other at the end of the year, so the date says
 * it on its own or leaves it to the question.
 */
const showAge = defineModel<boolean>('showAge', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-1">
    <div class="flex items-center justify-between">
      <label class="text-sm font-medium">{{ t('membersConfig.fieldShowAge') }}</label>
      <ToggleInput v-model="showAge"/>
    </div>
    <p class="text-xs text-(--text-muted)">{{ t('membersConfig.fieldShowAgeHint') }}</p>
  </div>
</template>
