/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {fieldTemplates, type FieldTemplate} from './fieldTemplates'

const emit = defineEmits<{
  (e: 'apply', tpl: FieldTemplate): void
}>()
</script>

<template>
  <div class="flex flex-wrap gap-2">
    <SecondaryButton
        v-for="tpl in fieldTemplates"
        :key="tpl.name"
        :icon="['fas', tpl.icon]"
        @click="emit('apply', tpl)"
    >
      {{ tpl.name }}
    </SecondaryButton>
  </div>
</template>
