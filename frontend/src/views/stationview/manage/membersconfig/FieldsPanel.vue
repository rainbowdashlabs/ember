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
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldTable from './FieldTable.vue'
import TemplateButtons from './TemplateButtons.vue'
import type {FieldTemplate} from './fieldTemplates'
import type {ProfileField} from '@/api/profileFields'
import type {WritabilityName} from '@/composables/useFieldsConfig'

const props = defineProps<{
  activeTab: string
  fields: ProfileField[]
}>()

const emit = defineEmits<{
  (e: 'add'): void
  (e: 'edit', field: ProfileField): void
  (e: 'delete', field: ProfileField): void
  (e: 'reorder', fromIndex: number, toIndex: number): void
  (e: 'toggle-config', field: ProfileField, key: string, value: boolean): void
  (e: 'toggle-keep-on-archive', field: ProfileField, value: boolean): void
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

    <p class="text-sm text-(--text-muted)">
      <template v-if="props.activeTab === 'MEMBER'">{{ t('membersConfig.memberHint') }}</template>
      <template v-else-if="props.activeTab === 'GUARDIAN'">{{ t('membersConfig.guardianHint') }}</template>
      <template v-else-if="props.activeTab === 'TEAM'">{{ t('membersConfig.teamHint') }}</template>
      <template v-else-if="props.activeTab === 'MANAGER'">{{ t('membersConfig.stationManagerHint') }}</template>
      <template v-else>{{ t('membersConfig.groupHint') }}</template>
    </p>

    <div v-if="props.fields.length === 0" class="space-y-4">
      <EmptyState compact>{{ t('membersConfig.noFields') }}</EmptyState>
      <div class="space-y-2">
        <FieldLabel>{{ t('membersConfig.templates') }}</FieldLabel>
        <TemplateButtons @apply="(tpl: FieldTemplate) => emit('apply-template', tpl)"/>
      </div>
    </div>

    <FieldTable
        v-if="props.fields.length > 0"
        :fields="props.fields"
        @delete="(f: ProfileField) => emit('delete', f)"
        @edit="(f: ProfileField) => emit('edit', f)"
        @reorder="(from: number, to: number) => emit('reorder', from, to)"
        @toggle-config="(f: ProfileField, k: string, v: boolean) => emit('toggle-config', f, k, v)"
        @toggle-keep-on-archive="(f: ProfileField, v: boolean) => emit('toggle-keep-on-archive', f, v)"
        @set-writability="(f: ProfileField, level: WritabilityName) => emit('set-writability', f, level)"
    />

    <div v-if="props.fields.length > 0" class="pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
      <FieldLabel hint class="mb-2">{{ t('membersConfig.templates') }}</FieldLabel>
      <TemplateButtons @apply="(tpl: FieldTemplate) => emit('apply-template', tpl)"/>
    </div>
  </NeutralContainer>
</template>
