/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import MemberInventoryLink from '@/components/inventory/MemberInventoryLink.vue'
import type {Movement} from '@/api/movements'

/** Who a movement is with, leading to what they hold, or the words for the station's store. */
const props = defineProps<{
  movement: Movement
  /** The words the cell has without a member, which is the store. */
  text: string
}>()
</script>

<template>
  <MemberInventoryLink
      v-if="props.movement.memberIdentity"
      :identity="props.movement.memberIdentity"
      :member-id="props.movement.memberId"
  />
  <span v-else class="text-(--text-muted)">{{ props.text }}</span>
</template>
