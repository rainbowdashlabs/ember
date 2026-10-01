/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import DateInput from '@/components/input/datetime/DateInput.vue'

import type { DateAnswer } from '@/api/generated/schema'

const answer = defineModel<DateAnswer>({ required: true })
</script>

<template>
  <DateInput v-model="answer.date" />
</template>
