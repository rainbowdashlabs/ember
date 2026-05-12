/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ProfileFieldModal from './membersconfig/FieldModal.vue'
import ProfileFieldTable from './membersconfig/FieldTable.vue'
import type {MemberGroup, ProfileField} from '@/api/types'
import {memberGroups, profileFields} from '@/api'

const {t} = useI18n()

const allFields = ref<ProfileField[]>([])
const availableGroups = ref<MemberGroup[]>([])
const loading = ref(true)
const error = ref('')
const activeTab = ref('MEMBER')
const selectedGroupId = ref('')

const tabs = computed(() => [
  {key: 'MEMBER', label: t('membersConfig.tabMember')},
  {key: 'MEMBER_MANAGER', label: t('membersConfig.tabManager')},
  {key: 'TEAM', label: t('membersConfig.tabTeam')},
  {key: 'GROUP', label: t('membersConfig.tabGroup')},
])

const currentFields = computed(() => {
  if (activeTab.value === 'MEMBER') return allFields.value.filter(f => f.scope === 'MEMBER')
  if (activeTab.value === 'MEMBER_MANAGER') return allFields.value.filter(f => f.scope === 'MEMBER_MANAGER')
  if (activeTab.value === 'TEAM') return allFields.value.filter(f => f.scope === 'TEAM')
  if (!selectedGroupId.value) return []
  return allFields.value.filter(f => {
    if (f.scope !== 'GROUP') return false
    const cfg = parseConfig(f.config)
    return cfg.groupId === Number(selectedGroupId.value)
  })
})

const dateFields = computed(() => currentFields.value.filter(f => f.fieldType === 'date'))

function parseConfig(configStr: string | undefined): Record<string, unknown> {
  if (!configStr) return {}
  try {
    return JSON.parse(configStr)
  } catch {
    return {}
  }
}

// Field templates
interface FieldTemplate {
  name: string
  icon: string
  fields: Array<{ name: string; fieldType: string; config: string }>
}

const fieldTemplates: FieldTemplate[] = [
  {
    name: 'Adresse', icon: 'house', fields: [
      {name: 'Straße', fieldType: 'text', config: '{"required":true}'},
      {name: 'Postleitzahl', fieldType: 'text', config: '{"required":true}'},
      {name: 'Ort', fieldType: 'text', config: '{"required":true}'},
    ]
  },
  {
    name: 'Geburtsdatum', icon: 'calendar-plus', fields: [
      {name: 'Geburtsdatum', fieldType: 'date', config: '{"required":true,"readonly":true}'},
    ]
  },
  {
    name: 'Festnetz', icon: 'phone', fields: [
      {name: 'Festnetznummer', fieldType: 'text', config: '{"notifyOnChange":true}'},
    ]
  },
  {
    name: 'Mobilnummer', icon: 'mobile-screen', fields: [
      {name: 'Mobilnummer', fieldType: 'text', config: '{"notifyOnChange":true}'},
    ]
  },
  {
    name: 'Notfallkontakt', icon: 'triangle-exclamation', fields: [
      {name: 'Notfallkontakt Name', fieldType: 'text', config: '{"required":true}'},
      {name: 'Notfallkontakt Telefon', fieldType: 'text', config: '{"required":true}'},
    ]
  },
  {
    name: 'Führerschein', icon: 'id-card', fields: [
      {name: 'Führerscheinklasse', fieldType: 'text', config: '{}'},
      {name: 'Führerschein gültig bis', fieldType: 'date', config: '{}'},
    ]
  },
  {
    name: 'Beitrittsdatum', icon: 'calendar-plus', fields: [
      {name: 'Beitrittsdatum', fieldType: 'date', config: '{"readonly":true}'},
    ]
  },
  {
    name: 'Personalnummer', icon: 'hashtag', fields: [
      {name: 'Personalnummer', fieldType: 'text', config: '{"readonly":true}'},
    ]
  },
  {
    name: 'Geschlecht', icon: 'rainbow', fields: [
      {
        name: 'Geschlecht',
        fieldType: 'enum',
        config: '{"options":["Männlich","Weiblich","Divers","Nicht-binär","Andere"],"readonly":true}'
      },
    ]
  },
  {
    name: 'Jugendflamme', icon: 'fire', fields: [
      {
        name: 'Jugendflamme Stufe',
        fieldType: 'enum',
        config: '{"options":["Jugendflamme 1","Jugendflamme 2","Jugendflamme 3"],"readonly":true}'
      },
      {name: 'Jugendflamme Datum', fieldType: 'date', config: '{"readonly":true}'},
    ]
  },
  {
    name: 'Leistungsspange', icon: 'medal', fields: [
      {name: 'Leistungsspange', fieldType: 'boolean', config: '{"readonly":true}'},
      {name: 'Leistungsspange Datum', fieldType: 'date', config: '{"readonly":true}'},
    ]
  },
]

// Modal state
const showFieldModal = ref(false)
const editingField = ref<ProfileField | null>(null)
const showDeleteModal = ref(false)
const deleteTarget = ref<ProfileField | null>(null)

async function loadFields() {
  loading.value = true
  error.value = ''
  try {
    const [fields, groups] = await Promise.all([
      profileFields.listFields(),
      memberGroups.listGroups(),
    ])
    allFields.value = fields
    availableGroups.value = groups
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function openAddField() {
  editingField.value = null
  showFieldModal.value = true
}

function openEditField(field: ProfileField) {
  editingField.value = field
  showFieldModal.value = true
}

async function saveField(data: { name: string; fieldType: string; config: string; position: number; scope: string }) {
  error.value = ''
  try {
    if (editingField.value) {
      await profileFields.updateField(editingField.value.id, data)
    } else {
      await profileFields.createField({...data, position: currentFields.value.length})
    }
    showFieldModal.value = false
    await loadFields()
  } catch {
    error.value = t('common.error')
  }
}

async function toggleFieldConfig(field: ProfileField, key: string, value: boolean) {
  const cfg = parseConfig(field.config)
  if (value) {
    (cfg as Record<string, unknown>)[key] = true
  } else {
    delete (cfg as Record<string, unknown>)[key]
  }
  try {
    await profileFields.updateField(field.id, {
      name: field.name ?? '',
      fieldType: field.fieldType ?? '',
      config: JSON.stringify(cfg),
      position: field.position,
    })
    await loadFields()
  } catch {
    error.value = t('common.error')
  }
}

function requestDelete(field: ProfileField) {
  deleteTarget.value = field
  showDeleteModal.value = true
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await profileFields.deleteField(deleteTarget.value.id)
    showDeleteModal.value = false
    deleteTarget.value = null
    await loadFields()
  } catch {
    error.value = t('common.error')
  }
}

async function onReorder(fromIndex: number, toIndex: number) {
  const arr = [...currentFields.value]
  const [moved] = arr.splice(fromIndex, 1)
  arr.splice(toIndex, 0, moved)
  try {
    for (let i = 0; i < arr.length; i++) {
      await profileFields.updateField(arr[i].id, {
        name: arr[i].name ?? '',
        fieldType: arr[i].fieldType ?? '',
        config: arr[i].config ?? '{}',
        position: i,
      })
    }
    await loadFields()
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
    await loadFields()
  } catch {
    error.value = t('common.error')
  }
}

onMounted(loadFields)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <div v-if="!loading" class="space-y-6">
        <TabBar v-model="activeTab" :tabs="tabs"/>

        <!-- Group selector for GROUP tab -->
        <div v-if="activeTab === 'GROUP'" class="space-y-2">
          <label class="block text-sm font-medium">{{ t('membersConfig.selectGroup') }}</label>
          <SelectInput v-model="selectedGroupId">
            <option disabled value="">{{ t('membersConfig.selectGroupPlaceholder') }}</option>
            <option v-for="group in availableGroups" :key="group.id" :value="String(group.id)">{{ group.name }}</option>
          </SelectInput>
        </div>

        <!-- Fields list -->
        <NeutralContainer v-if="activeTab !== 'GROUP' || selectedGroupId" class="space-y-4">
          <div class="flex items-center justify-between">
            <SectionHeader>{{ t('membersConfig.fields') }}</SectionHeader>
            <PrimaryButton @click="openAddField">
              <font-awesome-icon :icon="['fas', 'plus']" class="mr-2"/>
              {{ t('membersConfig.addField') }}
            </PrimaryButton>
          </div>

          <p class="text-sm text-(--text-muted)">
            <template v-if="activeTab === 'MEMBER'">{{ t('membersConfig.memberHint') }}</template>
            <template v-else-if="activeTab === 'MEMBER_MANAGER'">{{ t('membersConfig.managerHint') }}</template>
            <template v-else-if="activeTab === 'TEAM'">{{ t('membersConfig.teamHint') }}</template>
            <template v-else>{{ t('membersConfig.groupHint') }}</template>
          </p>

          <div v-if="currentFields.length === 0" class="space-y-4">
            <p class="text-center text-(--text-muted) py-4">{{ t('membersConfig.noFields') }}</p>
            <div class="space-y-2">
              <label class="block text-sm font-medium">{{ t('membersConfig.templates') }}</label>
              <div class="flex flex-wrap gap-2">
                <SecondaryButton v-for="tpl in fieldTemplates" :key="tpl.name" class="text-sm"
                                 @click="applyTemplate(tpl)">
                  <font-awesome-icon :icon="['fas', tpl.icon]" class="mr-1"/>
                  {{ tpl.name }}
                </SecondaryButton>
              </div>
            </div>
          </div>

          <ProfileFieldTable
              v-if="currentFields.length > 0"
              :fields="currentFields"
              @delete="requestDelete"
              @edit="openEditField"
              @reorder="onReorder"
              @toggle-config="toggleFieldConfig"
          />

          <!-- Templates when fields exist -->
          <div v-if="currentFields.length > 0" class="pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
            <label class="block text-xs font-medium text-(--text-muted) mb-2">{{ t('membersConfig.templates') }}</label>
            <div class="flex flex-wrap gap-2">
              <SecondaryButton v-for="tpl in fieldTemplates" :key="tpl.name" class="text-xs"
                               @click="applyTemplate(tpl)">
                <font-awesome-icon :icon="['fas', tpl.icon]" class="mr-1"/>
                {{ tpl.name }}
              </SecondaryButton>
            </div>
          </div>
        </NeutralContainer>
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
