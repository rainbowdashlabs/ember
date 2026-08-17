/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import ProcedureItemRow from '@/views/stationview/procedure/proceduredetailview/ProcedureItemRow.vue'
import type {PitchProcedure} from './pitchTypes'

/** A running procedure: who it belongs to, how far it has come, and its steps. */
const props = defineProps<{
  procedure: PitchProcedure
}>()

const checked = computed(() => props.procedure.items.filter(item => item.checked).length)
const percent = computed(() => Math.round(checked.value / props.procedure.items.length * 100))
const blocked = (id: number) => props.procedure.blockedBy[id] ?? []
</script>

<template>
  <NeutralContainer>
    <SubHeader class="mb-2">Zugewiesene</SubHeader>
    <div class="flex flex-wrap gap-2">
      <MemberName v-for="assignee in procedure.assignees" :key="assignee.memberUid ?? ''"
                  :identity="assignee" size="sm"/>
    </div>
  </NeutralContainer>

  <NeutralContainer>
    <div class="flex items-center gap-3">
      <span class="text-sm font-medium">Fortschritt</span>
      <div class="h-2 flex-1 overflow-hidden rounded-full bg-bg-light-accent dark:bg-bg-dark-accent">
        <div class="h-full rounded-full bg-success" :style="{width: `${percent}%`}"/>
      </div>
      <span class="text-sm text-(--text-muted)">{{ checked }}/{{ procedure.items.length }}</span>
    </div>
  </NeutralContainer>

  <div class="space-y-2">
    <ProcedureItemRow
        v-for="item in procedure.items" :key="item.id"
        :item="item" :can-edit="false" :can-check="!item.checked && blocked(item.id).length === 0"
        :dependency-met="blocked(item.id).length === 0" :dependency-names="blocked(item.id)"/>
  </div>
</template>
