/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ProfileFieldsDisplay from '@/components/profilefields/ProfileFieldsDisplay.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type { ProfileQuestion } from '@/api/profileFields'
import type { MemberWithName } from '@/api/generated/schema'

/**
 * One person linked to the member, with their answers and, where the reader may change the link,
 * the buttons to open or unlink them.
 *
 * <p>A guardian carries their place in the order as a badge, because the first guardian is the one
 * documents name as guardian 1.
 */
defineProps<{
  person: MemberWithName
  readonly: boolean
  /** The badge naming the person's place, or nothing where the order means nothing. */
  place?: string
  rowTestid?: string
  displayName: (m: MemberWithName) => string
  fields: ProfileQuestion[]
  fieldValue: (fieldId: number) => unknown
}>()

const emit = defineEmits<{
  edit: [id: number]
  remove: [id: number]
}>()
</script>

<template>
  <div :data-testid="rowTestid" class="rounded-lg px-4 py-3 bg-bg-light-accent/30 dark:bg-bg-dark-accent/30 space-y-2">
    <div class="flex items-center justify-between gap-2">
      <div class="flex flex-wrap items-center gap-2">
        <span class="font-semibold">{{ displayName(person) }}</span>
        <SecondaryBadge v-if="place" data-testid="guardian-place">{{ place }}</SecondaryBadge>
        <MutedText v-if="person.email">{{ person.email }}</MutedText>
      </div>
      <div v-if="!readonly" class="flex items-center gap-2">
        <EditButton @click="emit('edit', person.id)" />
        <DeleteButton @click="emit('remove', person.id)" />
      </div>
    </div>
    <ProfileFieldsDisplay
        v-if="fields.length > 0"
        :fields="fields"
        :get-value="field => fieldValue(field.id)"
    />
  </div>
</template>
