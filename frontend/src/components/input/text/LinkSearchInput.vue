/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {
    ComboboxAnchor,
    ComboboxContent,
    ComboboxInput,
    ComboboxItem,
    ComboboxPortal,
    ComboboxRoot,
    ComboboxViewport,
    type AcceptableValue,
} from 'reka-ui'
import TextInput from './TextInput.vue'
import LinkPickedChip from './linksearch/LinkPickedChip.vue'
import MediaBrowseButton from '@/components/media/MediaBrowseButton.vue'
import {listPublicPages} from '@/api/publicPages'
import {listMediaFiles} from '@/api/media'
import type {FileListing, PublicPageSummary, StationFile} from '@/api/generated/schema'

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

/**
 * A link typed as an address or picked from what the station has: its public pages, its knowledge
 * base, its calendar, and with `noFiles` unset its files as well.
 *
 * <p>The field is a combobox. Suggestions open as it gets the focus and narrow as the reader types;
 * the arrow keys walk them while the focus stays in the field, and Enter takes one. An address
 * typed out in full is taken as it is and suggests nothing. The station's files are only listed
 * where a file may be picked, but its pages are needed either way, for the suggestions and to name
 * a page already linked.
 */
const {t} = useI18n()
const open = ref(false)
const pagesCache = ref<PublicPageSummary[] | null>(null)
const filesCache = ref<StationFile[] | null>(null)
const loaded = ref(false)

async function ensureLoaded() {
    if (loaded.value || !props.stationUid) return
    try {
        const [pages, files] = await Promise.all([
            listPublicPages(props.stationUid),
            props.noFiles
                ? Promise.resolve([] as FileListing[])
                : listMediaFiles().catch(() => [] as FileListing[]),
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
const pickedFile = computed<StationFile | null>(() => {
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
    if (/^(https?:|\/\/|mailto:|tel:)/i.test(q)) return []
    if (!q) return result.slice(0, 8)
    return result.filter(s =>
        s.title.toLowerCase().includes(q) || s.url.toLowerCase().includes(q)).slice(0, 10)
})

function pick(url: AcceptableValue | AcceptableValue[] | undefined) {
    if (typeof url !== 'string') return
    model.value = url
    open.value = false
}

function pickFile(picked: {file: StationFile; url: string}) {
    model.value = picked.url
    open.value = false
    if (filesCache.value && !filesCache.value.some(f => f.id === picked.file.id)) filesCache.value.push(picked.file)
}

function suggestionIcon(kind: LinkKind): string[] {
    return ['fas', kind === 'page' ? 'file-lines' : kind === 'calendar' ? 'calendar' : 'book']
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
    <ComboboxRoot
        :model-value="model"
        :open="open && suggestions.length > 0"
        ignore-filter
        open-on-focus
        open-on-click
        :reset-search-term-on-blur="false"
        :reset-search-term-on-select="false"
        class="w-full"
        @update:model-value="pick"
        @update:open="open = $event"
    >
        <ComboboxAnchor class="flex items-center gap-1">
            <LinkPickedChip
                v-if="pickedFile"
                :icon="['fas', 'file']"
                :title="pickedFile.fileName"
                :hint="`${pickedFile.mimeType ?? '-'} · ${formatBytes(pickedFile.fileSize)}`"
                @clear="model = ''"
            />
            <LinkPickedChip
                v-else-if="pickedInternal"
                :icon="['fas', pickedInternal.icon]"
                :title="pickedInternal.title"
                :hint="pickedInternal.hint"
                @clear="model = ''"
            />
            <ComboboxInput v-else v-model="model" as-child>
                <TextInput
                    v-model="model"
                    class="flex-1"
                    :placeholder="placeholder ?? t('stationPages.editor.linkSearchPlaceholder')"
                    @focus="ensureLoaded"
                />
            </ComboboxInput>
            <MediaBrowseButton v-if="stationUid && !noFiles" :station-uid="stationUid" :mime-prefix="mimePrefix" @pick="pickFile"/>
        </ComboboxAnchor>
        <ComboboxPortal>
            <ComboboxContent
                position="popper"
                :side-offset="4"
                class="z-[90] w-(--reka-combobox-trigger-width) max-h-64 overflow-hidden rounded-theme border border-(--border) bg-(--bg) shadow-lg"
            >
                <ComboboxViewport class="py-1">
                    <ComboboxItem
                        v-for="s in suggestions"
                        :key="s.url"
                        :value="s.url"
                        class="flex w-full cursor-pointer items-center gap-2 px-4 py-2 text-left text-sm outline-none data-[highlighted]:bg-primary/10"
                    >
                        <font-awesome-icon :icon="suggestionIcon(s.kind)" class="w-4 text-primary"/>
                        <span class="flex min-w-0 flex-col items-start">
                            <span class="truncate">{{ s.title }}</span>
                            <span v-if="s.hint" class="text-xs text-(--text-muted) truncate">{{ s.hint }}</span>
                        </span>
                    </ComboboxItem>
                </ComboboxViewport>
            </ComboboxContent>
        </ComboboxPortal>
    </ComboboxRoot>
</template>
