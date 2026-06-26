/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'

type LikertAnswer = { ratings: Record<string, number> }

const props = defineProps<{
  config: Record<string, unknown>
}>()

const answer = defineModel<LikertAnswer>({ required: true })

const statements = computed(() => (props.config.statements as string[]) || [])
const scaleMin = computed(() => props.config.scaleMin as number)
const scaleMax = computed(() => props.config.scaleMax as number)
const scaleLabels = computed(() => (props.config.scaleLabels as string[]) ?? [])
const scaleSteps = computed(() => scaleMax.value - scaleMin.value + 1)

function valueFor(n: number): number {
  return scaleMin.value + n - 1
}

function labelFor(n: number): string | number {
  return scaleLabels.value[scaleMin.value + n - 2] || valueFor(n)
}

function isSelected(stmtIndex: number, n: number): boolean {
  return answer.value.ratings?.[String(stmtIndex)] === valueFor(n)
}

function setRating(stmtIndex: number, n: number) {
  answer.value.ratings[String(stmtIndex)] = valueFor(n)
}
</script>

<template>
  <div>
    <div class="hidden sm:block overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
        <tr>
          <th></th>
          <th v-for="n in scaleSteps" :key="n" class="text-center px-2 py-1 text-xs text-(--text-muted)">
            {{ labelFor(n) }}
          </th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="(stmt, si) in statements" :key="si"
            class="border-t border-bg-light-accent/50 dark:border-bg-dark-accent/50">
          <td class="py-2 pr-4 text-sm">{{ stmt || `Option ${si + 1}` }}</td>
          <td v-for="n in scaleSteps" :key="n" class="text-center px-2 py-2">
            <SelectionToggleButton
                :selected="isSelected(si, n)"
                @toggle="setRating(si, n)">
              {{ valueFor(n) }}
            </SelectionToggleButton>
          </td>
        </tr>
        </tbody>
      </table>
    </div>
    <div class="sm:hidden space-y-3">
      <div v-for="(stmt, si) in statements" :key="si"
           class="p-3 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent space-y-2">
        <p class="text-sm font-medium">{{ stmt || `Option ${si + 1}` }}</p>
        <div class="flex flex-wrap gap-1">
          <SelectionToggleButton
              v-for="n in scaleSteps"
              :key="n"
              :selected="isSelected(si, n)"
              size="md"
              @toggle="setRating(si, n)">
            {{ valueFor(n) }}
          </SelectionToggleButton>
        </div>
      </div>
    </div>
  </div>
</template>
