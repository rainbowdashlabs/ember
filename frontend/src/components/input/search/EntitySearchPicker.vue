/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup generic="T">
import {computed, onBeforeUnmount, ref, shallowRef, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import SearchInput from '@/components/input/text/SearchInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import Spinner from '@/components/feedback/Spinner.vue'

const props = defineProps<{
    /** Async search function — called with the typed query (or empty string for the default state). */
    searchFn: (query: string) => Promise<T[]>
    /** Primary label for each result row. */
    displayFn: (item: T) => string
    /** Optional second-line label. */
    subtitleFn?: (item: T) => string
    /** Optional gate for non-selectable rows (e.g. private partner stations). Defaults to always selectable. */
    isSelectableFn?: (item: T) => boolean
    /** Optional FontAwesome icon for each row, e.g. {@code ['fas', 'newspaper']}. */
    iconFn?: (item: T) => string[]
    /** Optional Tailwind class string applied to the leading icon — lets consumers colour by state. */
    iconClassFn?: (item: T) => string
    /**
     * Optional avatar/image URL for each row. When the function returns a non-empty string, the
     * row renders an {@code <img>} thumbnail instead of the FontAwesome {@link iconFn}.
     */
    avatarFn?: (item: T) => string | null | undefined
    /** Optional right-aligned state badge. Returns {@code null} to omit. */
    badgeFn?: (item: T) => { text: string; variant: 'success' | 'info' | 'error' | 'neutral' | 'warning' } | null
    /** Stable key for v-for; defaults to {@link displayFn}. */
    keyFn?: (item: T) => string | number
    /**
     * Label to render in the "picked" chip when the model holds a UUID but the entity itself has
     * not been refetched (e.g. on first mount). Falls back to the model value.
     */
    selectedDisplay?: string | null
    placeholder?: string
    disabled?: boolean
    /** Localised hint shown when the empty default-state list returns nothing. */
    emptyLabel?: string
    /** Localised tooltip shown when hovering a non-selectable row. */
    notSelectableHint?: string
}>()

const emit = defineEmits<{
    /** Fires when the user picks a row. Provides the chosen entity. */
    pick: [item: T]
}>()

/** UUID (or any string identifier) of the picked entity, or {@code null} for "no selection". */
const model = defineModel<string | null>()

const {t} = useI18n()

const query = ref('')
const results = shallowRef<T[]>([])
const loading = ref(false)
const open = ref(false)
const rootRef = ref<HTMLElement | null>(null)
let debounceTimer: ReturnType<typeof setTimeout> | null = null

const hasSelection = computed(() => model.value != null && model.value !== '')

const chipLabel = computed(() => props.selectedDisplay || model.value || '')

async function runSearch(q: string) {
    loading.value = true
    try {
        results.value = await props.searchFn(q)
    } catch {
        results.value = []
    } finally {
        loading.value = false
    }
}

function scheduleSearch(q: string) {
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => runSearch(q), 250)
}

async function onFocus() {
    if (hasSelection.value) return
    open.value = true
    if (results.value.length === 0 && !loading.value) {
        await runSearch('')
    }
}

watch(query, q => {
    if (!open.value || hasSelection.value) return
    scheduleSearch(q)
})

function pickItem(item: T) {
    if (props.isSelectableFn && !props.isSelectableFn(item)) return
    emit('pick', item)
    open.value = false
    query.value = ''
    results.value = []
}

function clearSelection() {
    model.value = null
    query.value = ''
    results.value = []
}

function onDocClick(e: MouseEvent) {
    if (!rootRef.value) return
    if (!rootRef.value.contains(e.target as Node)) open.value = false
}

if (typeof document !== 'undefined') {
    document.addEventListener('click', onDocClick)
    onBeforeUnmount(() => document.removeEventListener('click', onDocClick))
}
</script>

<template>
    <div ref="rootRef" class="relative w-full">
        <!-- A reference is set: show the picked label as a chip with an X to clear. -->
        <div
            v-if="hasSelection"
            class="flex items-center gap-2 px-3 py-2 rounded-theme border border-(--border) bg-bg-light dark:bg-bg-dark"
        >
            <font-awesome-icon :icon="['fas', 'check']" class="text-success shrink-0"/>
            <span class="text-sm truncate flex-1" :title="chipLabel">{{ chipLabel }}</span>
            <IconButton
                :icon="['fas', 'xmark']"
                :label="t('common.delete')"
                class="text-(--text-muted) hover:text-error shrink-0 !p-1"
                :disabled="disabled"
                @click="clearSelection"
            />
        </div>

        <!-- No reference set: search input + dropdown of typeahead results. -->
        <div v-else @focusin="onFocus" @click="onFocus">
            <SearchInput
                v-model="query"
                :placeholder="placeholder"
                :disabled="disabled"
            />

            <div
                v-if="open"
                class="absolute left-0 right-0 top-full mt-1 z-20 max-h-72 overflow-y-auto rounded-theme border border-(--border) bg-(--bg) shadow-lg py-1"
            >
                <div v-if="loading" class="flex items-center justify-center py-3">
                    <Spinner size="sm"/>
                </div>
                <p v-else-if="results.length === 0" class="px-3 py-2 text-sm text-(--text-muted) italic">
                    {{ emptyLabel ?? t('common.empty') }}
                </p>
                <div
                    v-for="item in results"
                    :key="keyFn ? keyFn(item) : displayFn(item)"
                    :title="isSelectableFn && !isSelectableFn(item) ? (notSelectableHint ?? '') : undefined"
                    :class="isSelectableFn && !isSelectableFn(item) ? 'opacity-50 cursor-not-allowed' : ''"
                >
                    <DropdownMenuItem
                        :icon="avatarFn && avatarFn(item) ? undefined : (iconFn ? iconFn(item) : ['fas', 'circle'])"
                        :icon-class="iconClassFn ? iconClassFn(item) : undefined"
                        @click="pickItem(item)"
                    >
                        <img
                            v-if="avatarFn && avatarFn(item)"
                            :src="avatarFn(item) ?? undefined"
                            :alt="displayFn(item)"
                            class="w-6 h-6 rounded-full object-cover shrink-0"
                        />
                        <span class="flex flex-col items-start text-left min-w-0 flex-1">
                            <span class="truncate font-medium">{{ displayFn(item) }}</span>
                            <span
                                v-if="subtitleFn && subtitleFn(item)"
                                class="text-xs text-(--text-muted) truncate"
                            >{{ subtitleFn(item) }}</span>
                        </span>
                        <span
                            v-if="badgeFn && badgeFn(item)"
                            :class="[
                                'shrink-0 rounded-full px-2 py-0.5 text-xs font-medium',
                                badgeFn(item)?.variant === 'success' ? 'bg-success/15 text-success' : '',
                                badgeFn(item)?.variant === 'info' ? 'bg-secondary/20 text-secondary-accent dark:text-secondary' : '',
                                badgeFn(item)?.variant === 'error' ? 'bg-error/15 text-error' : '',
                                badgeFn(item)?.variant === 'warning' ? 'bg-warning/15 text-warning' : '',
                                badgeFn(item)?.variant === 'neutral' ? 'bg-(--bg-accent) text-(--text-muted)' : '',
                            ]"
                        >{{ badgeFn(item)?.text }}</span>
                    </DropdownMenuItem>
                </div>
            </div>
        </div>
    </div>
</template>
