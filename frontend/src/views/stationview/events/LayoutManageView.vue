/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EventFieldEditor from '@/components/input/EventFieldEditor.vue'
import type {AttendanceTemplateField, EventFieldEntry, EventLayout, LayoutFieldEntry} from '@/api/types'
import {attendance, events} from '@/api'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const {loaded} = useSession()

const layouts = ref<EventLayout[]>([])
const attendanceFields = ref<AttendanceTemplateField[]>([])
const loading = ref(true)

// Edit modal state
const editOpen = ref(false)
const editLayoutId = ref<number | null>(null)
const editName = ref('')
const editFields = ref<EventFieldEntry[]>([])

onMounted(() => {
  if (loaded.value) loadData()
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) loadData()
})

async function loadData() {
  loading.value = true
  try {
    const [layoutList, templates] = await Promise.all([
      events.listLayouts(),
      attendance.listTemplates(),
    ])
    layouts.value = layoutList
    // Flatten all template fields for linking
    const allFields: AttendanceTemplateField[] = []
    for (const tmpl of templates) {
      const fields = await attendance.listTemplateFields(tmpl.id)
      allFields.push(...fields)
    }
    attendanceFields.value = allFields
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editLayoutId.value = null
  editName.value = ''
  editFields.value = []
  editOpen.value = true
}

async function openEdit(layout: EventLayout) {
  editLayoutId.value = layout.id
  editName.value = layout.name
  const fields = await events.getLayoutFields(layout.id)
  editFields.value = fields.map(f => ({
    name: f.name,
    fieldType: f.fieldType,
    config: f.config ? JSON.parse(f.config) : {},
    overview: f.overview,
    attendanceFieldId: f.attendanceFieldId ?? null,
  }))
  editOpen.value = true
}

function addField() {
  editFields.value.push({name: '', fieldType: 'STRING', config: {}, overview: false, attendanceFieldId: null})
}

function removeField(index: number) {
  editFields.value.splice(index, 1)
}

async function saveLayout() {
  let layoutId: number
  if (editLayoutId.value != null) {
    await events.updateLayout(editLayoutId.value, {name: editName.value})
    layoutId = editLayoutId.value
  } else {
    const created = await events.createLayout({name: editName.value})
    layoutId = created.id
  }
  const fieldEntries: LayoutFieldEntry[] = editFields.value.filter(f => f.name.trim()).map(f => ({
    name: f.name,
    fieldType: f.fieldType,
    config: f.config ?? undefined,
    overview: f.overview,
    attendanceFieldId: f.attendanceFieldId,
  }))
  await events.setLayoutFields(layoutId, {fields: fieldEntries})
  editOpen.value = false
  await loadData()
}

async function deleteLayout(id: number) {
  await events.deleteLayout(id)
  await loadData()
}
</script>

<template>
  <ViewContent>
    <div class="flex items-center justify-between">
      <SectionHeader>{{ t('eventLayouts.title') }}</SectionHeader>
      <PrimaryButton :icon="['fas', 'plus']" @click="openCreate">
        {{ t('eventLayouts.createLayout') }}
      </PrimaryButton>
    </div>

    <Spinner v-if="loading"/>

    <MutedText v-else-if="layouts.length === 0" tag="div" size="sm" class="py-4">
      {{ t('eventLayouts.noLayouts') }}
    </MutedText>

    <div v-else class="space-y-2">
      <NeutralContainer v-for="layout in layouts" :key="layout.id" class="flex items-center justify-between">
        <span class="font-medium">{{ layout.name }}</span>
        <div class="flex gap-2">
          <EditButton :label="t('common.edit')" @click="openEdit(layout)"/>
          <DeleteButton :label="t('common.delete')" @click="deleteLayout(layout.id)"/>
        </div>
      </NeutralContainer>
    </div>

    <!-- Edit/Create Modal -->
    <Modal v-model="editOpen">
      <div class="space-y-4">
        <SectionHeader>
          {{ editLayoutId != null ? t('eventLayouts.editLayout') : t('eventLayouts.createLayout') }}
        </SectionHeader>

        <div class="space-y-1">
          <TextInput v-model="editName" :placeholder="t('eventLayouts.layoutNamePlaceholder')"/>
        </div>

        <div class="space-y-3">
          <div class="flex items-center justify-between">
            <SubHeader>{{ t('eventLayouts.fields') }}</SubHeader>
            <SecondaryButton :icon="['fas', 'plus']" @click="addField">
              {{ t('eventFields.addField') }}
            </SecondaryButton>
          </div>

          <MutedText v-if="editFields.length === 0" tag="div" size="sm">
            {{ t('eventLayouts.noFields') }}
          </MutedText>

          <EventFieldEditor
              v-for="(field, index) in editFields"
              :key="index"
              :model-value="field"
              :attendance-fields="attendanceFields"
              @update:model-value="editFields[index] = $event"
              @remove="removeField(index)"
          />
        </div>

        <div class="flex justify-end gap-3">
          <SecondaryButton @click="editOpen = false">{{ t('common.cancel') }}</SecondaryButton>
          <SaveButton :disabled="!editName.trim()" :action="saveLayout"/>
        </div>
      </div>
    </Modal>
  </ViewContent>
</template>
