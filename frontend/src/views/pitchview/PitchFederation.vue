/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import type {PitchFederation} from './pitchTypes'

/** The capability matrix of one partner: a row per feature, a switch per direction. */
defineProps<{
  federation: PitchFederation
}>()
</script>

<template>
  <NeutralContainer>
    <table class="w-full text-sm">
      <thead>
        <tr class="border-b border-(--border)">
          <th class="px-3 py-2 text-left font-medium">Funktion</th>
          <Th align="center" class="w-32 font-medium">Empfangen</Th>
          <Th align="center" class="w-32 font-medium">Senden</Th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in federation.capabilities" :key="row.label"
            class="border-b border-(--border) last:border-b-0">
          <Td>{{ row.label }}</Td>
          <Td align="center"><ToggleInput :model-value="row.receive"/></Td>
          <Td align="center"><ToggleInput :model-value="row.send"/></Td>
        </tr>
      </tbody>
    </table>
  </NeutralContainer>
</template>
