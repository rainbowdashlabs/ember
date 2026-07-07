/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'

defineProps<{
  label: string
  type?: string
  hint?: string
}>()

const value = defineModel<string>({required: true})
</script>

<template>
  <div>
    <FieldLabel class="mb-1">{{ label }}</FieldLabel>
    <TextInput v-model="value" :type="type"/>
    <p v-if="hint" class="text-xs text-(--text-muted) mt-1">{{ hint }}</p>
  </div>
</template>
