/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {TestProtocolSection} from '@/api/generated/schema'
import type {RunPlan} from './useRunPlan'

/**
 * One section on the planning page: who examines it, who already does from a section above, and its
 * subsections below, each drawn by this same component one level deeper.
 */
const props = defineProps<{
  section: TestProtocolSection
  depth: number
  plan: RunPlan
}>()

const {t} = useI18n()

const inherited = computed(() => props.plan.inheritedNames(props.section))
const children = computed(() => props.plan.tree.value.childrenOf(props.section.id))
</script>

<template>
  <component
      :is="depth === 0 ? NeutralContainer : 'div'"
      :class="depth === 0 ? 'space-y-2' : `${depth <= 3 ? 'ml-4' : 'ml-1'} border-l-2 border-[var(--border)] pl-3 space-y-2`"
  >
    <div class="flex flex-wrap items-center gap-2">
      <SubHeader v-if="depth === 0">{{ section.name }}</SubHeader>
      <span v-else class="font-medium text-sm">{{ section.name }}</span>
      <MutedText class="ml-auto">{{ plan.tree.value.maxPointsUnder(section.id) }}P</MutedText>
    </div>
    <MemberSelectInput
        multiple
        :members="plan.candidateOptions.value"
        :selected="plan.ownOf(section.id)"
        :placeholder="t('protocol.plan.pickExaminers')"
        @update:selected="memberIds => plan.setOwn(section.id, memberIds)"
    />
    <MutedText v-if="inherited.length > 0" tag="p" size="sm">
      {{ t('protocol.plan.inherited', {names: inherited.join(', ')}) }}
    </MutedText>
    <PlanSectionNode v-for="child in children" :key="child.id" :section="child" :depth="depth + 1" :plan="plan"/>
  </component>
</template>
