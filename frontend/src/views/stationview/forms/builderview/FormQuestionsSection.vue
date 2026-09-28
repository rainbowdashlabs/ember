/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FormPagesEditor from './pages/FormPagesEditor.vue'
import FormPreview from './preview/FormPreview.vue'
import type { FormLayoutEditor } from './useFormLayout'
import type { QuestionType } from '@/api/forms'

/**
 * The pages and questions of the form, either being edited or, with the switch on, shown the way a
 * reader will fill them in.
 */
defineProps<{
  layout: FormLayoutEditor
  questionTypes: QuestionType[]
}>()

const { t } = useI18n()

const previewing = ref(false)
</script>

<template>
  <div class="space-y-4">
    <FieldLabel inline>
      <ToggleInput v-model="previewing" data-testid="form-preview-toggle"/>
      {{ t('forms.preview.toggle') }}
    </FieldLabel>
    <FormPreview v-if="previewing" :layout="layout"/>
    <FormPagesEditor v-else :layout="layout" :question-types="questionTypes"/>
  </div>
</template>
