/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import type {MovementFlowStep} from '@/api/movements'

const {t} = useI18n()

defineProps<{
  step: MovementFlowStep
  editable: boolean
}>()

defineEmits<{
  archive: []
}>()
</script>

<template>
  <div class="flex items-start justify-between gap-2 py-1" :class="step.archived ? 'opacity-50' : ''">
    <div class="text-sm">
      <div class="flex items-center gap-2 flex-wrap">
        <span class="text-(--text-muted)">{{ step.position + 1 }}.</span>
        <span class="font-medium">{{ step.label }}</span>
        <SecondaryBadge>{{ t(`movements.actor.${step.actor}`) }}</SecondaryBadge>
        <InfoBadge v-if="step.picksItem">{{ t('flows.picksItem') }}</InfoBadge>
        <SecondaryBadge v-if="step.archived">{{ t('flows.archived') }}</SecondaryBadge>
      </div>
      <div class="text-xs text-(--text-muted)">
        {{ t(`movements.subject.${step.subject}`) }} · {{ t(`itemDetail.custodyValues.${step.custodyAfter}`) }}
      </div>
    </div>
    <MutedIconButton
        v-if="editable && !step.archived"
        :icon="['fas', 'xmark']"
        :label="t('flows.archiveStep')"
        hover="error"
        @click="$emit('archive')"
    />
  </div>
</template>
