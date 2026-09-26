/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import ProfileFieldModal from './membersconfig/FieldModal.vue'
import FieldsWorkspace from './membersconfig/FieldsWorkspace.vue'
import {FieldTypes} from '@/api/profileFields'
import {memberGroups, profileFields} from '@/api'
import {STATION_ROLES, useFieldsConfig, type FieldsPort} from '@/composables/useFieldsConfig'

const {t} = useI18n()

/** A station asks every kind of member, offers every type, and has nobody above it to lock a field from. */
const port: FieldsPort = {
  list: () => profileFields.listFields(),
  listAssignments: () => profileFields.listAssignments(),
  create: (field) => profileFields.createField(field),
  update: (id, field) => profileFields.updateField(id, field),
  remove: (id) => profileFields.deleteField(id),
  assign: (fieldId, assignment) => profileFields.assignField(fieldId, assignment),
  unassign: (fieldId, target) => profileFields.unassignField(fieldId, target),
  reorder: (role, fieldIds) => profileFields.reorderFields(role, fieldIds),
  roles: STATION_ROLES,
  types: Object.values(FieldTypes),
  listGroups: () => memberGroups.listGroups(),
  stationReadonly: false,
}

const config = useFieldsConfig(port)
const {
  birthDateField, dateFields, showFieldModal, editingField, loading, failure,
  saveField, showDeleteModal, deleteTarget, confirmDelete,
} = config
</script>

<template>
  <ViewContent
      :title="t('pages.station-members-config.title')"
      :subtitle="t('pages.station-members-config.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure"/>

      <FieldsWorkspace v-if="!loading" :config="config" :roles="STATION_ROLES"/>

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
