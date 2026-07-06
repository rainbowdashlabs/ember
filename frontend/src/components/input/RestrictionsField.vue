/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import type {RestrictionSelection} from '@/components/input/restriction'
import type {MemberGroup, UserTag} from '@/api/types'

defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
}>()

const model = defineModel<RestrictionSelection>({required: true})
</script>

<template>
  <RestrictionPicker
      :groups="groups"
      :tags="tags"
      v-model="model"
  />
</template>
