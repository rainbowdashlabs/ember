/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import GeneratedPreview from '@/components/documents/GeneratedPreview.vue'
import {fromMember} from '@/components/input/select/memberOption'
import type {TemplateSource} from '@/api/documentTemplates'
import type {DocumentTemplateResponse, MemberWithName, PreviewResponse} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {requestOf, type TemplateDraft} from './templateDraft'

/**
 * The document as it will print: drawn from the editor's draft, for a member or, without one, with
 * every placeholder shown by its name. A PDF template is filled from the PDF of the saved template.
 * Nothing is filed from here; documents are generated in the document store and on a member's page.
 * An association has no members of its own, so its template is always drawn without one.
 */
const props = defineProps<{
  draft: TemplateDraft
  saved: DocumentTemplateResponse | null
  members: MemberWithName[]
  source: TemplateSource
  /** Whether the owner has members to draw the template for. */
  hasMembers: boolean
}>()

const {t} = useI18n()

const memberId = ref('')
const preview = ref<PreviewResponse | null>(null)
const memberOptions = computed(() => props.members.map(fromMember))

const drawing = useAsyncAction(async () => {
  preview.value = await props.source.previewDraft(
      requestOf(props.draft), props.saved?.id ?? null, memberId.value ? Number(memberId.value) : null)
})
</script>

<template>
  <NeutralContainer class="space-y-4">
    <LabelledField v-if="hasMembers" :label="t('documentTemplates.previewMember')" :help="t('documentTemplates.previewMemberHelp')" hint>
      <MemberSelectInput v-model="memberId" :members="memberOptions" clearable data-testid="preview-member"
                         :placeholder="t('documentTemplates.previewWithoutMember')"/>
    </LabelledField>
    <MutedText v-else size="sm" tag="p">{{ t('documentTemplates.previewOfAssociation') }}</MutedText>
    <SecondaryButton :icon="['fas', 'eye']" :disabled="drawing.running.value" data-testid="template-preview" @click="drawing.run()">
      {{ t('documentTemplates.showPreview') }}
    </SecondaryButton>
    <FailureAlert :failure="drawing.failure.value"/>
    <GeneratedPreview v-if="preview" :preview="preview" :title="draft.name || t('documentTemplates.preview')"/>
  </NeutralContainer>
</template>
