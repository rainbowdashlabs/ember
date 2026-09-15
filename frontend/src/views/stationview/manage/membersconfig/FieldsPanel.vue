/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldTable from './FieldTable.vue'
import TemplateButtons from './TemplateButtons.vue'
import BatchAssignBar from './BatchAssignBar.vue'
import type {FieldTemplate} from './fieldTemplates'
import type {AssignmentTarget, ProfileField} from '@/api/profileFields'
import type {MemberGroup} from '@/api/types'
import type {WritabilityName} from '@/composables/useFieldsConfig'

/**
 * The questions themselves, one row each, whoever ends up being asked them.
 *
 * <p>This used to be a tab per kind of member, and a question belonged to the tab it was written in.
 * Asking two kinds the same thing meant writing it twice, and the two copies then collected different
 * answers to what read as one question.
 */
const props = defineProps<{
  fields: ProfileField[]
  selectedId: number | null
  audienceCount: Record<number, number>
  /** The questions ticked for putting to somebody all at once. */
  checkedIds: Set<number>
  /** Every audience a batch may be put to, which is all of them rather than only the unasked ones. */
  roles: readonly string[]
  groups: MemberGroup[]
}>()

const emit = defineEmits<{
  (e: 'add'): void
  (e: 'select', field: ProfileField): void
  (e: 'toggle-checked', field: ProfileField): void
  (e: 'assign-checked', target: AssignmentTarget): void
  (e: 'clear-checked'): void
  (e: 'edit', field: ProfileField): void
  (e: 'delete', field: ProfileField): void
  (e: 'toggle-config', field: ProfileField, key: string, value: boolean): void
  (e: 'toggle-keep-on-archive', field: ProfileField, value: boolean): void
  (e: 'toggle-required', field: ProfileField, value: boolean): void
  (e: 'toggle-readonly', field: ProfileField, value: boolean): void
  (e: 'set-writability', field: ProfileField, level: WritabilityName): void
  (e: 'apply-template', tpl: FieldTemplate): void
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between">
      <SectionHeader>{{ t('membersConfig.fields') }}</SectionHeader>
      <PrimaryButton data-testid="field-add" :icon="['fas', 'plus']" @click="emit('add')">
        {{ t('membersConfig.addField') }}
      </PrimaryButton>
    </div>

    <MutedText tag="p" size="sm">{{ t('membersConfig.questionsHint') }}</MutedText>

    <BatchAssignBar
        v-if="props.checkedIds.size > 0"
        :count="props.checkedIds.size"
        :roles="props.roles"
        :groups="props.groups"
        @assign="(target: AssignmentTarget) => emit('assign-checked', target)"
        @clear="emit('clear-checked')"/>

    <div v-if="props.fields.length === 0" class="space-y-4">
      <EmptyState compact>{{ t('membersConfig.noFields') }}</EmptyState>
      <div class="space-y-2">
        <FieldLabel>{{ t('membersConfig.templates') }}</FieldLabel>
        <TemplateButtons @apply="(tpl: FieldTemplate) => emit('apply-template', tpl)"/>
      </div>
    </div>

    <FieldTable
        v-if="props.fields.length > 0"
        :audience-count="props.audienceCount"
        :fields="props.fields"
        :selected-id="props.selectedId"
        :checked-ids="props.checkedIds"
        @select="(f: ProfileField) => emit('select', f)"
        @toggle-checked="(f: ProfileField) => emit('toggle-checked', f)"
        @delete="(f: ProfileField) => emit('delete', f)"
        @edit="(f: ProfileField) => emit('edit', f)"
        @toggle-config="(f: ProfileField, k: string, v: boolean) => emit('toggle-config', f, k, v)"
        @toggle-keep-on-archive="(f: ProfileField, v: boolean) => emit('toggle-keep-on-archive', f, v)"
        @toggle-required="(f: ProfileField, v: boolean) => emit('toggle-required', f, v)"
        @toggle-readonly="(f: ProfileField, v: boolean) => emit('toggle-readonly', f, v)"
        @set-writability="(f: ProfileField, level: WritabilityName) => emit('set-writability', f, level)"
    />

    <div v-if="props.fields.length > 0" class="pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
      <FieldLabel hint class="mb-2">{{ t('membersConfig.templates') }}</FieldLabel>
      <TemplateButtons @apply="(tpl: FieldTemplate) => emit('apply-template', tpl)"/>
    </div>
  </NeutralContainer>
</template>
