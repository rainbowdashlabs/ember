/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {FontFamilyOption, LetterPage} from '@/api/generated/schema'
import LinkButton from '@/components/button/LinkButton.vue'
import PageFonts from './PageFonts.vue'
import {DEFAULT_PAGE} from './templateDraft'

/**
 * The margins of the A4 page in millimetres, the size of the body text in points, and the fonts. A text
 * size changed away from the one a new template starts with can be set back to it in one press.
 */
const page = defineModel<LetterPage>({required: true})

defineProps<{
  fonts: readonly FontFamilyOption[]
  defaultFamily: string
}>()

const {t} = useI18n()

type Measure = 'marginTopMm' | 'marginBottomMm' | 'marginLeftMm' | 'marginRightMm' | 'fontSizePt'

const fields: readonly {key: Measure, label: string}[] = [
  {key: 'marginTopMm', label: 'documentTemplates.marginTop'},
  {key: 'marginBottomMm', label: 'documentTemplates.marginBottom'},
  {key: 'marginLeftMm', label: 'documentTemplates.marginLeft'},
  {key: 'marginRightMm', label: 'documentTemplates.marginRight'},
  {key: 'fontSizePt', label: 'documentTemplates.fontSize'},
]

function set(key: Measure, value: number | undefined) {
  page.value = {...page.value, [key]: value ?? page.value[key]}
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('documentTemplates.pageTitle') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('documentTemplates.pageHint') }}</MutedText>
    <div class="grid gap-3 sm:grid-cols-3 lg:grid-cols-5">
      <LabelledField v-for="field in fields" :key="field.key" :label="t(field.label)">
        <NumberInput :model-value="page[field.key]" @update:model-value="value => set(field.key, value)"/>
        <LinkButton v-if="field.key === 'fontSizePt' && page.fontSizePt !== DEFAULT_PAGE.fontSizePt"
                    class="text-xs mt-1" data-testid="page-font-size-reset"
                    @click="set('fontSizePt', DEFAULT_PAGE.fontSizePt)">
          {{ t('documentTemplates.fontSizeReset', {size: DEFAULT_PAGE.fontSizePt}) }}
        </LinkButton>
      </LabelledField>
    </div>
    <PageFonts v-model="page" :fonts="fonts" :default-family="defaultFamily"/>
  </NeutralContainer>
</template>
