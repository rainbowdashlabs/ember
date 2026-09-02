/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import KbFileSearchPicker from '@/components/input/search/KbFileSearchPicker.vue'
import type {KbFile, SearchResult} from '@/api/knowledgeBase'

/**
 * The articles this one points at, and the articles pointing at it.
 *
 * The second list is the first one read the other way round rather than a second set of rows, so a
 * reference shows on both articles while only the article that wrote it can take it away. That is
 * why the back-references carry no remove button: removing one belongs on the other article.
 *
 * Articles the reader may not open are left out of both lists by the server, and not counted
 * either: a number standing one higher would give away that the article exists just as plainly as
 * its title would.
 */
const props = defineProps<{
    relatedFiles: KbFile[]
    backlinks: KbFile[]
    fileId: number
    canManage: boolean
}>()

const emit = defineEmits<{
    addRelated: [targetId: number]
    removeRelated: [targetId: number]
}>()

const {t} = useI18n()

const showAddRelated = ref(false)

const excludeIds = computed(() => [props.fileId, ...props.relatedFiles.map(file => file.id)])

function onPick(result: SearchResult) {
    emit('addRelated', result.file.id)
    showAddRelated.value = false
}
</script>

<template>
    <div v-if="relatedFiles.length > 0 || backlinks.length > 0 || canManage" class="mb-4">
        <div class="flex items-center gap-2 mb-2">
            <font-awesome-icon :icon="['fas', 'book-open']" class="text-xs text-[var(--text-muted)]"/>
            <span class="text-sm font-medium">{{ t('kb.relatedFiles') }}</span>
            <SecondaryButton
                v-if="canManage"
                class="!text-xs !py-0.5 !px-2"
                data-testid="kb-add-related"
                @click="showAddRelated = true"
            >
                <font-awesome-icon :icon="['fas', 'plus']"/> {{ t('common.add') }}
            </SecondaryButton>
        </div>

        <div class="flex flex-wrap gap-2">
            <router-link
                v-for="rf in relatedFiles"
                :key="rf.id"
                :to="{name: 'kb-file', params: {id: rf.id}}"
                class="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg text-sm bg-[var(--bg-accent)] border border-[var(--border)] hover:border-[var(--primary)] transition-colors group/rf"
            >
                <font-awesome-icon :icon="['fas', 'file-lines']" class="text-xs text-[var(--primary)]"/>
                {{ rf.name }}
                <IconButton
                    v-if="canManage"
                    :icon="['fas', 'xmark']"
                    :label="t('common.remove')"
                    class="!p-0 text-[10px] opacity-0 group-hover/rf:opacity-100 text-[var(--error)]"
                    @click.prevent="emit('removeRelated', rf.id)"
                />
            </router-link>
        </div>

        <div v-if="showAddRelated" class="mt-2 p-3 rounded-lg border border-[var(--border)] bg-[var(--bg)]">
            <KbFileSearchPicker :exclude-ids="excludeIds" @pick="onPick"/>
            <SecondaryButton compact class="mt-2" @click="showAddRelated = false">
                {{ t('common.close') }}
            </SecondaryButton>
        </div>

        <div v-if="backlinks.length > 0" class="mt-3">
            <div class="flex items-center gap-2 mb-2">
                <font-awesome-icon :icon="['fas', 'link']" class="text-xs text-[var(--text-muted)]"/>
                <span class="text-sm font-medium">{{ t('kb.backlinks') }}</span>
            </div>
            <div class="flex flex-wrap gap-2" data-testid="kb-backlinks">
                <router-link
                    v-for="bl in backlinks"
                    :key="bl.id"
                    :to="{name: 'kb-file', params: {id: bl.id}}"
                    class="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg text-sm bg-[var(--bg-accent)] border border-[var(--border)] hover:border-[var(--primary)] transition-colors"
                >
                    <font-awesome-icon :icon="['fas', 'file-lines']" class="text-xs text-[var(--text-muted)]"/>
                    {{ bl.name }}
                </router-link>
            </div>
        </div>
    </div>
</template>
