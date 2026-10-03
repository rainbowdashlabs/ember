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
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {TemplateDraft} from './templateDraft'

/**
 * What a template is called and how the documents it makes are filed: under which title and file
 * name, with which tags, whether they outlast the membership and whether they are hidden from the
 * member. A legal template uses official names only and keeps its documents by default.
 */
const draft = defineModel<TemplateDraft>({required: true})

defineProps<{
  /** The document tags the station uses already, offered while typing. */
  documentTags: string[]
}>()

const {t} = useI18n()

function setLegal(legal: boolean) {
  draft.value.legal = legal
  if (legal) draft.value.keepOnArchive = true
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('documentTemplates.generalTitle') }}</SubHeader>
    <LabelledField :label="t('documentTemplates.name')">
      <TextInput v-model="draft.name" data-testid="template-name" :placeholder="t('documentTemplates.namePlaceholder')"/>
    </LabelledField>
    <LabelledField :label="t('documentTemplates.titlePattern')" :help="t('documentTemplates.titlePatternHelp')" hint>
      <TextInput v-model="draft.titlePattern" :placeholder="t('documentTemplates.titlePatternPlaceholder')"/>
    </LabelledField>
    <LabelledField :label="t('documentTemplates.fileNamePattern')" :help="t('documentTemplates.fileNamePatternHelp')" hint>
      <TextInput v-model="draft.fileNamePattern" :placeholder="t('documentTemplates.fileNamePatternPlaceholder')"/>
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
