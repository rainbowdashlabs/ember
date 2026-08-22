/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import ProfileFieldModal from './membersconfig/FieldModal.vue'
import GroupSelect from './membersconfig/GroupSelect.vue'
import FieldsPanel from './membersconfig/FieldsPanel.vue'
import UnassignedGroupFields from './membersconfig/UnassignedGroupFields.vue'
import FieldsPreview from './membersconfig/FieldsPreview.vue'
import {FieldTypes} from '@/api/profileFields'
import {memberGroups, profileFields} from '@/api'
import {useFieldsConfig, type FieldsPort} from '@/composables/useFieldsConfig'

const {t} = useI18n()

/** A station declares every scope and every type, and has nobody above it to lock a field from. */
const port: FieldsPort = {
  list: () => profileFields.listFields(),
  create: (field) => profileFields.createField(field),
  update: (id, field) => profileFields.updateField(id, field),
  remove: (id) => profileFields.deleteField(id),
  scopes: ['MEMBER', 'GUARDIAN', 'TEAM', 'MANAGER', 'GROUP'],
  types: Object.values(FieldTypes),
  listGroups: () => memberGroups.listGroups(),
  stationReadonly: false,
}

const {
  availableGroups, activeTab, selectedGroupId, currentFields, unassignedGroupFields,
  dateFields, birthDateField, showFieldModal, editingField, loading, error,
  openAddField, openEditField, saveField, toggleFieldConfig, toggleKeepOnArchive,
  showDeleteModal, deleteTarget, requestDelete, confirmDelete, onReorder, applyTemplate,
} = useFieldsConfig(port)

const tabs = computed(() => [
  {key: 'MEMBER', label: t('membersConfig.tabMember')},
  {key: 'GUARDIAN', label: t('membersConfig.tabGuardian')},
  {key: 'TEAM', label: t('membersConfig.tabTeam')},
  {key: 'MANAGER', label: t('membersConfig.tabStationManager')},
  {key: 'GROUP', label: t('membersConfig.tabGroup')},
])
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

        <GroupSelect v-if="activeTab === 'GROUP'" v-model="selectedGroupId" :groups="availableGroups"/>

        <UnassignedGroupFields
            v-if="activeTab === 'GROUP' && selectedGroupId && unassignedGroupFields.length > 0"
            :fields="unassignedGroupFields"
            @edit="openEditField"
        />

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

        <FieldsPreview v-if="currentFields.length > 0" :fields="currentFields"/>
      </div>

      <ProfileFieldModal
          v-model="showFieldModal"
          :birth-date-field="birthDateField"
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
