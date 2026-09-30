/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import { VueDraggable } from 'vue-draggable-plus'
import PageSection from './PageSection.vue'
import { keepTargetsForward, type FormLayoutEditor } from '../useFormLayout'
import type { QuestionType } from '@/api/forms'

/**
 * The pages of the form in the editor, one section each, with a button between them that puts a new
 * page there. Pages are sections of the one editor rather than screens of their own, so the whole
 * form stays in view while it is split up.
 *
 * <p>Pages are dragged as a whole by the grip in their header, and questions by theirs, within a page
 * and from one page to another. The up and down entries in the menus stay, because dragging is no
 * use from a keyboard. A page dragged below a page it led to stops leading there.
 */
defineProps<{
  layout: FormLayoutEditor
  questionTypes: QuestionType[]
}>()

const { t } = useI18n()
</script>

<template>
  <VueDraggable :model-value="layout.pages.value" handle="[data-page-grip]" :animation="150" class="space-y-6"
                @update:model-value="layout.reorderPages" @end="keepTargetsForward(layout.pages.value)">
    <div v-for="(page, index) in layout.pages.value" :key="page.key" class="space-y-6">
      <PageSection :layout="layout" :page-index="index" :question-types="questionTypes"/>
      <div class="flex justify-center border-t border-dashed border-(--border) pt-3">
        <SecondaryButton :icon="['fas', 'plus']" compact data-testid="add-page" @click="layout.addPage(index)">
          {{ t('forms.pages.add') }}
        </SecondaryButton>
      </div>
    </div>
  </VueDraggable>
</template>
