/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import ProblemLevelSummary from '@/components/problem/ProblemLevelSummary.vue'

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-wrap items-center gap-2 mb-2">
    <SelectionToggleButton :selected="true">{{ t('adminProblems.faults') }}</SelectionToggleButton>
    <SelectionToggleButton :selected="false">{{ t('adminProblems.refusals') }}</SelectionToggleButton>
  </div>
  <div class="flex flex-wrap items-center gap-2 mb-2">
    <SelectionToggleButton :selected="true">{{ t('adminProblems.stateOpen') }}</SelectionToggleButton>
    <SelectionToggleButton :selected="false">{{ t('adminProblems.stateResolved') }}</SelectionToggleButton>
    <SelectionToggleButton :selected="false">{{ t('adminProblems.stateMuted') }}</SelectionToggleButton>
    <SelectionToggleButton :selected="false">{{ t('adminProblems.stateAll') }}</SelectionToggleButton>
  </div>
  <div class="flex flex-wrap items-center gap-2 mb-2">
    <SelectionToggleButton :selected="false">{{ t('adminProblems.errors') }}</SelectionToggleButton>
    <SelectionToggleButton :selected="false">{{ t('adminProblems.warnings') }}</SelectionToggleButton>
  </div>
  <div class="mb-4">
    <SecondaryButton :icon="['fas', 'check-double']">{{ t('adminProblems.resolveAll') }}</SecondaryButton>
  </div>
  <ProblemLevelSummary :empty-label="t('adminProblems.noProblems')" :error-count="2" :warn-count="1"/>
</template>
