/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import PitchBadge from './PitchBadge.vue'
import type {PitchListRow} from './pitchTypes'

/**
 * The entry list of the application: the name as a primary link, the muted meta beside it, the
 * overview fields underneath and the summary badges on the right.
 */
defineProps<{
  rows: PitchListRow[]
}>()
</script>

<template>
  <component :is="row.highlight ? PrimaryContainer : NeutralContainer" v-for="row in rows" :key="row.name">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div class="min-w-0">
        <span v-if="row.prefix" class="mr-2 rounded bg-(--bg-accent) px-1.5 py-0.5 font-mono text-xs text-(--text-muted)">
          {{ row.prefix }}
        </span>
        <span class="font-medium" :class="row.plain ? '' : 'text-primary'">{{ row.name }}</span>
        <MutedIcon v-if="row.locked" :icon="['fas', 'lock']" class="ml-1"/>
        <StationBadge v-if="row.station" :station-name="row.station" class="ml-2"/>
        <MutedText v-for="meta in row.meta ?? []" :key="meta" size="sm" class="ml-2">{{ meta }}</MutedText>
        <MutedText v-if="row.description" tag="div" size="sm">{{ row.description }}</MutedText>
        <div v-if="row.fields?.length" class="mt-1 flex flex-wrap gap-3 text-xs">
          <span v-for="field in row.fields" :key="field.label" class="text-(--text-muted)">
            <span class="font-medium">{{ field.label }}:</span> {{ field.value }}
          </span>
        </div>
      </div>
      <div class="flex items-center gap-2 text-xs">
        <span v-if="row.trailing" class="text-(--text-muted)">{{ row.trailing }}</span>
        <span v-if="row.score" class="font-mono text-sm">{{ row.score }}</span>
        <PitchBadge v-for="badge in row.badges ?? []" :key="badge.text" :tone="badge.tone">{{ badge.text }}</PitchBadge>
        <font-awesome-icon v-if="row.favourite !== undefined" :icon="['fas', 'star']"
                           :class="row.favourite ? 'text-yellow-500' : 'text-(--text-muted)'"/>
        <PrimaryButton v-if="row.action" compact :icon="['fas', row.actionIcon ?? 'clipboard-check']">
          {{ row.action }}
        </PrimaryButton>
      </div>
    </div>
  </component>
</template>
