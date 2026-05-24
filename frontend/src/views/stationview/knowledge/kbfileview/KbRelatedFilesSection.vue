/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import type {KbFile} from '@/api/knowledgeBase'
import {knowledgeBase} from '@/api'

const props = defineProps<{
    relatedFiles: KbFile[]
    fileId: number
    canManage: boolean
}>()

const emit = defineEmits<{
    addRelated: [targetId: number]
    removeRelated: [targetId: number]
}>()

const {t} = useI18n()

const showAddRelated = ref(false)
const allStationFiles = ref<KbFile[]>([])
const relatedSearchQuery = ref('')

async function openAddRelated() {
    showAddRelated.value = true
    relatedSearchQuery.value = ''
    const browse = await knowledgeBase.browse()
    allStationFiles.value = browse.files.filter(f => f.id !== props.fileId)
}

const filteredRelatedCandidates = computed(() => {
    const existingIds = new Set(props.relatedFiles.map(f => f.id))
    const q = relatedSearchQuery.value.toLowerCase()
    return allStationFiles.value
        .filter(f => !existingIds.has(f.id))
        .filter(f => !q || f.name.toLowerCase().includes(q) || f.description.toLowerCase().includes(q))
        .slice(0, 10)
})
</script>

<template>
    <div v-if="relatedFiles.length > 0 || canManage" class="mb-4">
        <div class="flex items-center gap-2 mb-2">
            <font-awesome-icon :icon="['fas', 'book-open']" class="text-xs text-[var(--text-muted)]"/>
            <span class="text-sm font-medium">{{ t('kb.relatedFiles') }}</span>
            <SecondaryButton
                v-if="canManage"
                class="!text-xs !py-0.5 !px-2"
                @click="openAddRelated"
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
        <!-- Add related file picker -->
        <div v-if="showAddRelated" class="mt-2 p-3 rounded-lg border border-[var(--border)] bg-[var(--bg)]">
            <TextInput
                v-model="relatedSearchQuery"
                :placeholder="t('kb.searchRelated')"
                class="!text-sm mb-2"
            />
            <div v-if="filteredRelatedCandidates.length > 0" class="flex flex-col gap-1 max-h-40 overflow-y-auto">
                <DropdownMenuItem
                    v-for="candidate in filteredRelatedCandidates"
                    :key="candidate.id"
                    :icon="['fas', 'file-lines']"
                    class="!text-sm !py-1 !px-2 !rounded"
                    @click="emit('addRelated', candidate.id)"
                >
                    <span class="truncate">{{ candidate.name }}</span>
                    <span v-if="candidate.description" class="text-xs text-[var(--text-muted)] truncate">
                        {{ candidate.description }}
                    </span>
                </DropdownMenuItem>
            </div>
            <p v-else class="text-xs text-[var(--text-muted)]">{{ t('kb.noFilesFound') }}</p>
            <SecondaryButton compact class="mt-2 " @click="showAddRelated = false">
                {{ t('common.close') }}
            </SecondaryButton>
        </div>
    </div>
</template>
