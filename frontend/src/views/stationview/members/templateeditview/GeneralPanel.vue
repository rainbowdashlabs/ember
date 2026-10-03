/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TagPicker from '@/components/input/TagPicker.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {DocumentLanguage, type Placeholder} from '@/api/generated/schema'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {TemplateDraft} from './templateDraft'
import PlaceholderTextInput from './placeholderpicker/PlaceholderTextInput.vue'

/**
 * What a template is called and how the documents it makes are filed: under which title and file
 * name, both of which take placeholders, with which tags, whether they outlast the membership and whether they are hidden from the
 * member. A legal template uses official names only and keeps its documents by default. The language
 * picks the pronouns of the gender field and the words the document prints, the station's by default.
 */
const draft = defineModel<TemplateDraft>({required: true})

defineProps<{
  /** The document tags the station uses already, offered while typing. */
  documentTags: string[]
  /** What the title and the file name can name. */
  placeholders: Placeholder[]
}>()

const {t} = useI18n()

const LANGUAGES: readonly DocumentLanguage[] = [DocumentLanguage.DE, DocumentLanguage.EN]

function setLegal(legal: boolean) {
  draft.value.legal = legal
  if (legal) draft.value.keepOnArchive = true
}

function setLanguage(language: string | number | null | undefined) {
  draft.value.language = LANGUAGES.find(candidate => candidate === language) ?? null
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('documentTemplates.generalTitle') }}</SubHeader>
    <LabelledField :label="t('documentTemplates.name')">
      <TextInput v-model="draft.name" data-testid="template-name" :placeholder="t('documentTemplates.namePlaceholder')"/>
    </LabelledField>
    <LabelledField :label="t('documentTemplates.titlePattern')" :help="t('documentTemplates.titlePatternHelp')" hint>
      <PlaceholderTextInput v-model="draft.titlePattern" :placeholders="placeholders" :legal="draft.legal"
                            :prompt="t('documentTemplates.titlePatternPlaceholder')" data-testid="template-title-pattern"/>
    </LabelledField>
    <LabelledField :label="t('documentTemplates.fileNamePattern')" :help="t('documentTemplates.fileNamePatternHelp')" hint>
      <PlaceholderTextInput v-model="draft.fileNamePattern" :placeholders="placeholders" :legal="draft.legal"
                            :prompt="t('documentTemplates.fileNamePatternPlaceholder')" data-testid="template-file-name-pattern"/>
    </LabelledField>
    <LabelledField :label="t('documentTemplates.language')" :help="t('documentTemplates.languageHelp')" hint>
      <SelectInput :model-value="draft.language ?? ''" data-testid="template-language" @update:model-value="setLanguage">
        <option value="">{{ t('documentTemplates.languageStation') }}</option>
        <option v-for="language in LANGUAGES" :key="language" :value="language">
          {{ t(`documentTemplates.languageName.${language}`) }}
        </option>
      </SelectInput>
    </LabelledField>
    <LabelledField :label="t('documents.tags')" hint>
      <TagPicker v-model="draft.tags" :suggestions="documentTags" :placeholder="t('documents.tagsPlaceholder')"/>
    </LabelledField>
    <ToggleSetting :model-value="draft.legal" :label="t('documentTemplates.legal')" :hint="t('documentTemplates.legalHint')"
                   data-testid="template-legal" @update:model-value="setLegal"/>
    <ToggleSetting v-model="draft.keepOnArchive" :label="t('documents.keepOnArchive')" :hint="t('documents.keepOnArchiveHint')"/>
    <ToggleSetting v-model="draft.hidden" :label="t('documents.hide')" :hint="t('documentTemplates.hiddenHint')"/>
  </NeutralContainer>
</template>
