/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import GeneratedPreview from '@/components/documents/GeneratedPreview.vue'
import {fromMember} from '@/components/input/select/memberOption'
import {documentTemplates} from '@/api'
import type {TemplateSource} from '@/api/documentTemplates'
import {StationPermission, type DocumentTemplateResponse, type MemberWithName, type PreviewResponse} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {usePermissions} from '@/composables/usePermissions'
import {showToast} from '@/util/toast'
import {requestOf, type TemplateDraft} from './templateDraft'

/**
 * The document as it will print: drawn from the editor's draft, for a member or, without one, with
 * every placeholder shown by its name. A PDF template is filled from the PDF of the saved template.
 * Whoever may file documents for members can generate the saved template for the chosen member from
 * here. An association has no members of its own, so its template is always drawn without one.
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
const {hasPermission} = usePermissions()

const memberId = ref('')
const preview = ref<PreviewResponse | null>(null)
const memberOptions = computed(() => props.members.map(fromMember))
const canGenerate = computed(() =>
    props.hasMembers && !!props.saved && !props.saved.archivedAt && !!memberId.value
    && hasPermission(StationPermission.DOCUMENT_EDIT_MEMBER))

const drawing = useAsyncAction(async () => {
  preview.value = await props.source.previewDraft(
      requestOf(props.draft), props.saved?.id ?? null, memberId.value ? Number(memberId.value) : null)
})

const generating = useAsyncAction(async () => {
  if (!props.saved || !memberId.value) return
  await documentTemplates.generateForMember(props.saved.id, Number(memberId.value))
  showToast(t('documentTemplates.generated'), 'success')
})
</script>

<template>
  <NeutralContainer class="space-y-4">
    <LabelledField v-if="hasMembers" :label="t('documentTemplates.previewMember')" :help="t('documentTemplates.previewMemberHelp')" hint>
      <MemberSelectInput v-model="memberId" :members="memberOptions" clearable data-testid="preview-member"
                         :placeholder="t('documentTemplates.previewWithoutMember')"/>
    </LabelledField>
    <MutedText v-else size="sm" tag="p">{{ t('documentTemplates.previewOfAssociation') }}</MutedText>
    <ButtonRow>
      <SecondaryButton :icon="['fas', 'eye']" :disabled="drawing.running.value" data-testid="template-preview" @click="drawing.run()">
        {{ t('documentTemplates.showPreview') }}
      </SecondaryButton>
      <PrimaryButton v-if="canGenerate" :icon="['fas', 'file-circle-plus']" :disabled="generating.running.value"
                     data-testid="template-generate" @click="generating.run()">
        {{ t('documentTemplates.generateForMember') }}
      </PrimaryButton>
    </ButtonRow>
    <MutedText v-if="canGenerate" size="sm" tag="p">{{ t('documentTemplates.generateUsesSaved') }}</MutedText>
    <FailureAlert :failure="drawing.failure.value ?? generating.failure.value"/>
    <GeneratedPreview v-if="preview" :preview="preview" :title="draft.name || t('documentTemplates.preview')"/>
  </NeutralContainer>
</template>
