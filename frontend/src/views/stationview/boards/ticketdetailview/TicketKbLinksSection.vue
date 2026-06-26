/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { BoardTicketKbLink } from '@/api/boards'
import type {KbSearchResult} from './types'


const searchQuery = defineModel<string>('searchQuery', {required: true})

defineProps<{
    kbLinks: BoardTicketKbLink[]
    showSearch: boolean
    searchResults: KbSearchResult[]
    readonly?: boolean
}>()

const emit = defineEmits<{
    search: []
    add: [id: number]
    remove: [id: number]
}>()

const { t } = useI18n()
</script>

<template>
    <div>
        <SubHeader class="text-sm mb-2">{{ t('boards.kbLinks') }}</SubHeader>
        <div class="space-y-1 mb-2">
            <div v-for="kl in kbLinks" :key="kl.id" class="flex items-center gap-2 text-sm group">
                <font-awesome-icon :icon="['fas', 'book']" class="text-(--text-muted) text-xs shrink-0" />
                <router-link :to="`/station/knowledge/${kl.kbFileId}`" class="text-primary hover:underline truncate flex-1">
                    <span v-if="kl.folderPath && kl.folderPath !== '/'" class="text-(--text-muted) text-xs">{{ kl.folderPath }} /&nbsp;</span>{{ kl.title }}
                </router-link>
                <IconButton v-if="!readonly" :icon="['fas', 'xmark']" label="Remove" class="sm:opacity-0 sm:group-hover:opacity-100 text-xs" @click="emit('remove', kl.id)" />
            </div>
        </div>
        <div v-if="showSearch" class="space-y-1">
            <TextInput v-model="searchQuery" :placeholder="t('boards.searchKb')" class="text-sm" @update:model-value="emit('search')" />
            <div v-if="searchResults.length > 0" class="border border-(--border) rounded-theme divide-y divide-(--border)">
                <div v-for="r in searchResults" :key="r.id" class="px-3 py-1.5 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2" @click="emit('add', r.id)">
                    <font-awesome-icon :icon="['fas', 'book']" class="text-(--text-muted) text-xs shrink-0" />
                    <div class="truncate"><span v-if="r.path && r.path !== '/'" class="text-(--text-muted) text-xs">{{ r.path }} /&nbsp;</span>{{ r.title }}</div>
                </div>
            </div>
        </div>
    </div>
</template>
