/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FileThumbnail from '@/components/documents/FileThumbnail.vue'
import TemplateBadges from '@/components/documents/templatepicker/TemplateBadges.vue'
import TemplatePickerModal from '@/components/documents/templatepicker/TemplatePickerModal.vue'
import {stationTemplateSource, usableTemplates, type TemplateListQuery} from '@/api/documentTemplates'
import type {DocumentTemplateSummary} from '@/api/generated/schema'

/**
 * The template a document is generated from: the one chosen, with its picture and badges, and a button
 * opening the template picker over the templates a manager can generate from.
 */
const template = defineModel<DocumentTemplateSummary | null>({required: true})

defineProps<{
  /** What the picker asks for besides the reader's choice, such as leaving out templates for appointments. */
  fixed?: TemplateListQuery
}>()

const CHOSEN_PICTURE_SIZE = 256

const {t} = useI18n()
const picking = ref(false)
</script>

<template>
  <LabelledField :label="t('documentTemplates.template')">
    <div class="flex items-center gap-3" data-testid="template-choice">
      <template v-if="template">
        <FileThumbnail :url="stationTemplateSource.pictureUrl(template.id, CHOSEN_PICTURE_SIZE)" mime-type="application/pdf"
                       :alt="template.name" size="h-20 w-16" anchor-top/>
        <div class="min-w-0 flex-1 space-y-1">
          <div class="truncate font-medium" data-testid="template-choice-name">{{ template.name }}</div>
          <TemplateBadges :template="template"/>
        </div>
      </template>
      <SecondaryButton :icon="['fas', 'file-lines']" data-testid="template-choice-open" @click="picking = true">
        {{ template ? t('documentTemplates.browse.chooseOther') : t('documentTemplates.chooseTemplate') }}
      </SecondaryButton>
    </div>
    <TemplatePickerModal v-model="picking" :pages="usableTemplates" :fixed="fixed"
                         :chosen-ids="template ? [template.id] : []" @pick="picked => template = picked[0] ?? template"/>
  </LabelledField>
</template>
