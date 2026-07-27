/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import ViewContent from '@/components/layout/ViewContent.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ProfileFieldModal from './membersconfig/FieldModal.vue'
import FieldsPanel from './membersconfig/FieldsPanel.vue'
import type {FieldTemplate} from './membersconfig/fieldTemplates'
import type {MemberGroup, ProfileField} from '@/api/types'
import {parseFieldConfig} from '@/api/types'
import {memberGroups, profileFields} from '@/api'

const {t} = useI18n()

const allFields = ref<ProfileField[]>([])
const availableGroups = ref<MemberGroup[]>([])
const activeTab = ref('MEMBER')
const selectedGroupId = ref('')

const tabs = computed(() => [
  {key: 'MEMBER', label: t('membersConfig.tabMember')},
  {key: 'GUARDIAN', label: t('membersConfig.tabGuardian')},
  {key: 'TEAM', label: t('membersConfig.tabTeam')},
  {key: 'MANAGER', label: t('membersConfig.tabStationManager')},
  {key: 'GROUP', label: t('membersConfig.tabGroup')},
])

const currentFields = computed(() => {
  if (activeTab.value === 'MEMBER') return allFields.value.filter(f => f.scope === 'MEMBER')
  if (activeTab.value === 'GUARDIAN') return allFields.value.filter(f => f.scope === 'GUARDIAN')
  if (activeTab.value === 'TEAM') return allFields.value.filter(f => f.scope === 'TEAM')
  if (activeTab.value === 'MANAGER') return allFields.value.filter(f => f.scope === 'MANAGER')
  if (!selectedGroupId.value) return []
  return allFields.value.filter(f => {
    if (f.scope !== 'GROUP') return false
    const cfg = parseFieldConfig(f.config)
    return cfg.groupId === Number(selectedGroupId.value)
  })
})

const dateFields = computed(() => currentFields.value.filter(f => f.fieldType === 'DATE'))

const showFieldModal = ref(false)
const editingField = ref<ProfileField | null>(null)

const {loading, error, reload} = useAsyncLoader(async () => {
  const [fields, groups] = await Promise.all([
    profileFields.listFields(),
    memberGroups.listGroups(),
  ])
  allFields.value = fields
  availableGroups.value = groups
})

function openAddField() {
  editingField.value = null
  showFieldModal.value = true
}

function openEditField(field: ProfileField) {
  editingField.value = field
  showFieldModal.value = true
}

async function saveField(data: { name: string; fieldType: string; config: string; position: number; scope: string; keepOnArchive: boolean }) {
  error.value = ''
  try {
    if (editingField.value) {
      await profileFields.updateField(editingField.value.id, data)
    } else {
      await profileFields.createField({...data, position: currentFields.value.length})
    }
    showFieldModal.value = false
    await reload()
  } catch {
    error.value = t('common.error')
  }
}

function updateFieldLocally(fieldId: number, patch: Partial<ProfileField>) {
  allFields.value = allFields.value.map(f => f.id === fieldId ? { ...f, ...patch } : f)
}

async function toggleFieldConfig(field: ProfileField, key: string, value: boolean) {
  const cfg = parseFieldConfig(field.config)
  if (value) {
    (cfg as Record<string, unknown>)[key] = true
  } else {
    delete (cfg as Record<string, unknown>)[key]
  }
  const newConfig = JSON.stringify(cfg)
  updateFieldLocally(field.id, { config: newConfig })
  try {
    await profileFields.updateField(field.id, {
      name: field.name ?? '',
      fieldType: field.fieldType ?? '',
      config: newConfig,
      position: field.position,
      keepOnArchive: field.keepOnArchive,
    })
  } catch {
    error.value = t('common.error')
    await reload()
  }
}

async function toggleKeepOnArchive(field: ProfileField, value: boolean) {
  updateFieldLocally(field.id, { keepOnArchive: value })
  try {
    await profileFields.updateField(field.id, {
      name: field.name ?? '',
      fieldType: field.fieldType ?? '',
      config: typeof field.config === 'string' ? field.config : JSON.stringify(field.config ?? {}),
      position: field.position,
      keepOnArchive: value,
    })
  } catch {
    error.value = t('common.error')
    await reload()
  }
}

const {
  show: showDeleteModal,
  target: deleteTarget,
  requestDelete,
  confirm: confirmDelete,
} = useConfirmDelete<ProfileField>({
  onDelete: (field) => profileFields.deleteField(field.id),
  onSuccess: () => reload(),
  error,
})

async function onReorder(fromIndex: number, toIndex: number) {
  const arr = [...currentFields.value]
  const [moved] = arr.splice(fromIndex, 1)
  arr.splice(toIndex, 0, moved)
  try {
    for (let i = 0; i < arr.length; i++) {
      await profileFields.updateField(arr[i].id, {
        name: arr[i].name ?? '',
        fieldType: arr[i].fieldType ?? '',
        config: typeof arr[i].config === 'string' ? (arr[i].config as string) : JSON.stringify(arr[i].config ?? {}),
        position: i,
      })
    }
    await reload()
  } catch {
    error.value = t('common.error')
  }
}

async function applyTemplate(template: FieldTemplate) {
  error.value = ''
  try {
    const startPosition = currentFields.value.length
    for (let i = 0; i < template.fields.length; i++) {
      const f = template.fields[i]
      await profileFields.createField({
        name: f.name,
        fieldType: f.fieldType,
        config: f.config,
        position: startPosition + i,
        scope: activeTab.value,
      })
    }
    await reload()
  } catch {
    error.value = t('common.error')
  }
}

</script>

<template>
  <ViewContent
      :title="t('pages.station-members-config.title')"
      :subtitle="t('pages.station-members-config.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <div v-if="!loading" class="space-y-6">
        <TabBar v-model="activeTab" :tabs="tabs"/>

        <!-- Group selector for GROUP tab -->
        <div v-if="activeTab === 'GROUP'" class="space-y-2">
          <FieldLabel>{{ t('membersConfig.selectGroup') }}</FieldLabel>
          <SelectInput v-model="selectedGroupId" class="w-full">
            <option disabled value="">{{ t('membersConfig.selectGroupPlaceholder') }}</option>
            <option v-for="group in availableGroups" :key="group.id" :value="String(group.id)">{{ group.name }}</option>
          </SelectInput>
        </div>

        <FieldsPanel
            v-if="activeTab !== 'GROUP' || selectedGroupId"
            :active-tab="activeTab"
            :fields="currentFields"
            @add="openAddField"
            @edit="openEditField"
            @delete="requestDelete"
            @reorder="onReorder"
            @toggle-config="toggleFieldConfig"
            @toggle-keep-on-archive="toggleKeepOnArchive"
            @apply-template="applyTemplate"
        />
      </div>

      <ProfileFieldModal
          v-model="showFieldModal"
          :date-fields="dateFields"
          :field="editingField"
          :group-id="selectedGroupId"
          :scope="activeTab"
          @save="saveField"
      />

      <ConfirmDeleteModal
          v-model="showDeleteModal"
          :message="t('membersConfig.deleteConfirm', { name: deleteTarget?.name })"
          @confirm="confirmDelete"
      />
    </div>
  </ViewContent>
</template>
