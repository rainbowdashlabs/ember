/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TabBar from '@/components/navigation/TabBar.vue'
import FieldsPanel from './FieldsPanel.vue'
import AudiencesPanel from './AudiencesPanel.vue'
import FieldsPreview from './FieldsPreview.vue'
import type {AskedField} from './askedField'
import type {FieldTemplate} from './fieldTemplates'
import type {ProfileField} from '@/api/profileFields'
import type {useFieldsConfig} from '@/composables/useFieldsConfig'

/**
 * The field editor, wired to a configuration.
 *
 * <p>Above, the two halves of the model: the questions, and who the selected one is put to. Below, one
 * audience at a time, the order their form stands in and what it looks like filled.
 *
 * <p>The same screen for a station and for an association, because the difference between them is
 * already expressed as the port the configuration was built from.
 */
const props = defineProps<{
  config: ReturnType<typeof useFieldsConfig>
  /** The kinds of member whose forms may be looked at, which an association has fewer of. */
  roles: readonly string[]
}>()

const {t} = useI18n()

const c = props.config

const tabs = computed(() => props.roles.map(role => ({key: role, label: t(`membersConfig.roles.${role}`)})))

/** How many audiences each question reaches, which is what a question asked of nobody is spotted by. */
const audienceCount = computed(() => c.allAssignments.value.reduce<Record<number, number>>((counts, assignment) => {
  counts[assignment.fieldId] = (counts[assignment.fieldId] ?? 0) + 1
  return counts
}, {}))
</script>

<template>
  <div class="space-y-6">
    <div class="grid gap-6 lg:grid-cols-2 items-start">
      <FieldsPanel
          :audience-count="audienceCount"
          :fields="c.questions.value"
          :selected-id="c.selectedFieldId.value"
          :checked-ids="c.checkedIds.value"
          :roles="props.roles"
          :groups="c.availableGroups.value"
          @add="c.openAddField"
          @select="(f: ProfileField) => c.select(f.id)"
          @toggle-checked="(f: ProfileField) => c.toggleChecked(f.id)"
          @assign-checked="c.assignCheckedTo"
          @clear-checked="c.clearChecked"
          @edit="c.openEditField"
          @delete="c.requestDelete"
          @toggle-config="c.toggleFieldConfig"
          @toggle-keep-on-archive="c.toggleKeepOnArchive"
          @toggle-required="c.toggleRequired"
          @toggle-readonly="c.toggleReadonly"
          @set-writability="c.setWritability"
          @apply-template="(tpl: FieldTemplate) => c.applyTemplate(tpl, c.previewRole.value)"
      />

      <AudiencesPanel
          :audiences="c.audiences.value"
          :field="c.selectedField.value"
          :unasked-groups="c.unaskedGroups.value"
          :unasked-roles="c.unaskedRoles.value"
          @add="c.addAudience"
          @remove="c.removeAudience"
          @set="c.setAudience"
      />
    </div>

    <TabBar v-model="c.previewRole.value" :tabs="tabs"/>

    <FieldsPreview
        :fields="c.previewFields.value"
        @reorder="(from: number, to: number) => c.onReorder(c.previewRole.value, from, to)"
        @resize="(field: AskedField, width: string) => c.setPreviewWidth(field, width)"/>
  </div>
</template>
