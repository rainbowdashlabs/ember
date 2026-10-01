/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import { PageTargetKind } from '@/api/forms'
import type { PageTarget } from '@/api/generated/schema'
import type { PageChoice } from '../pageChoice'

/**
 * Where a reader goes from a page, or from one answer on it: on to the page below, to a chosen page,
 * or to the end, which sends the form.
 *
 * <p>Only pages further down are offered. A form can then never loop, every path ends, and nobody
 * has to be told their form goes round in circles.
 */
const props = defineProps<{
  /** The pages further down, the only ones a page may lead to. */
  further: PageChoice[]
  /** What the first entry says, which is "the next page" for a page and "otherwise" for an answer. */
  defaultLabel: string
}>()

const target = defineModel<PageTarget>({ required: true })

const { t } = useI18n()

const PAGE_PREFIX = 'page:'

const value = computed({
  get: () => (target.value.kind === PageTargetKind.PAGE ? `${PAGE_PREFIX}${target.value.page}` : target.value.kind),
  set: (chosen: string) => {
    if (chosen.startsWith(PAGE_PREFIX)) target.value = { kind: PageTargetKind.PAGE, page: chosen.slice(PAGE_PREFIX.length) }
    else target.value = { kind: Object.values(PageTargetKind).find(kind => kind === chosen) ?? PageTargetKind.NEXT }
  },
})

function onPick(chosen: string | number | null | undefined) {
  value.value = String(chosen ?? PageTargetKind.NEXT)
}
</script>

<template>
  <SelectInput :model-value="value" @update:model-value="onPick">
    <option :value="PageTargetKind.NEXT">{{ defaultLabel }}</option>
    <option v-for="page in props.further" :key="page.key" :value="`${PAGE_PREFIX}${page.key}`">
      {{ page.label }}
    </option>
    <option :value="PageTargetKind.SUBMIT">{{ t('forms.pages.submit') }}</option>
  </SelectInput>
</template>
