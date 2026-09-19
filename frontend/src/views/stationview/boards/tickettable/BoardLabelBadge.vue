/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseBadge from '@/components/badge/BaseBadge.vue'
import type {BoardLabel} from '@/api/boards'
import {contrastTextColor} from '@/theme/contrast'

/** One label of a board, in the colour the board gave it. */
defineProps<{
  label: BoardLabel
}>()
</script>

<template>
  <BaseBadge bg-class="" :style="{backgroundColor: label.color, color: contrastTextColor(label.color)}">{{ label.name }}</BaseBadge>
</template>
