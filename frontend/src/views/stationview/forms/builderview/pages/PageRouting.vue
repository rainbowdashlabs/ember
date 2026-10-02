/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PageAfterSelect from './PageAfterSelect.vue'
import PageBranchEditor from './PageBranchEditor.vue'
import { pageChoices } from '../pageChoice'
import type { FormLayoutEditor } from '../useFormLayout'

/** Where a page of a form with several pages leads: the page after it, and per answer where one decides. */
const props = defineProps<{
  layout: FormLayoutEditor
  pageIndex: number
}>()

const { t } = useI18n()

const page = computed(() => props.layout.pages.value[props.pageIndex]!)
const lastPage = computed(() => props.pageIndex === props.layout.pages.value.length - 1)
const further = computed(() => pageChoices(props.layout.pages.value, t).filter(choice => choice.index > props.pageIndex))
</script>

<template>
  <div class="space-y-3 rounded-theme border border-dashed border-(--border) p-3">
    <FieldLabel class="space-y-1">
      {{ t('forms.pages.after') }}
      <PageAfterSelect v-model="page.after" :further="further"
                       :default-label="lastPage ? t('forms.pages.submit') : t('forms.pages.next')"/>
    </FieldLabel>
    <PageBranchEditor :layout="layout" :page-index="pageIndex" :further="further"/>
  </div>
</template>
