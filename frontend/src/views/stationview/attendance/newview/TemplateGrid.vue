/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {TemplateDetail} from '@/api/attendance'
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
  <div class="space-y-3">
    <SubHeader>{{ t('attendanceNew.fromTemplate') }}</SubHeader>
    <MutedText v-if="props.templates.length === 0" tag="div" size="sm" class="py-2">
      {{ t('attendanceNew.noTemplates') }}
    </MutedText>
    <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <NeutralContainer v-for="tpl in props.templates" :key="tpl.id"
                        :data-testid="`attendance-template-${tpl.id}`"
                        class="cursor-pointer hover:ring-2 hover:ring-primary/30 transition-all"
                        @click="emit('select', tpl.id)">
        <div class="space-y-2">
          <div class="flex items-center justify-between gap-2">
            <span class="font-medium">{{ tpl.name }}</span>
            <PrimaryButton :icon="['fas', 'plus']">
              {{ t('attendanceNew.create') }}
            </PrimaryButton>
          </div>

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
        </div>
      </NeutralContainer>
    </div>
  </div>
</template>
