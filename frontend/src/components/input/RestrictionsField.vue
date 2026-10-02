/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import type {RestrictionSelection} from '@/api/types'
import type {MemberGroup, UserTag} from '@/api/generated/schema'

defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
}>()

const model = defineModel<RestrictionSelection>({required: true})
</script>

<template>
  <RestrictionPicker
      v-model="model"
      :groups="groups"
      :tags="tags"
  />
</template>
