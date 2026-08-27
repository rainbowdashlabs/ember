/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldValueDisplay from '@/components/display/FieldValueDisplay.vue'
import type {LaidOutField} from './ProfileFieldsLayout.vue'
import {isSection, spanClass} from './fieldLayout'

/**
 * The fields of a member as they are read rather than filled in, arranged the way the station
 * arranged them.
 *
 * <p>The reading half of {@link ProfileFieldsLayout} and laid out by the same rules, because a
 * profile that reads in a different order from the one it is written in is a different profile as
 * far as the reader is concerned. Headings appear where they were put and short fields stand beside
 * each other, neither of which a plain list of name and value can do.
 */
defineProps<{
  fields: LaidOutField[]
  getValue: (field: LaidOutField) => unknown
}>()
</script>

<template>
  <div class="grid grid-cols-6 gap-x-4 gap-y-2 items-start">
    <template v-for="field in fields" :key="`${field.origin ?? 'STATION'}-${field.id}`">
      <div v-if="isSection(field)" data-testid="field-section" :class="spanClass(field)" class="pt-2 first:pt-0">
        <SubHeader class="text-sm">{{ field.name }}</SubHeader>
      </div>
      <div v-else data-testid="field-entry" :data-field="field.name" :class="spanClass(field)" class="text-sm">
        <MutedText>{{ field.name }}:</MutedText>
        <span class="ml-1 font-medium">
          <FieldValueDisplay :value="getValue(field)" :field-type="field.fieldType"/>
        </span>
      </div>
    </template>
  </div>
</template>
