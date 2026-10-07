/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import PagePager from '@/components/documents/PagePager.vue'
import type {DocumentTemplateSummary, TemplatePage} from '@/api/generated/schema'

/**
 * One page of templates as a grid of tiles, with the pager below where there is more than one page.
 * Each tile comes from the `tile` slot, so the list and the picker decide what pressing one does.
 */
const page = defineModel<number>('page', {required: true})

const props = defineProps<{
  result: TemplatePage | null
  loading: boolean
  pageCount: number
  /** What to say where nothing matches. */
  emptyText: string
}>()

defineSlots<{
  tile(props: {template: DocumentTemplateSummary}): unknown
}>()

const {t} = useI18n()

const shownPage = computed({
  get: () => page.value + 1,
  set: value => { page.value = value - 1 },
})
</script>

<template>
  <div class="space-y-3">
    <Spinner v-if="props.loading && !props.result" size="lg"/>
    <EmptyState v-else-if="props.result && props.result.items.length === 0">{{ emptyText }}</EmptyState>
    <div v-else :class="{'opacity-60': props.loading}" class="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4"
         data-testid="template-grid">
      <template v-for="template in props.result?.items ?? []" :key="template.id">
        <slot name="tile" :template="template"/>
      </template>
    </div>
    <PagePager v-if="pageCount > 1" v-model:page="shownPage" :page-count="pageCount" class="justify-center"
               :label="t('documentTemplates.pageOf', {page: shownPage, count: pageCount})"/>
  </div>
</template>
