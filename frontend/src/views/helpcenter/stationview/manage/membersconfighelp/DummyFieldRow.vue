/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import TRow from '@/components/table/TRow.vue'
import Td from '@/components/table/Td.vue'

defineProps<{
  name: string
  type: string
  required: boolean
  readonly: boolean
}>()
</script>

<template>
  <TRow>
    <Td>{{ name }}</Td>
    <Td class="text-(--text-muted)">{{ type }}</Td>
    <Td align="center"><ToggleInput :model-value="required" disabled/></Td>
    <Td align="center"><ToggleInput :model-value="readonly" disabled/></Td>
    <Td align="right">
      <div class="flex items-center justify-end gap-2">
        <EditButton disabled/>
        <DeleteButton disabled/>
      </div>
    </Td>
  </TRow>
</template>
