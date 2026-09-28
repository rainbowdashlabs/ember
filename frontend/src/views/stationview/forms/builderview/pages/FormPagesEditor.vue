/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PageSection from './PageSection.vue'
import type { FormLayoutEditor } from '../useFormLayout'
import type { QuestionType } from '@/api/forms'

/**
 * The pages of the form in the editor, one section each, with a button between them that puts a new
 * page there. Pages are sections of the one editor rather than screens of their own, so the whole
 * form stays in view while it is split up.
 */
defineProps<{
  layout: FormLayoutEditor
  questionTypes: QuestionType[]
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-6">
    <template v-for="(page, index) in layout.pages.value" :key="page.key">
      <PageSection :layout="layout" :page-index="index" :question-types="questionTypes"/>
      <div class="flex justify-center border-t border-dashed border-(--border) pt-3">
        <SecondaryButton :icon="['fas', 'plus']" compact data-testid="add-page" @click="layout.addPage(index)">
          {{ t('forms.pages.add') }}
        </SecondaryButton>
      </div>
    </template>
  </div>
</template>
