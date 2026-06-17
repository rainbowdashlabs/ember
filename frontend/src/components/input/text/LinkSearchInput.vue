/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from './TextInput.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import IconButton from '@/components/button/IconButton.vue'
import PageFileBrowseButton from '@/views/stationview/pages/pageeditorview/PageFileBrowseButton.vue'
import {listPublicPages, type PublicPageSummary} from '@/api/publicPages'
import {listStationPageFiles, type PageFile, type PageFileListing} from '@/api/pageManage'

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
    /** Optional MIME prefix forwarded to the browse modal (e.g. 'audio/' on an AUDIO cell). */
    mimePrefix?: string
    /**
     * Hides the file-browse affordance for fields where picking a file is nonsensical
     * (button destinations, CTA URLs, attribution links). The page / KB / calendar
     * suggestions in the dropdown stay.
     */
    noFiles?: boolean
}>()

const {t} = useI18n()
const open = ref(false)
const rootRef = ref<HTMLElement | null>(null)
const pagesCache = ref<PublicPageSummary[] | null>(null)
const filesCache = ref<PageFile[] | null>(null)
const loaded = ref(false)

async function ensureLoaded() {
    if (loaded.value || !props.stationUid) return
    try {
        const [pages, files] = await Promise.all([
            listPublicPages(props.stationUid),
            // Skip the file-listing round-trip on URL-only fields. The page list is still needed
            // for both the suggestion dropdown and the internal-page chip rendering.
            props.noFiles
                ? Promise.resolve([] as PageFileListing[])
                : listStationPageFiles().catch(() => [] as PageFileListing[]),
        ])
        pagesCache.value = pages
        filesCache.value = files.map(l => l.file)
    } catch {
        pagesCache.value = []
        filesCache.value = []
    } finally {
        loaded.value = true
    }
}

function formatBytes(bytes: number): string {
    if (bytes >= 1024 * 1024) {
        const mb = bytes / (1024 * 1024)
        return `${mb % 1 === 0 ? mb.toFixed(0) : mb.toFixed(1)} MB`
    }
    if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
    return `${bytes} B`
}

/** If the current model points at a page-file we know about, surface its file name. */
const pickedFile = computed<PageFile | null>(() => {
    const url = model.value ?? ''
    if (!url) return null
    const m = url.match(/\/files\/([0-9a-f]{64})$/)
    if (!m) return null
    const hash = m[1]
    for (const f of (filesCache.value ?? [])) {
        if (f.contentHash === hash) return f
    }
    return null
})

/**
 * If the current model points at an internal destination (a public page, the KB, or the
 * calendar) for this station, surface a friendly chip instead of the raw URL. Returns null
 * for external URLs or anything we can't resolve, so the regular text editor stays visible.
 */
const pickedInternal = computed<{title: string; hint: string; icon: string} | null>(() => {
    const url = model.value ?? ''
    if (!url || !props.stationUid) return null
    const base = `/public/station/${props.stationUid}`
    if (!url.startsWith(base)) return null
    const rest = url.slice(base.length)
    if (rest === '/calendar') {
        return {title: t('stationPages.editor.linkPickCalendar'), hint: '/calendar', icon: 'calendar'}
    }
    if (rest === '/knowledge') {
        return {title: t('stationPages.editor.linkPickKb'), hint: '/knowledge', icon: 'book'}
    }
    const pageMatch = rest.match(/^\/page\/(.+)$/)
    if (pageMatch) {
        const path = pageMatch[1]
        const page = (pagesCache.value ?? []).find(p => p.path === path)
        if (page) return {title: page.title, hint: `/page/${page.path}`, icon: 'file-lines'}
    }
    return null
})

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

if (typeof document !== 'undefined') {
    document.addEventListener('click', onDocClick)
    onBeforeUnmount(() => document.removeEventListener('click', onDocClick))
}

const needsResolve = (v: string | undefined) => {
    if (!v || !props.stationUid) return false
    if (/\/files\/[0-9a-f]{64}/.test(v)) return true
    return v.startsWith(`/public/station/${props.stationUid}`)
}
onMounted(() => { if (needsResolve(model.value)) ensureLoaded() })
watch(() => model.value, v => { if (!loaded.value && needsResolve(v)) ensureLoaded() })
</script>

<template>
    <div ref="rootRef" class="relative w-full">
        <div class="flex items-center gap-1">
            <!-- A file was picked: replace the URL input with a chip showing the file name + meta. -->
            <div
                v-if="pickedFile"
                class="flex-1 flex items-center gap-2 px-3 py-2 rounded-theme border border-(--border) bg-bg-light dark:bg-bg-dark"
            >
                <font-awesome-icon :icon="['fas', 'file']" class="text-primary shrink-0"/>
                <span class="flex flex-col min-w-0 flex-1">
                    <span class="text-sm truncate" :title="pickedFile.fileName">{{ pickedFile.fileName }}</span>
                    <span class="text-xs text-(--text-muted) truncate">
                        {{ pickedFile.mimeType ?? '—' }} · {{ formatBytes(pickedFile.fileSize) }}
                    </span>
                </span>
                <IconButton
                    :icon="['fas', 'xmark']"
                    :label="t('common.delete')"
                    class="text-(--text-muted) hover:text-error shrink-0 !p-1"
                    @click="model = ''"
                />
            </div>
            <!-- An internal destination (page / KB / calendar) was picked: show its title. -->
            <div
                v-else-if="pickedInternal"
                class="flex-1 flex items-center gap-2 px-3 py-2 rounded-theme border border-(--border) bg-bg-light dark:bg-bg-dark"
            >
                <font-awesome-icon :icon="['fas', pickedInternal.icon]" class="text-primary shrink-0"/>
                <span class="flex flex-col min-w-0 flex-1">
                    <span class="text-sm truncate" :title="pickedInternal.title">{{ pickedInternal.title }}</span>
                    <span class="text-xs text-(--text-muted) truncate">{{ pickedInternal.hint }}</span>
                </span>
                <IconButton
                    :icon="['fas', 'xmark']"
                    :label="t('common.delete')"
                    class="text-(--text-muted) hover:text-error shrink-0 !p-1"
                    @click="model = ''"
                />
            </div>
            <!-- Plain URL editing: searchable text input. -->
            <div v-else class="flex-1" @focusin="onFocus" @click="onFocus">
                <TextInput
                    v-model="model"
                    :placeholder="placeholder ?? t('stationPages.editor.linkSearchPlaceholder')"
                />
            </div>
            <PageFileBrowseButton
                v-if="stationUid && !noFiles"
                :station-uid="stationUid"
                :mime-prefix="mimePrefix"
                @pick="(p: {file: PageFile; url: string}) => {
                    model = p.url
                    open = false
                    if (filesCache && !filesCache.find(f => f.id === p.file.id)) filesCache.push(p.file)
                }"
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
    </div>
</template>
