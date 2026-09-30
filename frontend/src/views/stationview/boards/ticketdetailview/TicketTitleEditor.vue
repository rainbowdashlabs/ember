/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import TextInput from '@/components/input/text/TextInput.vue'

const props = defineProps<{
    canEdit: boolean
}>()

const title = defineModel<string>('title', { default: '' })
const editing = defineModel<boolean>('editing', { default: false })

const emit = defineEmits<{
    save: []
}>()

/** Turns the title into a field, for whoever may change it. */
function startEditing() {
    if (props.canEdit) editing.value = true
}
</script>

<template>
    <TextInput v-if="editing && canEdit" v-model="title" borderless class="text-lg font-semibold" @blur="editing = false; emit('save')" @keydown.enter="($event.target as HTMLInputElement).blur()" />
    <div
        v-else
        class="text-lg font-semibold rounded-theme px-2 py-1"
        :class="canEdit ? 'cursor-pointer hover:bg-[var(--bg-accent)]' : ''"
        role="button"
        tabindex="0"
        :aria-disabled="!canEdit"
        @click="startEditing"
        @keydown.enter.prevent="startEditing"
        @keydown.space.prevent="startEditing"
    >{{ title }}</div>
</template>
