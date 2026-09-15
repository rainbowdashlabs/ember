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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import ProfileFieldModal from '@/views/stationview/manage/membersconfig/FieldModal.vue'
import FieldsWorkspace from '@/views/stationview/manage/membersconfig/FieldsWorkspace.vue'
import {clusterFields, clusterStationGroups} from '@/api'
import {CLUSTER_FIELD_ROLES, CLUSTER_FIELD_TYPES} from '@/api/clusterFields'
import {useFieldsConfig, type FieldsPort} from '@/composables/useFieldsConfig'

const {t} = useI18n()

/**
 * An association asks its questions of the members of all its stations, and they are answered on a
 * station's own profile screen beside that station's own questions.
 *
 * <p>Two narrowings and one widening against a station. It names no member groups, because a group
 * belongs to one station and an association has no view of those. It may not ask for a date of birth,
 * because the station declares its own and the two would collide. And it alone can say that the
 * station may read an answer without writing it, because it alone has somebody below it.
 */
const port: FieldsPort = {
  list: () => clusterFields.listFields(),
  listAssignments: () => clusterFields.listAssignments(),
  create: (field) => clusterFields.createField(field),
  update: (id, field) => clusterFields.updateField(id, field),
  remove: (id) => clusterFields.deleteField(id),
  assign: (fieldId, assignment) => clusterFields.assignField(fieldId, assignment),
  unassign: (fieldId, target) => clusterFields.unassignField(fieldId, target),
  reorder: (role, fieldIds) => clusterFields.reorderFields(role, fieldIds),
  roles: CLUSTER_FIELD_ROLES,
  types: CLUSTER_FIELD_TYPES,
  stationReadonly: true,
  listStationGroups: () => clusterStationGroups.listGroups(),
}

const config = useFieldsConfig(port)
const {
  availableStationGroups, selectedStationGroupId,
  birthDateField, dateFields, showFieldModal, editingField, loading, error,
  saveField, showDeleteModal, deleteTarget, confirmDelete,
} = config

/**
 * The second axis: which stations a question reaches. An association that files nothing sees exactly
 * the screen it saw before this row existed.
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
      <FailureAlert :message="error"/>

      <p class="text-sm text-(--text-muted)">{{ t('clusterFields.hint') }}</p>

      <TabBar v-if="!loading && availableStationGroups.length > 0"
              v-model="activeStationGroup" :tabs="stationGroupTabs"/>

      <FieldsWorkspace v-if="!loading" :config="config" :roles="CLUSTER_FIELD_ROLES"/>

      <ProfileFieldModal
          v-model="showFieldModal"
          :birth-date-field="birthDateField"
          :date-fields="dateFields"
          :field="editingField"
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
