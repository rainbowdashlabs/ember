/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {spanForWidth} from './fieldLayout'

/** One box of the preview: what it is called and how much of a row it takes. */
export interface PreviewField {
  name?: string
  width?: unknown
}

/**
 * The questions as they will be laid out, beside the list that lays them out.
 *
 * <p>Setting a width is guesswork while the only thing on the screen is a column of rows: half of what
 * and beside which? Here it is drawn, so the answer is looked at rather than imagined and then found
 * out on the form itself.
 */
defineProps<{
  fields: PreviewField[]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer v-if="fields.length > 0" class="space-y-2" data-testid="field-layout-preview">
    <SubHeader class="text-sm">{{ t('membersConfig.previewTitle') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('membersConfig.previewHint') }}</MutedText>
    <div class="grid grid-cols-6 gap-2">
      <div
          v-for="(field, index) in fields"
          :key="index"
          class="truncate rounded-md border border-(--border) bg-(--bg-accent) px-2 py-3 text-xs"
          data-testid="preview-box"
          :class="spanForWidth(field.width)"
      >
        {{ field.name || t('membersConfig.previewUnnamed') }}
      </div>
    </div>
  </NeutralContainer>
</template>
