/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ItemChip from '@/components/inventory/ItemChip.vue'
import {glyphFor} from '@/util/glyph'
import type {MovementPurposeName, StepActorName} from '@/api/movements'

/**
 * One row of the queue, drawn the way the real one is drawn: the piece, the purpose, who it is with,
 * the step it stands on and whose turn that is.
 */
const props = defineProps<{
  managerView: boolean
  member: string
  purpose: MovementPurposeName
  icon: string
  color: string
  item: string
  sizeName?: string
  step: string
  actor: StepActorName
  date: string
  ours?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="grid gap-2 sm:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,1fr)_auto]">
    <div class="flex flex-wrap items-center gap-2">
      <ItemChip :source="{glyph: glyphFor({icon: props.icon, color: props.color}), name: props.item, sizeName: props.sizeName}"/>
      <PrimaryBadge>{{ t(`movements.purpose.${props.purpose}`) }}</PrimaryBadge>
    </div>

    <div v-if="props.managerView" class="min-w-0 truncate text-sm">{{ props.member }}</div>
    <div v-else></div>

    <div class="flex flex-wrap items-center gap-2 text-sm">
      <InfoBadge>{{ props.step }}</InfoBadge>
      <SecondaryBadge>{{ t(`movements.actor.${props.actor}`) }}</SecondaryBadge>
    </div>

    <div class="flex flex-wrap items-center justify-end gap-2">
      <PrimaryButton v-if="props.ours" class="text-xs">{{ t('movements.queue.acknowledge') }}</PrimaryButton>
      <SecondaryButton v-if="props.managerView" class="text-xs">{{ t('movements.queue.correct') }}</SecondaryButton>
      <SecondaryButton class="text-xs">{{ t('movements.queue.openDetail') }}</SecondaryButton>
      <MutedText size="sm">{{ props.date }}</MutedText>
    </div>
  </NeutralContainer>
</template>
