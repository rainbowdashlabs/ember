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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
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
  name, description, dueAt, isPublic,
  templates, selectedTemplateId, members, selectedAssignees, selectedAssigneeIds, items,
  isEditMode, loading, error,
} = form

const assigneePickerValue = ref('')

const {running: saving, error: saveError, run: runSubmit} = useAsyncAction(
    async () => router.push({name: 'procedure-detail', params: {id: await form.submit()}}),
    {formatError: () => t('common.error')},
)

function handleSubmit() {
  if (!name.value.trim()) return
  error.value = ''
  return runSubmit()
}

function addAssigneeFromPicker() {
  form.addAssignee(Number(assigneePickerValue.value))
  assigneePickerValue.value = ''
}

watch(loaded, (v) => {
  if (v) form.reload()
}, {immediate: true})
</script>

<template>
  <ViewContent
      :title="t('pages.procedure-create.title')"
      :subtitle="t('pages.procedure-create.subtitle')"
  >
    <Spinner v-if="loading"/>
    <Alert v-if="error || saveError" variant="error" class="mb-4">{{ error || saveError }}</Alert>

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

        <AssigneesSection
            v-model:assignee-picker-value="assigneePickerValue"
            :members="members"
            :selected-assignees="selectedAssignees"
            :selected-assignee-ids="selectedAssigneeIds"
            @add="addAssigneeFromPicker"
            @remove="form.removeAssignee"
        />

        <ItemsSection
            :items="items"
            @add="form.addItem()"
            @move="form.moveItem"
            @remove="form.removeItem"
        />

        <div class="flex justify-end gap-2">
          <SecondaryButton @click="router.back()">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="!name.trim() || saving" @click="handleSubmit">
            {{ isEditMode ? t('common.save') : t('procedures.createProcedure') }}
          </PrimaryButton>
        </div>
      </div>
    </template>

  </ViewContent>
</template>
