/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import IconButton from '@/components/button/IconButton.vue'
import type {DocumentTemplateSummary} from '@/api/generated/schema'
import type {DataTableApi} from '@/composables/useDataTable'
import type {TemplateScreens} from '../templateScreens'
import {templateTarget} from './templateTarget'

/**
 * The templates of an owner, each name opening its editor. A template of the association, in a
 * station's list, opens the page where the station sets how it uses it, and is marked as the
 * association's. Every template can be duplicated from its row, an association's into one of the
 * station's own.
 */
defineProps<{
  table: DataTableApi<DocumentTemplateSummary>
  screens: TemplateScreens
  /** Whether a copy is being made, which holds the other duplicate buttons. */
  duplicating: boolean
}>()

const emit = defineEmits<{
  duplicate: [template: DocumentTemplateSummary]
}>()

const {t} = useI18n()
</script>

<template>
  <RecordTable :table="table" test-id="document-templates" row-test-id="document-template-row">
    <template #cell-name="{row}">
      <RowLink :to="templateTarget(row, screens)">
        <span class="font-semibold">{{ row.name }}</span>
        <SecondaryBadge v-if="row.ofAssociation" class="ml-2">{{ t('documentTemplates.ofAssociation') }}</SecondaryBadge>
      </RowLink>
    </template>
    <template #actions="{row}">
      <IconButton :icon="['fas', 'copy']" :label="t('documentTemplates.duplicate')" :disabled="duplicating"
                  data-testid="template-duplicate" @click="emit('duplicate', row)"/>
    </template>
    <template #empty>
      <EmptyState>{{ t('documentTemplates.empty') }}</EmptyState>
    </template>
  </RecordTable>
</template>
