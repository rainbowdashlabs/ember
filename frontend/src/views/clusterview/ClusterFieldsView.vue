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
import ProfileFieldModal from '@/views/stationview/manage/membersconfig/FieldModal.vue'
import FieldsPanel from '@/views/stationview/manage/membersconfig/FieldsPanel.vue'
import FieldsPreview from '@/views/stationview/manage/membersconfig/FieldsPreview.vue'
import {clusterFields, clusterStationGroups} from '@/api'
import {CLUSTER_FIELD_SCOPES, CLUSTER_FIELD_TYPES} from '@/api/clusterFields'
import {useFieldsConfig, type FieldsPort} from '@/composables/useFieldsConfig'

const {t} = useI18n()

/**
 * An association asks its questions of the members of all its stations, and they are answered on a
 * station's own profile screen beside that station's own questions.
 *
 * <p>Two narrowings and one widening against a station. It declares no group scope, because a group
 * belongs to one station and an association has no view of those. It may not ask for a date of birth,
 * because the station declares its own and the two would collide. And it alone can say that the
 * station may read an answer without writing it, because it alone has somebody below it.
 */
const port: FieldsPort = {
  list: () => clusterFields.listFields(),
  create: (field) => clusterFields.createField(field),
  update: (id, field) => clusterFields.updateField(id, field),
  reorder: (fieldIds) => clusterFields.reorderFields(fieldIds),
  remove: (id) => clusterFields.deleteField(id),
  scopes: CLUSTER_FIELD_SCOPES,
  types: CLUSTER_FIELD_TYPES,
  stationReadonly: true,
  listStationGroups: () => clusterStationGroups.listGroups(),
}

const {
  activeTab, currentFields, previewFields, availableStationGroups, selectedStationGroupId,
  dateFields, birthDateField, showFieldModal, editingField,
  loading, error, openAddField, openEditField, saveField, toggleFieldConfig,
  toggleKeepOnArchive, setWritability, showDeleteModal, deleteTarget, requestDelete,
  confirmDelete, onReorder, applyTemplate,
} = useFieldsConfig(port)

const tabs = computed(() => [
  {key: 'MEMBER', label: t('membersConfig.tabMember')},
  {key: 'GUARDIAN', label: t('membersConfig.tabGuardian')},
  {key: 'TEAM', label: t('membersConfig.tabTeam')},
  {key: 'MANAGER', label: t('membersConfig.tabStationManager')},
])

/**
 * The second axis: who a question is asked of. An association that files nothing sees exactly the
 * screen it saw before this row existed.
 */
const stationGroupTabs = computed(() => [
  {key: '', label: t('membersConfig.everyStation')},
  ...availableStationGroups.value.map(g => ({key: String(g.id), label: g.name})),
])

const activeStationGroup = computed({
  get: () => selectedStationGroupId.value === null ? '' : String(selectedStationGroupId.value),
  set: (key: string) => { selectedStationGroupId.value = key ? Number(key) : null },
})
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-fields.subtitle')" :title="t('pages.cluster-fields.title')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <p class="text-sm text-(--text-muted)">{{ t('clusterFields.hint') }}</p>

      <div v-if="!loading" class="space-y-6">
        <TabBar v-model="activeTab" :tabs="tabs"/>

        <TabBar v-if="availableStationGroups.length > 0" v-model="activeStationGroup" :tabs="stationGroupTabs"/>

        <FieldsPanel
            :active-tab="activeTab"
            :fields="currentFields"
            @add="openAddField"
            @edit="openEditField"
            @delete="requestDelete"
            @reorder="onReorder"
            @toggle-config="toggleFieldConfig"
            @toggle-keep-on-archive="toggleKeepOnArchive"
            @set-writability="setWritability"
            @apply-template="applyTemplate"
        />

        <FieldsPreview v-if="previewFields.length > 0" :fields="previewFields"/>
      </div>

      <ProfileFieldModal
          v-model="showFieldModal"
          :birth-date-field="birthDateField"
          :date-fields="dateFields"
          :field="editingField"
          group-id=""
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
