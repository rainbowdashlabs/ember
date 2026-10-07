/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup generic="T extends PreviewField">
import {ref, toRef} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldPreviewTile from './FieldPreviewTile.vue'
import {spanForWidth, type FieldWidthName, type PreviewField} from './fieldLayout'
import {usePreviewLayout} from './usePreviewLayout'

/**
 * A form's questions as they will be laid out, arranged where they are read.
 *
 * <p>This was a picture beside a list: the list was dragged and the picture showed what came of it,
 * which is two places to look for one thing. The form is arranged here instead, on the shape it will
 * actually have, so making a question half a row wide is done by making it half a row wide. A question
 * is dragged by its grip to another place and by the bar on its right to another width; both are handed
 * on as events for the owner of the form to keep.
 *
 * <p>Where a form is arranged somewhere else, it is drawn without the handles. The hint is the owner's,
 * because what a change affects differs: one audience's form, or a whole appointment's.
 */
const props = withDefaults(defineProps<{
  fields: readonly T[]
  hint: string
  editable?: boolean
  /** Shown in place of the grid while there are no questions; without one the preview is left out. */
  emptyText?: string
}>(), {
  editable: true,
  emptyText: undefined,
})

const emit = defineEmits<{
  reorder: [fromIndex: number, toIndex: number]
  resize: [field: T, width: FieldWidthName]
}>()

const {t} = useI18n()

const grid = ref<HTMLElement | null>(null)

const {shown, startMove, startResize, widthOf, moving, resizing} = usePreviewLayout(
    toRef(props, 'fields'),
    (from, to) => emit('reorder', from, to),
    (field, width) => emit('resize', field, width),
)
</script>

<template>
  <NeutralContainer v-if="fields.length > 0 || emptyText" class="space-y-3" data-testid="field-layout-preview">
    <SubHeader class="text-sm">{{ t('membersConfig.previewTitle') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ hint }}</MutedText>

    <EmptyState v-if="fields.length === 0" compact>{{ emptyText }}</EmptyState>

    <div v-else ref="grid" class="grid grid-cols-6 gap-x-4 gap-y-3 items-start">
      <FieldPreviewTile
          v-for="(field, index) in shown()"
          :key="index"
          :class="spanForWidth(widthOf(field))"
          :field="field"
          :index="index"
          :width="widthOf(field)"
          :editable="editable"
          :moving="moving === field"
          :resizing="resizing === field"
          @move-start="f => startMove(f)"
          @resize-start="(f, event) => startResize(f, event, grid)"/>
    </div>
  </NeutralContainer>
</template>
