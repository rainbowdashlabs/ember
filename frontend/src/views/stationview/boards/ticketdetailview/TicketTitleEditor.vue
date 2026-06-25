/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import TextInput from '@/components/input/text/TextInput.vue'

defineProps<{
    canEdit: boolean
}>()

const title = defineModel<string>('title', { default: '' })
const editing = defineModel<boolean>('editing', { default: false })

const emit = defineEmits<{
    save: []
}>()
</script>

<template>
    <TextInput v-if="editing && canEdit" v-model="title" borderless class="text-lg font-semibold" @blur="editing = false; emit('save')" @keydown.enter="($event.target as HTMLInputElement).blur()" />
    <div v-else class="text-lg font-semibold rounded-theme px-2 py-1" :class="canEdit ? 'cursor-pointer hover:bg-[var(--bg-accent)]' : ''" @click="canEdit && (editing = true)">{{ title }}</div>
</template>
