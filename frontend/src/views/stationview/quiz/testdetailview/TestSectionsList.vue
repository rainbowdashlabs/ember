/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type { QuizTestDetail } from '@/api/types'

defineProps<{
  sections: QuizTestDetail['sections']
  catalogName: (catalogId: number) => string
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-3">
    <SubHeader>{{ t('quiz.sections.title') }} ({{ sections.length }})</SubHeader>
    <NeutralContainer v-for="(section, idx) in sections" :key="section.id">
      <div class="space-y-2">
        <div class="flex items-center gap-2">
          <span class="text-xs font-semibold text-(--text-muted)">{{ idx + 1 }}.</span>
          <span class="font-medium">{{ section.title || t('quiz.sections.untitled') }}</span>
        </div>
        <p v-if="section.description" class="text-xs text-(--text-muted)">{{ section.description }}</p>
        <div class="flex flex-wrap gap-2">
          <InfoBadge v-for="source in section.sources" :key="source.id">
            {{ catalogName(source.catalogId) }}
            <template v-if="source.categoryId"> / {{ source.categoryId }}</template>
            ({{ source.questionCount }})
          </InfoBadge>
        </div>
      </div>
    </NeutralContainer>
  </div>
</template>
