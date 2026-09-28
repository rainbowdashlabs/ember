/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import MutedIcon from '@/components/display/MutedIcon.vue'
import FieldValueDisplay from '@/components/display/FieldValueDisplay.vue'
import type {ProfileFieldChange} from '@/api/profileFieldChanges'

/**
 * The value a field had before a change and the one it has after, each written the way the field
 * itself is read on the profile, so a date reads as a date rather than as it is stored.
 *
 * <p>A birth date is written without the age behind it: the age is today's, and next to a value
 * from the past it would say something the change never did.
 */
defineProps<{
  change: ProfileFieldChange
}>()

const WITHOUT_AGE = {showAge: false}

/** The stored answer as the field holds it, or the stored text itself where it is no JSON. */
function decoded(val?: string): unknown {
  if (!val) return null
  try { return JSON.parse(val) } catch { return val }
}
</script>

<template>
  <div class="flex items-center gap-2 text-xs">
    <span data-testid="change-side" class="text-(--text-muted)">
      <FieldValueDisplay :value="decoded(change.oldValue)" :field-type="change.fieldType ?? undefined" :config="WITHOUT_AGE"/>
    </span>
    <MutedIcon :icon="['fas', 'chevron-right']"/>
    <span data-testid="change-side" class="font-medium">
      <FieldValueDisplay :value="decoded(change.newValue)" :field-type="change.fieldType ?? undefined" :config="WITHOUT_AGE"/>
    </span>
  </div>
</template>
