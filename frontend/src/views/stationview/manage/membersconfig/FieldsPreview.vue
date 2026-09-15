/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, toRef} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PreviewTile from './previewtile/PreviewTile.vue'
import {spanForWidth, type FieldWidthName} from '@/components/profilefields/fieldLayout'
import {usePreviewLayout} from './usePreviewLayout'
import type {AskedField} from './askedField'

/**
 * One audience's form, arranged where it is read.
 *
 * <p>This was a picture beside a list: the list was dragged and the picture showed what came of it,
 * which is two places to look for one thing. The form is arranged here instead, on the shape it will
 * actually have, so making a question half a row wide is done by making it half a row wide.
 *
 * <p>Both of them belong to the audience being shown. The same question can be first and full width
 * on one form and last and narrow on another, which is the whole reason a form is not the question.
 */
const props = defineProps<{ fields: AskedField[] }>()

const emit = defineEmits<{
  reorder: [fromIndex: number, toIndex: number]
  resize: [field: AskedField, width: FieldWidthName]
}>()

const {t} = useI18n()

const grid = ref<HTMLElement | null>(null)

const {shown, startMove, startResize, widthOf, movingId, resizingId} = usePreviewLayout(
    toRef(props, 'fields'),
    (from, to) => emit('reorder', from, to),
    (field, width) => emit('resize', field, width),
)
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader class="text-sm">{{ t('membersConfig.previewTitle') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ t('membersConfig.previewHint') }}</MutedText>

    <EmptyState v-if="props.fields.length === 0" compact>{{ t('membersConfig.orderEmpty') }}</EmptyState>

    <div v-else ref="grid" class="grid grid-cols-6 gap-x-4 gap-y-3 items-start">
      <PreviewTile
          v-for="(field, index) in shown()"
          :key="field.id"
          :class="spanForWidth(widthOf(field))"
          :field="field"
          :index="index"
          :width="widthOf(field)"
          :moving="movingId === field.id"
          :resizing="resizingId === field.id"
          @move-start="(f, event) => startMove(f, event)"
          @resize-start="(f, event) => startResize(f, event, grid)"/>
    </div>
  </NeutralContainer>
</template>
