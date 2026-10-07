/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup generic="T extends PreviewField">
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {isSection, isSpacer, type FieldWidthName, type PreviewField} from './fieldLayout'
import {widthLabel} from './fieldWidths'
import {fieldTypeLabel} from '@/api/fieldTypes'

/**
 * One question as it stands on the form, and as it is arranged.
 *
 * <p>Two handles, and each says what it does: the grip on the left picks the question up, the bar on
 * the right drags its width. Both are drawn at rest rather than appearing on hover, because a handle
 * nobody can see is a feature nobody finds.
 *
 * <p>A heading takes the whole row whatever else happens, so it is moved and never resized. A spacer
 * is drawn here and nowhere else: on the form it is the gap it makes, and arranging a gap you cannot
 * see is not something anybody can do.
 *
 * <p>Where the form is arranged somewhere else, the tile shows the question without either handle.
 *
 * @param width    what it is drawn at right now, which is the drag in progress where there is one
 * @param moving   whether this is the tile being carried
 * @param editable whether it can be moved and resized here
 */
const props = withDefaults(defineProps<{
  field: T
  index: number
  width: FieldWidthName
  moving: boolean
  resizing: boolean
  editable?: boolean
}>(), {
  editable: true,
})

const emit = defineEmits<{
  moveStart: [field: T, event: PointerEvent]
  resizeStart: [field: T, event: PointerEvent]
}>()

const {t} = useI18n()
</script>

<template>
  <div
      :data-preview-index="index"
      :data-testid="`preview-tile-${field.name}`"
      :class="[
        moving ? 'opacity-50 ring-2 ring-primary' : 'hover:border-primary/60',
        resizing ? 'ring-2 ring-primary' : '',
        props.editable ? 'touch-none cursor-grab active:cursor-grabbing' : '',
      ]"
      class="group relative flex select-none items-stretch gap-2 overflow-hidden rounded-theme border
             border-bg-light-accent dark:border-bg-dark-accent bg-(--bg)"
      @pointerdown="props.editable && emit('moveStart', field, $event)">
    <div
        v-if="props.editable"
        :title="t('membersConfig.preview.moveHint')"
        :data-testid="`preview-move-${field.name}`"
        class="flex w-6 shrink-0 items-center justify-center bg-bg-light-accent/60 dark:bg-bg-dark-accent/60
               text-(--text-muted) group-hover:bg-primary group-hover:text-white">
      <font-awesome-icon :icon="['fas', 'grip-vertical']" class="h-3 w-3"/>
    </div>

    <div class="min-w-0 flex-1 py-2 pr-1" :class="props.editable ? '' : 'pl-2'">
      <SubHeader v-if="isSection(field)" class="text-sm">{{ field.name }}</SubHeader>
      <MutedText v-else-if="isSpacer(field)" class="block text-xs italic">
        {{ t('membersConfig.preview.spacer', {width: widthLabel(t, width)}) }}
      </MutedText>
      <template v-else>
        <FieldLabel>
          {{ field.name || t('membersConfig.previewUnnamed') }}
          <span v-if="field.required" class="text-error">*</span>
        </FieldLabel>
        <MutedText class="block text-xs">
          {{ fieldTypeLabel(t, field.fieldType ?? '') }} · {{ widthLabel(t, width) }}
        </MutedText>
      </template>
    </div>

    <div
        v-if="props.editable && !isSection(field)"
        :title="t('membersConfig.preview.resizeHint')"
        :data-testid="`preview-resize-${field.name}`"
        class="flex w-5 shrink-0 items-center justify-center bg-bg-light-accent/60 dark:bg-bg-dark-accent/60
               text-(--text-muted) hover:bg-primary hover:text-white cursor-ew-resize"
        @pointerdown="emit('resizeStart', field, $event)">
      <font-awesome-icon :icon="['fas', 'grip-lines']" class="h-3 w-3 rotate-90"/>
    </div>
  </div>
</template>
