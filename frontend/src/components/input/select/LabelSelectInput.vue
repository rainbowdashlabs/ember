/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { BoardLabel } from '@/api/boards'

const props = defineProps<{
    labels: BoardLabel[]
    selected: BoardLabel[]
    disabled?: boolean
}>()

const emit = defineEmits<{
    toggle: [labelId: number]
    create: [name: string]
}>()

const search = ref('')
const open = ref(false)
const containerRef = ref<HTMLElement | null>(null)
const searchInputRef = ref<HTMLInputElement | null>(null)

const filtered = computed(() => {
    if (!search.value) return props.labels
    const q = search.value.toLowerCase()
    return props.labels.filter(l => l.name.toLowerCase().includes(q))
})

const canCreate = computed(() => {
    if (!search.value.trim()) return false
    return !props.labels.some(l => l.name.toLowerCase() === search.value.trim().toLowerCase())
})

const selectedIds = computed(() => new Set(props.selected.map(l => l.id)))

function toggle(id: number) { emit('toggle', id); search.value = '' }
function createLabel() { if (canCreate.value) { emit('create', search.value.trim()); search.value = '' } }

function handleClickOutside(e: MouseEvent) { if (containerRef.value && !containerRef.value.contains(e.target as Node)) open.value = false }
watch(open, (isOpen) => { if (isOpen) nextTick(() => searchInputRef.value?.focus()) })
onMounted(() => document.addEventListener('click', handleClickOutside))
onBeforeUnmount(() => document.removeEventListener('click', handleClickOutside))
</script>

<template>
    <div ref="containerRef" class="relative">
        <div
            class="flex flex-wrap items-center gap-1 min-h-[2rem] px-2 py-1 rounded-theme bg-transparent cursor-pointer transition-colors hover:bg-[var(--bg-accent)]"
            :class="{ 'opacity-50 pointer-events-none': disabled }"
            @click.stop="open = !open"
        >
            <span v-for="label in selected" :key="label.id" class="text-xs px-2 py-0.5 rounded-full text-white inline-flex items-center gap-1" :style="{ backgroundColor: label.color }">
                {{ label.name }}
                <span class="opacity-70 cursor-pointer" @click.stop="toggle(label.id)">x</span>
            </span>
            <span v-if="selected.length === 0" class="text-sm text-(--text-muted)">Labels...</span>
        </div>
        <div v-if="open" class="absolute z-20 mt-1 w-full rounded-theme border border-[var(--border)] bg-[var(--bg)] shadow-lg overflow-hidden max-h-48 overflow-y-auto">
            <div class="p-2 border-b border-[var(--border)]">
                <input ref="searchInputRef" v-model="search" type="text" placeholder="Suchen oder erstellen..." class="w-full text-sm bg-transparent outline-none" @keydown.enter="canCreate ? createLabel() : undefined" />
            </div>
            <div v-for="label in filtered" :key="label.id" class="px-3 py-1.5 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2" @click="toggle(label.id)">
                <span class="w-3 h-3 rounded-full shrink-0" :style="{ backgroundColor: label.color }" />
                <span class="flex-1">{{ label.name }}</span>
                <font-awesome-icon v-if="selectedIds.has(label.id)" :icon="['fas', 'check']" class="text-xs text-primary" />
            </div>
            <div v-if="canCreate" class="px-3 py-1.5 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2 border-t border-[var(--border)]" @click="createLabel">
                <font-awesome-icon :icon="['fas', 'plus']" class="text-xs text-primary" />
                <span>„{{ search.trim() }}" erstellen</span>
            </div>
            <p v-else-if="filtered.length === 0" class="px-3 py-2 text-xs text-(--text-muted)">Keine Labels gefunden</p>
        </div>
    </div>
</template>
