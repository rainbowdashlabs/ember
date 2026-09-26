/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import TemplateSelectorSection from '@/views/stationview/procedure/procedurecreateview/TemplateSelectorSection.vue'
import BasicInfoSection from '@/views/stationview/procedure/procedurecreateview/BasicInfoSection.vue'
import AssigneesSection from '@/views/stationview/procedure/procedurecreateview/AssigneesSection.vue'
import ItemsSection from '@/views/stationview/procedure/procedurecreateview/ItemsSection.vue'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useProcedureForm} from '@/composables/useProcedureForm'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded} = useSession()

const editId = computed(() => {
  const id = route.params.id
  return id ? Number(id) : null
})

const presetTemplateId = computed(() => (route.query.template ? Number(route.query.template) : null))

const form = useProcedureForm(editId, presetTemplateId)
const {
  name, savedName, description, dueAt, isPublic,
  templates, selectedTemplateId, members, selectedAssigneeIds, items,
  isEditMode, loading, failure,
} = form

/**
 * A procedure being edited is named after itself; one being created has no name yet and keeps the
 * words of the form.
 */
const pageTitle = computed(() => isEditMode.value
    ? savedName.value || t('pages.procedure-edit.title')
    : t('pages.procedure-create.title'))

const pageSubtitle = computed(() => isEditMode.value
    ? t('pages.procedure-edit.subtitle')
    : t('pages.procedure-create.subtitle'))

const {running: saving, failure: saveFailure, run: runSubmit} = useAsyncAction(
    async () => router.push({name: 'procedure-detail', params: {id: await form.submit()}}),
)

function handleSubmit() {
  if (!name.value.trim()) return
  failure.value = null
  return runSubmit()
}

watch(loaded, (v) => {
  if (v) form.reload()
}, {immediate: true})
</script>

<template>
  <ViewContent
      :title="pageTitle"
      :subtitle="pageSubtitle"
  >
    <Spinner v-if="loading"/>
    <FailureAlert :failure="failure ?? saveFailure" class="mb-4"/>

    <template v-if="!loading">
      <div class="space-y-6">
        <TemplateSelectorSection
            v-if="!isEditMode"
            :templates="templates"
            :selected-template-id="selectedTemplateId"
            @change="form.handleTemplateChange"
        />

        <BasicInfoSection
            v-model:name="name"
            v-model:description="description"
            v-model:due-at="dueAt"
            v-model:is-public="isPublic"
        />

        <AssigneesSection v-model:assignee-ids="selectedAssigneeIds" :members="members"/>

        <ItemsSection
            :items="items"
            @add="form.addItem()"
            @reorder="form.reorderItems"
            @remove="form.removeItem"
        />

        <ButtonRow pair align="end">
          <SecondaryButton @click="router.back()">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="!name.trim() || saving" @click="handleSubmit">
            {{ isEditMode ? t('common.save') : t('procedures.createProcedure') }}
          </PrimaryButton>
        </ButtonRow>
      </div>
    </template>

  </ViewContent>
</template>
