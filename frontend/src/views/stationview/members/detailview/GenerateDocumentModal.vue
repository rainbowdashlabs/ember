/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import GeneratedPreview from '@/components/documents/GeneratedPreview.vue'
import {documentTemplates} from '@/api'
import type {DocumentTemplateSummary, PreviewResponse} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {showToast} from '@/util/toast'

/**
 * Generates a document from a template for one member: choose the template, look at the result with
 * the data it still lacks, and file it with the member. Missing data prints as lines to fill in, so a
 * manager may still file the document after the warning.
 */
const open = defineModel<boolean>({required: true})

const props = defineProps<{
  memberId: number
}>()

const emit = defineEmits<{
  filed: []
}>()

const {t} = useI18n()

const templates = ref<DocumentTemplateSummary[]>([])
const templateId = ref<number | null>(null)
const preview = ref<PreviewResponse | null>(null)
const chosen = computed(() => templates.value.find(template => template.id === templateId.value) ?? null)

const loader = useAsyncLoader(async () => {
  templates.value = await documentTemplates.usableTemplates()
})

const drawing = useAsyncAction(async (id: number) => {
  preview.value = await documentTemplates.previewForMember(id, props.memberId)
})

const filing = useAsyncAction(async () => {
  if (templateId.value === null) return
  await documentTemplates.generateForMember(templateId.value, props.memberId)
  showToast(t('documentTemplates.generated'), 'success')
  emit('filed')
  open.value = false
})

watch(templateId, id => {
  preview.value = null
  if (id !== null) drawing.run(id)
})
</script>

<template>
  <Modal v-model="open" size="2xl">
    <div class="space-y-4" data-testid="generate-document-modal">
      <SubHeader>{{ t('documentTemplates.generateTitle') }}</SubHeader>
      <FailureAlert :failure="loader.failure.value ?? drawing.failure.value ?? filing.failure.value"/>
      <MutedText v-if="!loader.loading.value && templates.length === 0" size="sm" tag="p">
        {{ t('documentTemplates.noTemplates') }}
      </MutedText>
      <LabelledField v-else :label="t('documentTemplates.template')">
        <SelectInput :model-value="templateId" data-testid="generate-template"
                     @update:model-value="value => templateId = value === null ? null : Number(value)">
          <option :value="null" disabled>{{ t('documentTemplates.chooseTemplate') }}</option>
          <option v-for="template in templates" :key="template.id" :value="template.id">
            {{ template.ofAssociation ? t('documentTemplates.namedOfAssociation', {name: template.name}) : template.name }}
          </option>
        </SelectInput>
      </LabelledField>
      <GeneratedPreview v-if="preview && chosen" :preview="preview" :title="chosen.name"/>
      <ButtonRow pair align="end">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :icon="['fas', 'file-circle-plus']" :disabled="!preview || filing.running.value"
                       data-testid="generate-file" @click="filing.run()">
          {{ preview && preview.missing.length > 0 ? t('documentTemplates.generateAnyway') : t('documentTemplates.generate') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
