/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import type { MemberCompletion } from '@/api/stationMembers'

const model = defineModel<string>({ default: '' })

const props = defineProps<{
    members: MemberCompletion[]
    placeholder?: string
    disabled?: boolean
    autoOpen?: boolean
}>()

const emit = defineEmits<{
    change: []
}>()

const search = ref('')
const open = ref(false)
const containerRef = ref<HTMLElement | null>(null)
const searchInputRef = ref<HTMLInputElement | null>(null)

watch(open, (isOpen) => {
    if (isOpen) {
        nextTick(() => searchInputRef.value?.focus())
    }
})

const filteredMembers = computed(() => {
    if (!search.value) return props.members
    const q = search.value.toLowerCase()
    return props.members.filter(m => m.name.toLowerCase().includes(q))
})

const selectedMember = computed(() => {
    if (!model.value) return null
    return props.members.find(m => m.id === Number(model.value)) ?? null
})

function select(memberId: number | null) {
    model.value = memberId?.toString() ?? ''
    open.value = false
    search.value = ''
    emit('change')
}

function handleClickOutside(e: MouseEvent) {
    if (containerRef.value && !containerRef.value.contains(e.target as Node)) {
        open.value = false
    }
}

onMounted(() => {
    document.addEventListener('click', handleClickOutside)
    if (props.autoOpen) {
        open.value = true
    }
})
onBeforeUnmount(() => document.removeEventListener('click', handleClickOutside))
</script>

<template>
    <div ref="containerRef" class="relative">
        <button
            type="button"
            :disabled="disabled"
            class="w-full flex items-center gap-2 rounded-theme border border-[var(--border)] bg-[var(--bg)] px-3 py-2 text-sm text-left transition-colors hover:border-primary disabled:opacity-50"
            @click="open = !open"
        >
            <template v-if="selectedMember">
                <UserAvatar :identity="selectedMember" :name="selectedMember.name" size="sm" />
                <span class="flex-1">{{ selectedMember.name }}</span>
            </template>
            <span v-else class="flex-1 text-(--text-muted)">{{ placeholder ?? '-' }}</span>
            <font-awesome-icon :icon="['fas', 'chevron-down']" class="text-xs text-(--text-muted)" />
        </button>
        <div v-if="open" class="absolute z-20 mt-1 w-full rounded-theme border border-[var(--border)] bg-[var(--bg)] shadow-lg overflow-hidden max-h-60 overflow-y-auto">
            <div class="p-2 border-b border-[var(--border)]">
                <input
                    ref="searchInputRef"
                    v-model="search"
                    type="text"
                    :placeholder="placeholder ?? 'Suchen...'"
                    class="w-full text-sm bg-transparent outline-none"
                />
            </div>
            <button
                type="button"
                class="w-full flex items-center gap-2 px-3 py-2 text-sm text-left text-(--text-muted) hover:bg-primary/5 transition-colors"
                @click="select(null)"
            >
                <font-awesome-icon :icon="['fas', 'user-slash']" class="text-xs" />
                <span>Nicht zugewiesen</span>
            </button>
            <button
                v-for="m in filteredMembers"
                :key="m.id"
                type="button"
                :class="String(m.id) === model ? 'bg-primary/10' : 'hover:bg-primary/5'"
                class="w-full flex items-center gap-2 px-3 py-2 text-sm text-left transition-colors"
                @click="select(m.id)"
            >
                <UserAvatar :identity="m" :name="m.name" size="sm" />
                <span>{{ m.name }}</span>
            </button>
        </div>
    </div>
</template>
