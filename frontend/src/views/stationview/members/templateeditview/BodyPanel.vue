/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {placeholderContent, placeholderTokens} from '@/components/input/markdowneditor/placeholderChip'
import type {Placeholder} from '@/api/generated/schema'
import PlaceholderPicker from './PlaceholderPicker.vue'
import LetterImportField from './LetterImportField.vue'
import type {TemplateDraft} from './templateDraft'

/**
 * The body of the letter, written in the editor every other text is written in, with its placeholders
 * as chips: a chip shows what it stands for and is stored as `{{key}}`. A Word or OpenDocument text can
 * fill the body, its gaps in brackets turned into chips where their words are known.
 */
const draft = defineModel<TemplateDraft>({required: true})

const props = defineProps<{
  placeholders: Placeholder[]
  labels: ReadonlyMap<string, string>
}>()

const {t} = useI18n()

const editor = ref<InstanceType<typeof MarkdownEditor> | null>(null)
const tokens = placeholderTokens(props.labels)

function insert(placeholder: Placeholder) {
  editor.value?.insert(placeholderContent(placeholder.key, placeholder.label))
}

function imported(body: string) {
  draft.value.bodyMarkdown = body
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('documentTemplates.bodyTitle') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('documentTemplates.bodyHint') }}</MutedText>
    <div class="grid gap-3 sm:grid-cols-2">
      <PlaceholderPicker :placeholders="placeholders" :legal="draft.legal" signatures @pick="insert"/>
      <LetterImportField @imported="imported"/>
    </div>
    <MarkdownEditor ref="editor" v-model="draft.bodyMarkdown" :tokens="tokens" data-testid="template-body"
                    :placeholder="t('documentTemplates.bodyPlaceholder')"/>
  </NeutralContainer>
</template>
