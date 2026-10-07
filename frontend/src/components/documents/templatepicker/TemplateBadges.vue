/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import type {DocumentTemplateSummary} from '@/api/generated/schema'

/**
 * What a template is and which of its settings are on: its kind always, then legal, for appointments,
 * for self service, of the association and archived, each only where it applies.
 */
const props = defineProps<{
  template: DocumentTemplateSummary
}>()

const {t} = useI18n()

const settings = computed(() => [
  {on: props.template.legal, label: t('documentTemplates.badge.legal')},
  {on: props.template.forAppointments, label: t('documentTemplates.forAppointments')},
  {on: props.template.selfService, label: t('documentTemplates.tabSelfService')},
  {on: props.template.ofAssociation, label: t('documentTemplates.ofAssociation')},
  {on: props.template.archivedAt != null, label: t('documentTemplates.isArchived')},
].filter(setting => setting.on))
</script>

<template>
  <div class="flex flex-wrap gap-1" data-testid="template-badges">
    <PrimaryBadge>{{ t(`documentTemplates.kind.${template.kind}`) }}</PrimaryBadge>
    <SecondaryBadge v-for="setting in settings" :key="setting.label">{{ setting.label }}</SecondaryBadge>
  </div>
</template>
