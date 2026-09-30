/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import EntitySearchPicker from './EntitySearchPicker.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import {searchNews, type NewsSearchResult} from '@/api/news'
import {findEmbeddedNews} from '@/components/content/blockeditor/embeddedNewsLookup'
import {useBlockAudience} from '@/composables/useBlockAudience'
import {apiUrl} from '@/util/apiUrl'

/**
 * Picks a news entry for a news block by searching the station's news by title.
 *
 * <p>It offers what every reader of the content may read, because the block shows the same to all
 * of them: on a page the entries on the public blog, in a news or wiki article every entry every
 * member may read, internal ones included. Who is picking does not widen it. The search runs on the
 * server, newest first, a few entries at a time, and "show more" asks again for the same words with
 * room for more.
 */
const model = defineModel<string | null>()

const props = defineProps<{
    /** Public station UID used to resolve a stored {@code publicUid} back to its title. */
    stationUid?: string | null
    selectedDisplay?: string | null
    placeholder?: string
    disabled?: boolean
}>()

const emit = defineEmits<{
    pick: [item: NewsSearchResult]
}>()

const {t} = useI18n()
const audience = useBlockAudience()

const PAGE_SIZE = 5

const shown = ref(PAGE_SIZE)
const more = ref(false)
let lastQuery = ''

const searchFn = computed(() => {
    const limit = shown.value
    return async (query: string): Promise<NewsSearchResult[]> => {
        const size = query === lastQuery ? limit : PAGE_SIZE
        if (query !== lastQuery) shown.value = PAGE_SIZE
        lastQuery = query
        const page = await searchNews(query, size, audience)
        more.value = page.more
        return page.entries
    }
})

function showMore() {
    shown.value += PAGE_SIZE
}

const displayFn = (item: NewsSearchResult) => item.title
const subtitleFn = (item: NewsSearchResult) => item.summary ?? ''
const keyFn = (item: NewsSearchResult) => item.publicUid
const iconFn = (): string[] => ['fas', 'newspaper']

const hint = computed(() =>
    t(audience === 'MEMBERS' ? 'stationPages.editor.newsTeaserHintMembers' : 'stationPages.editor.newsTeaserHintPublic'))

const apiBase = apiUrl('')
const resolvedTitle = ref<string | null>(null)

async function resolve() {
    resolvedTitle.value = null
    if (!props.stationUid || !model.value) return
    try {
        resolvedTitle.value = (await findEmbeddedNews(apiBase, props.stationUid, model.value, audience))?.title ?? null
    } catch {
        resolvedTitle.value = null
    }
}

onMounted(resolve)
watch(() => [props.stationUid, model.value], resolve)
</script>

<template>
    <div>
        <EntitySearchPicker
            v-model="model"
            :search-fn="searchFn"
            :display-fn="displayFn"
            :subtitle-fn="subtitleFn"
            :key-fn="keyFn"
            :icon-fn="iconFn"
            :selected-display="resolvedTitle ?? selectedDisplay"
            :placeholder="placeholder ?? t('stationPages.editor.newsTeaserSearchPlaceholder')"
            :empty-label="t('stationPages.editor.newsTeaserSearchEmpty')"
            :disabled="disabled"
            @pick="(it: NewsSearchResult) => emit('pick', it)"
        >
            <template #footer>
                <div v-if="more" class="px-2 pt-1">
                    <SecondaryButton class="w-full" data-testid="news-search-more" @click="showMore">
                        {{ t('stationPages.editor.newsTeaserSearchMore') }}
                    </SecondaryButton>
                </div>
            </template>
        </EntitySearchPicker>
        <FieldHint class="mt-1" data-testid="news-picker-hint">{{ hint }}</FieldHint>
    </div>
</template>
