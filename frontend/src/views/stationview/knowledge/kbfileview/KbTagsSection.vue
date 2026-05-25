/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import type {KbTag} from '@/api/knowledgeBase'

const props = defineProps<{
    tags: KbTag[]
    allTags: KbTag[]
    canManage: boolean
}>()

const emit = defineEmits<{
    addTag: [name: string]
    removeTag: [name: string]
}>()

const {t} = useI18n()

const newTagInput = ref('')
const showTagSuggestions = ref(false)

function hideTagSuggestions() {
    setTimeout(() => { showTagSuggestions.value = false }, 200)
}

const tagSuggestions = computed(() => {
    const input = newTagInput.value.toLowerCase().trim()
    if (!input) return []
    const existing = new Set(props.tags.map(t => t.name.toLowerCase()))
    return props.allTags
        .filter(t => t.name.toLowerCase().includes(input) && !existing.has(t.name.toLowerCase()))
        .slice(0, 8)
})

function onAddTag(name?: string) {
    const tagName = (name ?? newTagInput.value).trim()
    if (!tagName) return
    emit('addTag', tagName)
    newTagInput.value = ''
    showTagSuggestions.value = false
}
</script>

<template>
    <div v-if="tags.length > 0 || canManage" class="flex flex-wrap items-center gap-2 mb-4">
        <font-awesome-icon :icon="['fas', 'tags']" class="text-xs text-[var(--text-muted)]"/>
        <span
            v-for="tag in tags"
            :key="tag.id"
            class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs bg-[var(--bg-accent)] text-[var(--text)]"
        >
            {{ tag.name }}
            <IconButton
                v-if="canManage"
                :icon="['fas', 'xmark']"
                :label="t('common.remove')"
                class="!p-0 text-[10px] hover:text-[var(--error)]"
                @click="emit('removeTag', tag.name)"
            />
        </span>
        <div v-if="canManage" class="relative inline-flex">
            <form @submit.prevent="onAddTag()">
                <TextInput
                    v-model="newTagInput"
                    :placeholder="t('kb.tagPlaceholder')"
                    class="!py-0.5 !px-2 !text-xs w-32"
                    @focus="showTagSuggestions = true"
                    @blur="hideTagSuggestions"
                />
            </form>
            <div
                v-if="showTagSuggestions && tagSuggestions.length > 0"
                class="absolute top-full left-0 mt-1 z-20 min-w-40 rounded border border-[var(--border)] bg-[var(--bg)] shadow-lg py-1"
            >
                <DropdownMenuItem
                    v-for="suggestion in tagSuggestions"
                    :key="suggestion.id"
                    :icon="['fas', 'tag']"
                    class="!text-xs !py-1"
                    @click="onAddTag(suggestion.name)"
                >
                    {{ suggestion.name }}
                </DropdownMenuItem>
            </div>
        </div>
    </div>
</template>
