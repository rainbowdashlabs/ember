/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import IconButton from '@/components/button/IconButton.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps<{
  index: number
  text: string
  type: string
  points: string
}>()
</script>

<template>
  <NeutralContainer>
    <div class="flex items-start gap-3">
      <span class="text-xs text-(--text-muted) w-6 shrink-0 pt-0.5">{{ index }}.</span>
      <div class="flex-1">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="text-sm font-medium">{{ text }}</span>
          <SecondaryBadge>{{ type }}</SecondaryBadge>
          <InfoBadge>{{ points }}</InfoBadge>
        </div>
      </div>
      <div class="flex gap-1 shrink-0">
        <IconButton :icon="['fas', 'shuffle']" :label="t('helpCenter.quizTestDetailFrozenQuestion.shuffleLabel')" class="text-(--text-muted)" />
        <IconButton :icon="['fas', 'arrow-right-arrow-left']" :label="t('helpCenter.quizTestDetailFrozenQuestion.pickLabel')" class="text-(--text-muted)" />
      </div>
    </div>
  </NeutralContainer>
</template>
