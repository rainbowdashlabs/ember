/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import StartTile from '@/components/start/StartTile.vue'
import StartTileSection from '@/components/start/StartTileSection.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {TemplateDetail} from '@/api/generated/schema'
import {StationUserTypeLabels} from '@/api/types'

/**
 * The templates a sheet can be started from, each saying whom it enters and what it asks, so the
 * choice is not made from memory. One that enters nobody says so.
 */
const props = defineProps<{
  templates: TemplateDetail[]
  /** What a group is called, since a template names only the group's number. */
  groupName: (groupId: number) => string
}>()

const emit = defineEmits<{
  (e: 'select', templateId: number): void
}>()

const {t} = useI18n()

function fieldNames(template: TemplateDetail): string[] {
  return (template.fields ?? []).map(field => field.name ?? '').filter(name => name.length > 0)
}

/** Whom the template enters, groups first and then its user types, the way its sheets are written. */
function enteredNames(template: TemplateDetail): string[] {
  const groups = (template.groups ?? []).map(group => props.groupName(group.groupId))
  const types = (template.userTypes ?? []).map(type => StationUserTypeLabels[type])
  return [...groups, ...types].filter(name => name.length > 0)
}
</script>

<template>
  <StartTileSection :title="t('attendanceNew.fromTemplate')"
                    :empty-note="props.templates.length === 0 ? t('attendanceNew.noTemplates') : null">
    <StartTile v-for="tpl in props.templates" :key="tpl.id"
               :data-testid="`attendance-template-${tpl.id}`"
               :title="tpl.name" :button-label="t('attendanceNew.create')"
               @start="emit('select', tpl.id)">
      <MutedText tag="p" size="sm">
        {{ enteredNames(tpl).length > 0
          ? t('attendanceNew.entersGroups', {groups: enteredNames(tpl).join(', ')})
          : t('attendanceNew.entersNobody') }}
      </MutedText>

      <MutedText tag="p" size="sm">
        {{ fieldNames(tpl).length > 0
          ? t('attendanceNew.asksFor', {fields: fieldNames(tpl).join(', ')})
          : t('attendanceNew.asksNothing') }}
      </MutedText>
    </StartTile>
  </StartTileSection>
</template>
