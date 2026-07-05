/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import type {MemberGroup, UserTag} from '@/api/types'

defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
  roles?: unknown[]
}>()

const selectedUserTypes = defineModel<string[]>('selectedUserTypes', {required: true})
const selectedGroupIds = defineModel<number[]>('selectedGroupIds', {required: true})
const selectedTagIds = defineModel<number[]>('selectedTagIds', {required: true})
const mode = defineModel<'AND' | 'OR'>('mode', {default: 'AND'})
</script>

<template>
  <RestrictionPicker
      :roles="roles"
      :groups="groups"
      :tags="tags"
      v-model:selected-user-types="selectedUserTypes"
      v-model:selected-group-ids="selectedGroupIds"
      v-model:selected-tag-ids="selectedTagIds"
      v-model:mode="mode"
  />
</template>
