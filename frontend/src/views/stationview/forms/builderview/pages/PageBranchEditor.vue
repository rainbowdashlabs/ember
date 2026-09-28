/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PageAfterSelect from './PageAfterSelect.vue'
import { PageTargetKind, type PageTarget } from '@/api/forms'
import { optionsOf } from '@/util/formOptions'
import { canDecide, type FormLayoutEditor } from '../useFormLayout'
import type { PageChoice } from '../pageChoice'

/**
 * Lets an answer decide where a page leads: pick one question of the page that takes a single
 * answer from its options, then a page for each option. An option left at "otherwise" follows the
 * page's own "after this page".
 *
 * <p>A small table rather than a rule builder. With one deciding question per page and one kind of
 * condition, nothing more is needed, and this covers the cases a club actually has: coming or not,
 * adult or child.
 */
const props = defineProps<{
  layout: FormLayoutEditor
  pageIndex: number
  /** The pages further down, the only ones an answer may lead to. */
  further: PageChoice[]
}>()

const { t } = useI18n()

const NONE = ''

const page = computed(() => props.layout.pages.value[props.pageIndex]!)
const candidates = computed(() => page.value.questions.filter(canDecide))
const deciding = computed(() => page.value.questions.find(question => question.branch && canDecide(question)) ?? null)

const decidingId = computed({
  get: () => deciding.value?.id ?? NONE,
  set: (id: string) => props.layout.setDeciding(props.pageIndex, id === NONE ? null : id),
})

function onPick(value: string | number | null | undefined) {
  decidingId.value = String(value ?? NONE)
}

function titleOf(id: string): string {
  const question = page.value.questions.find(candidate => candidate.id === id)
  return question?.title.trim() || t('forms.branch.untitled', {number: question ? props.layout.numberOf(question) : 0})
}

function targetOf(optionKey: string): PageTarget {
  return deciding.value?.branch?.[optionKey] ?? { kind: PageTargetKind.NEXT, page: null }
}

function setTarget(optionKey: string, target: PageTarget) {
  const branch = deciding.value?.branch
  if (!branch) return
  if (target.kind === PageTargetKind.NEXT) delete branch[optionKey]
  else branch[optionKey] = target
}
</script>

<template>
  <div v-if="candidates.length > 0" class="space-y-2" data-testid="page-branch">
    <FieldLabel class="space-y-1">
      {{ t('forms.branch.question') }}
      <SelectInput :model-value="decidingId" @update:model-value="onPick">
        <option :value="NONE">{{ t('forms.branch.none') }}</option>
        <option v-for="question in candidates" :key="question.id" :value="question.id">{{ titleOf(question.id) }}</option>
      </SelectInput>
    </FieldLabel>
    <div v-if="deciding" class="grid grid-cols-1 gap-2 sm:grid-cols-2">
      <template v-for="option in optionsOf(deciding.config)" :key="option.key">
        <span class="self-center text-sm">{{ option.label || t('forms.branch.unnamedOption') }}</span>
        <PageAfterSelect :model-value="targetOf(option.key)" :further="further"
                         :default-label="t('forms.branch.otherwise')"
                         @update:model-value="setTarget(option.key, $event)"/>
      </template>
    </div>
  </div>
</template>
