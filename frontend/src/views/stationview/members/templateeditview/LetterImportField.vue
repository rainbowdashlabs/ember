/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import FileInput from '@/components/input/FileInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {documentTemplates} from '@/api'
import type {LetterImport} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * Fills the body from a Word (.docx) or OpenDocument (.odt) text and says which gaps became
 * placeholders and which stayed text, so the rest can be replaced by hand. The letterhead is not part
 * of the import; it is set up once in the letterhead tab.
 */
const emit = defineEmits<{
  imported: [body: string]
}>()

const {t} = useI18n()
const result = ref<LetterImport | null>(null)

const importing = useAsyncAction(async (file: File) => {
  result.value = await documentTemplates.importLetter(file)
  emit('imported', result.value.bodyMarkdown)
})
</script>

<template>
  <div class="space-y-2">
    <FileInput accept=".docx,.odt" :label="t('documentTemplates.importLetter')" data-testid="template-import"
               @select="file => importing.run(file)"/>
    <FailureAlert :failure="importing.failure.value"/>
    <Alert v-if="result" variant="info">
      {{ t('documentTemplates.imported', {recognised: result.recognised.length}) }}
      <template v-if="result.unrecognised.length > 0">
        {{ t('documentTemplates.importedUnrecognised', {gaps: result.unrecognised.join(', ')}) }}
      </template>
    </Alert>
  </div>
</template>
