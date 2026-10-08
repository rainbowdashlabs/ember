/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {watchDebounced} from '@vueuse/core'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import type {MemberOption} from '@/components/input/select/memberOption'
import SubHeader from '@/components/typography/SubHeader.vue'
import GeneratedPreview from './GeneratedPreview.vue'
import TemplateChoice from './TemplateChoice.vue'
import IssuerOverride from './IssuerOverride.vue'
import SignatureAskStep from './SignatureAskStep.vue'
import {fromCompletion} from '@/components/input/select/memberOption'
import {documentTemplates, stationMembers} from '@/api'
import type {DocumentTemplateSummary, IssuerChoice, PreviewIssuer, PreviewResponse} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {showToast} from '@/util/toast'

/**
 * Generates a document from a template for one member: choose the template, look at the result with
 * the data it still lacks, and file it with the member. Missing data prints as lines to fill in, so a
 * manager may still file the document after the warning. The template is chosen in the template
 * picker, which opens on the templates used most recently at the station.
 *
 * <p>A member's page hands the member in. The document store has nobody in front of it, so there the
 * member is chosen in the dialog, from the members handed in instead.
 *
 * <p>Templates for appointments are not offered: generated for a member alone, the appointment's own
 * values would all print as gaps.
 *
 * <p>Where the document names its issuer, the template's issuer is shown and another current member of
 * the station can be picked for this one document; the preview follows the choice.
 *
 * <p>A filed document with fields to sign leads on to asking for the signatures, which may be skipped and
 * done later from the list of generated documents; one without closes the dialog.
 */
const open = defineModel<boolean>({required: true})

const props = withDefaults(defineProps<{
  /** The member the document is for, where the screen already names them. */
  memberId?: number | null
  /** The members to choose from, where no member is handed in. */
  members?: MemberOption[]
}>(), {
  memberId: null,
  members: () => [],
})

const emit = defineEmits<{
  filed: [documentId: number]
}>()

const ISSUER_SETTLE_MS = 400

const {t} = useI18n()

const NOT_FOR_APPOINTMENTS = {forAppointments: false}

const chosen = ref<DocumentTemplateSummary | null>(null)
const issuerCandidates = ref<MemberOption[]>([])
const templateId = computed(() => chosen.value?.id ?? null)
const chosenMember = ref('')
const preview = ref<PreviewResponse | null>(null)
const templateIssuer = ref<PreviewIssuer | null>(null)
const issuer = ref<IssuerChoice | null>(null)
const memberId = computed(() => props.memberId ?? (chosenMember.value ? Number(chosenMember.value) : null))

const loader = useAsyncLoader(async () => {
  const completions = await stationMembers.listCompletions().catch(() => [])
  issuerCandidates.value = completions.map(fromCompletion)
})

const drawing = useAsyncAction(async (id: number, member: number) => {
  preview.value = await documentTemplates.previewForMember(id, member, issuer.value)
  if (issuer.value === null) templateIssuer.value = preview.value.issuer ?? null
})

const filedGeneration = ref<number | null>(null)

const filing = useAsyncAction(async () => {
  if (templateId.value === null || memberId.value === null) return
  const filed = await documentTemplates.generateForMember(templateId.value, memberId.value, issuer.value)
  showToast(t('documentTemplates.generated'), 'success')
  emit('filed', filed.documentId)
  filedGeneration.value = filed.generationId
})

function finish() {
  open.value = false
}

watch(open, isOpen => {
  if (!isOpen) filedGeneration.value = null
})

watch([templateId, memberId], ([id, member]) => {
  preview.value = null
  templateIssuer.value = null
  issuer.value = null
  if (id !== null && member !== null) drawing.run(id, member)
})

watchDebounced(issuer, () => {
  if (templateId.value !== null && memberId.value !== null) drawing.run(templateId.value, memberId.value)
}, {debounce: ISSUER_SETTLE_MS, deep: true})
</script>

<template>
  <Modal v-model="open" size="2xl">
    <SignatureAskStep v-if="filedGeneration !== null" :generation-id="filedGeneration" skip-when-none @done="finish"/>
    <div v-else class="space-y-4" data-testid="generate-document-modal">
      <SubHeader>{{ t('documentTemplates.generateTitle') }}</SubHeader>
      <FailureAlert :failure="loader.failure.value ?? drawing.failure.value ?? filing.failure.value"/>
      <TemplateChoice v-model="chosen" :fixed="NOT_FOR_APPOINTMENTS"/>
      <LabelledField v-if="props.memberId === null" :label="t('documentTemplates.generateMember')">
        <MemberSelectInput v-model="chosenMember" :members="props.members" data-testid="generate-member"/>
      </LabelledField>
      <IssuerOverride v-if="templateIssuer" :key="templateId ?? 0" v-model="issuer" :template-issuer="templateIssuer"
                      :members="issuerCandidates"/>
      <GeneratedPreview v-if="preview && chosen" :preview="preview" :title="chosen.name"/>
      <ButtonRow pair align="end">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :icon="['fas', 'file-circle-plus']" :disabled="!preview || filing.running.value"
                       data-testid="generate-file" @click="filing.run()">
          {{ preview && preview.missing.length > 0 ? t('documentTemplates.generateAnyway') : t('common.create') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
