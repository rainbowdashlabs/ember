/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ContentBlockEditor from '@/components/content/ContentBlockEditor.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {ContentRow, FontFamilyOption} from '@/api/generated/schema'
import {provideBlockEditorOptions} from '@/composables/useBlockEditorOptions'
import {useSession} from '@/composables/useSession'
import {toEditRows} from '@/util/blockSwitch'
import LetterEdgeEditor from './LetterEdgeEditor.vue'
import LetterImportField from './LetterImportField.vue'
import PageSettings from './PageSettings.vue'
import {letterBlockOptions, type LetterCatalogue} from './letterBlockOptions'
import type {TemplateDraft} from './templateDraft'

/**
 * The letter as it is laid out on the page: the header, the body and the footer, each rows of up to
 * three columns of texts and pictures, written with the block editor pages use. A block can be meant
 * for some members only and is left out for the others. The header and the footer show as they print
 * until they are clicked. A Word or OpenDocument text fills the body.
 */
const draft = defineModel<TemplateDraft>({required: true})

const props = defineProps<{
  catalogue: Omit<LetterCatalogue, 'legal'>
  fonts: readonly FontFamilyOption[]
}>()

const {t} = useI18n()
const {sessionInfo} = useSession()
const stationUid = computed(() => sessionInfo.value?.stationId ?? '')
const catalogue = computed<LetterCatalogue>(() => ({...props.catalogue, legal: draft.value.legal}))

provideBlockEditorOptions(() => letterBlockOptions(catalogue.value, true))

function imported(rows: ContentRow[]) {
  draft.value.body = toEditRows(rows)
}
</script>

<template>
  <div class="space-y-6">
    <NeutralContainer>
      <LetterEdgeEditor v-model="draft.header" :title="t('documentTemplates.header')" :station-uid="stationUid"
                        :catalogue="catalogue" data-testid="letter-header"/>
    </NeutralContainer>
    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('documentTemplates.bodyTitle') }}</SubHeader>
      <MutedText size="sm" tag="p">{{ t('documentTemplates.bodyHint') }}</MutedText>
      <LetterImportField @imported="imported"/>
      <div data-testid="letter-body">
        <ContentBlockEditor v-model:rows="draft.body" :station-uid="stationUid"/>
      </div>
    </NeutralContainer>
    <NeutralContainer>
      <LetterEdgeEditor v-model="draft.footer" :title="t('documentTemplates.footer')" :station-uid="stationUid"
                        :catalogue="catalogue" data-testid="letter-footer"/>
    </NeutralContainer>
    <PageSettings v-model="draft.page" :fonts="fonts"/>
  </div>
</template>
