/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import type {Placeholder} from '@/api/generated/schema'
import LetterRowEditor from './LetterRowEditor.vue'
import PageSettings from './PageSettings.vue'
import type {TemplateDraft} from './templateDraft'

/**
 * The letterhead every page carries, a header and a footer of up to three cells each, and the page it
 * is set on. The header and the footer sit inside the top and bottom margins, so those margins leave
 * room for them.
 */
const draft = defineModel<TemplateDraft>({required: true})

defineProps<{
  placeholders: Placeholder[]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-6">
    <NeutralContainer>
      <LetterRowEditor v-model="draft.letterhead.header" :title="t('documentTemplates.header')" :placeholders="placeholders"
                       :legal="draft.legal" data-testid="letter-header"/>
    </NeutralContainer>
    <NeutralContainer>
      <LetterRowEditor v-model="draft.letterhead.footer" :title="t('documentTemplates.footer')" :placeholders="placeholders"
                       :legal="draft.legal" data-testid="letter-footer"/>
    </NeutralContainer>
    <PageSettings v-model="draft.page"/>
  </div>
</template>
