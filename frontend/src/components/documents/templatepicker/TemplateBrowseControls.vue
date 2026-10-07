/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SearchInput from '@/components/input/text/SearchInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {DocumentTemplateKind, TemplateSort} from '@/api/generated/schema'

/**
 * Searching templates by name, narrowing them to a kind and choosing their order. The server does all
 * three over every template.
 */
const search = defineModel<string>('search', {required: true})
const kind = defineModel<DocumentTemplateKind | null>('kind', {required: true})
const sort = defineModel<TemplateSort>('sort', {required: true})

const SORTS: readonly TemplateSort[] = [TemplateSort.LAST_USED, TemplateSort.NAME, TemplateSort.UPDATED, TemplateSort.CREATED]

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-wrap items-center gap-2">
    <SearchInput v-model="search" class="min-w-48 flex-1" :placeholder="t('documentTemplates.browse.search')"
                 :aria-label="t('documentTemplates.browse.search')" data-testid="template-search"/>
    <SelectInput v-model="kind" :aria-label="t('documentTemplates.kindColumn')" data-testid="template-kind">
      <option :value="null">{{ t('documentTemplates.browse.allKinds') }}</option>
      <option v-for="value in Object.values(DocumentTemplateKind)" :key="value" :value="value">
        {{ t(`documentTemplates.kind.${value}`) }}
      </option>
    </SelectInput>
    <SelectInput v-model="sort" :aria-label="t('documentTemplates.browse.sort')" data-testid="template-sort">
      <option v-for="value in SORTS" :key="value" :value="value">
        {{ t(`documentTemplates.browse.sortBy.${value}`) }}
      </option>
    </SelectInput>
  </div>
</template>
