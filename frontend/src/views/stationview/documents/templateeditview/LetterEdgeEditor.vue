/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ContentBlockEditor from '@/components/content/ContentBlockEditor.vue'
import ContentBlocks from '@/components/content/ContentBlocks.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {ContentRow} from '@/api/generated/schema'
import type {RowEditData} from '@/components/content/blockeditor/EditorRow.vue'
import {provideBlockEditorOptions} from '@/composables/useBlockEditorOptions'
import {internalContentContext} from '@/util/contentContext'
import {letterBlockOptions, type LetterCatalogue} from './letterBlockOptions'

/**
 * The header or the footer of a letter, shown as it prints until it is clicked, then edited with the
 * block editor in place. Its texts take placeholders but no signature field, which has no place on
 * every page.
 */
const rows = defineModel<RowEditData[]>({required: true})

const props = defineProps<{
  title: string
  stationUid: string
  catalogue: LetterCatalogue
}>()

const {t} = useI18n()
const editing = ref(false)

provideBlockEditorOptions(() => letterBlockOptions(props.catalogue, false))

const context = computed(() => internalContentContext(props.stationUid, props.title))
const rendered = computed(() => rows.value as unknown as ContentRow[])
</script>

<template>
  <div class="space-y-2" :data-testid="`letter-edge-${editing ? 'editing' : 'shown'}`">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <SubHeader>{{ title }}</SubHeader>
      <SecondaryButton v-if="editing" :icon="['fas', 'check']" data-testid="letter-edge-done" @click="editing = false">
        {{ t('documentTemplates.edgeDone') }}
      </SecondaryButton>
    </div>
    <ContentBlockEditor v-if="editing" v-model:rows="rows" :station-uid="stationUid"/>
    <div v-else role="button" tabindex="0" data-testid="letter-edge-open"
         class="cursor-pointer rounded-theme border border-dashed border-(--border) p-3 hover:border-primary"
         :title="t('documentTemplates.edgeEdit', {part: title})"
         @click="editing = true"
         @keydown.enter.self.prevent="editing = true"
         @keydown.space.self.prevent="editing = true">
      <ContentBlocks v-if="rows.length > 0" :rows="rendered" :context="context"/>
      <MutedText v-else size="sm" tag="p">{{ t('documentTemplates.edgeEmpty') }}</MutedText>
    </div>
  </div>
</template>
