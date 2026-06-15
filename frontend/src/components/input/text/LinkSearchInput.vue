/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from './TextInput.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import IconButton from '@/components/button/IconButton.vue'
import PageFileBrowseModal from '@/views/stationview/pages/pageeditorview/PageFileBrowseModal.vue'
import {listPublicPages, type PublicPageSummary} from '@/api/publicPages'

type LinkKind = 'page' | 'kb' | 'calendar' | 'url'

interface Suggestion {
    kind: LinkKind
    title: string
    url: string
    hint?: string
}

const model = defineModel<string>()

const props = defineProps<{
    stationUid?: string | null
    placeholder?: string
}>()

const {t} = useI18n()
const open = ref(false)
const browseOpen = ref(false)
const rootRef = ref<HTMLElement | null>(null)
const pagesCache = ref<PublicPageSummary[] | null>(null)
const loaded = ref(false)

async function ensureLoaded() {
    if (loaded.value || !props.stationUid) return
    try {
        pagesCache.value = await listPublicPages(props.stationUid)
    } catch {
        pagesCache.value = []
    } finally {
        loaded.value = true
    }
}

const suggestions = computed<Suggestion[]>(() => {
    if (!props.stationUid) return []
    const uid = props.stationUid
    const base = `/public/station/${uid}`
    const result: Suggestion[] = [
        {kind: 'calendar', title: t('stationPages.editor.linkPickCalendar'), url: `${base}/calendar`, hint: '/calendar'},
        {kind: 'kb', title: t('stationPages.editor.linkPickKb'), url: `${base}/knowledge`, hint: '/knowledge'},
    ]
    for (const p of (pagesCache.value ?? [])) {
        result.push({
            kind: 'page',
            title: p.title,
            url: `${base}/page/${p.path}`,
            hint: `/page/${p.path}`,
        })
    }
    const q = (model.value ?? '').trim().toLowerCase()
    // If user typed a URL-ish value, don't suggest anything.
    if (/^(https?:|\/\/|mailto:|tel:)/i.test(q)) return []
    if (!q) return result.slice(0, 8)
    return result.filter(s =>
        s.title.toLowerCase().includes(q) || s.url.toLowerCase().includes(q)).slice(0, 10)
})

function pick(s: Suggestion) {
    model.value = s.url
    open.value = false
}

async function onFocus() {
    await ensureLoaded()
    open.value = true
}

function onDocClick(e: MouseEvent) {
    if (!rootRef.value) return
    if (!rootRef.value.contains(e.target as Node)) open.value = false
}

watch(() => model.value, () => {
    if (open.value) return
    // Re-open when the user types in the input again.
})

if (typeof document !== 'undefined') {
    document.addEventListener('click', onDocClick)
    onBeforeUnmount(() => document.removeEventListener('click', onDocClick))
}
</script>

<template>
    <div ref="rootRef" class="relative w-full">
        <div class="flex items-center gap-1">
            <div class="flex-1" @focusin="onFocus" @click="onFocus">
                <TextInput
                    v-model="model"
                    :placeholder="placeholder ?? t('stationPages.editor.linkSearchPlaceholder')"
                />
            </div>
            <IconButton
                v-if="stationUid"
                :icon="['fas', 'folder-open']"
                :label="t('stationPages.editor.browseFiles')"
                class="text-(--text-muted) hover:text-primary"
                @click="browseOpen = true"
            />
        </div>
        <div
            v-if="open && suggestions.length"
            class="absolute left-0 right-0 top-full mt-1 z-20 max-h-64 overflow-y-auto rounded-theme border border-(--border) bg-(--bg) shadow-lg py-1"
        >
            <DropdownMenuItem
                v-for="(s, i) in suggestions" :key="i"
                :icon="['fas', s.kind === 'page' ? 'file-lines' : s.kind === 'calendar' ? 'calendar' : 'book']"
                @click="pick(s)"
            >
                <span class="flex flex-col items-start text-left">
                    <span class="truncate">{{ s.title }}</span>
                    <span v-if="s.hint" class="text-xs text-(--text-muted) truncate">{{ s.hint }}</span>
                </span>
            </DropdownMenuItem>
        </div>
        <PageFileBrowseModal
            v-if="stationUid"
            v-model:open="browseOpen"
            :station-uid="stationUid"
            @pick="(p: {url: string}) => { model = p.url; open = false }"
        />
    </div>
</template>
