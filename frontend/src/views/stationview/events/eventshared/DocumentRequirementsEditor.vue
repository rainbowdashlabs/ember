/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TemplatePickerModal from '@/components/documents/templatepicker/TemplatePickerModal.vue'
import {appointmentDocuments} from '@/api'
import {StationModule, StationPermission, type DocumentTemplateSummary, type RequiredTemplate} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import RequiredTemplateRow from './RequiredTemplateRow.vue'
import {asRequired} from './useDocumentRequirements'

/**
 * The documents an appointment or an appointment template asks participants to bring: document
 * templates marked for appointments, chosen in the template picker. Every registered participant gets
 * a copy filled with their data and the appointment's. A template archived since it was chosen stays
 * until it is taken off, but cannot be chosen anew.
 *
 * <p>It shows wherever the station uses documents, also while no template is marked for appointments
 * yet: then it says so and leads to the templates, rather than leaving no trace of the feature.
 */
const chosen = defineModel<RequiredTemplate[]>({required: true})

defineProps<{
  /** How many templates for appointments the station offers. */
  offeredCount: number
}>()

const {t} = useI18n()
const {isModuleEnabled, hasPermission} = useSession()

const shown = computed(() => isModuleEnabled(StationModule.DOCUMENTS) || chosen.value.length > 0)
const mayWriteTemplates = computed(() => hasPermission(StationPermission.DOCUMENT_TEMPLATE_EDIT))
const chosenIds = computed(() => chosen.value.map(template => template.templateId))
const picking = ref(false)

function add(templates: DocumentTemplateSummary[]) {
  chosen.value = [...chosen.value, ...templates.map(asRequired)]
}

function remove(templateId: number) {
  chosen.value = chosen.value.filter(template => template.templateId !== templateId)
}
</script>

<template>
  <NeutralContainer v-if="shown" class="space-y-3" data-testid="document-requirements">
    <SubHeader>{{ t('events.documents.title') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('events.documents.hint') }}</MutedText>
    <MutedText v-if="offeredCount === 0" size="sm" tag="p" data-testid="document-requirements-none">
      {{ t('events.documents.noneOffered') }}
      <NuxtLink v-if="mayWriteTemplates" :to="{name: 'documents-templates'}">{{ t('events.documents.toTemplates') }}</NuxtLink>
    </MutedText>
    <ul v-if="chosen.length > 0" class="space-y-2">
      <RequiredTemplateRow v-for="template in chosen" :key="template.templateId" :template="template"
                           @remove="remove(template.templateId)"/>
    </ul>
    <SecondaryButton v-if="offeredCount > 0" :icon="['fas', 'plus']" data-testid="document-requirement-add"
                     @click="picking = true">
      {{ t('events.documents.add') }}
    </SecondaryButton>
    <TemplatePickerModal v-model="picking" :pages="appointmentDocuments.offeredTemplates" :chosen-ids="chosenIds"
                         multiple @pick="add"/>
  </NeutralContainer>
</template>
