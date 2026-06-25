/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'

defineProps<{
    canAddChecklist: boolean
}>()

const emit = defineEmits<{
    addChecklist: []
    addLink: []
    addWeblink: []
    addAttachment: []
    addKbLink: []
}>()

const { t } = useI18n()

const showMenu = ref(false)
const menuRef = ref<HTMLElement | null>(null)

function handleClickOutside(e: MouseEvent) {
    if (showMenu.value && menuRef.value && !menuRef.value.contains(e.target as Node)) {
        showMenu.value = false
    }
}

onMounted(() => document.addEventListener('click', handleClickOutside))
onBeforeUnmount(() => document.removeEventListener('click', handleClickOutside))
</script>

<template>
    <div ref="menuRef" class="relative inline-block">
        <IconButton :icon="['fas', 'plus']" label="Add" class="text-(--text-muted)" @click.stop="showMenu = !showMenu" />
        <div v-if="showMenu" class="absolute left-0 mt-1 w-48 rounded-theme border border-[var(--border)] bg-[var(--bg)] shadow-lg z-20">
            <div v-if="canAddChecklist" class="px-3 py-2 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2" @click="emit('addChecklist'); showMenu = false">
                <font-awesome-icon :icon="['fas', 'list-check']" class="text-(--text-muted) w-4" />
                {{ t('boards.checklist') }}
            </div>
            <div class="px-3 py-2 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2" @click="emit('addLink'); showMenu = false">
                <font-awesome-icon :icon="['fas', 'link']" class="text-(--text-muted) w-4" />
                {{ t('boards.addLink') }}
            </div>
            <div class="px-3 py-2 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2" @click="emit('addWeblink'); showMenu = false">
                <font-awesome-icon :icon="['fas', 'globe']" class="text-(--text-muted) w-4" />
                {{ t('boards.addWeblink') }}
            </div>
            <div class="px-3 py-2 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2" @click="emit('addAttachment'); showMenu = false">
                <font-awesome-icon :icon="['fas', 'paperclip']" class="text-(--text-muted) w-4" />
                {{ t('boards.addAttachment') }}
            </div>
            <div class="px-3 py-2 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2" @click="emit('addKbLink'); showMenu = false">
                <font-awesome-icon :icon="['fas', 'book']" class="text-(--text-muted) w-4" />
                {{ t('boards.addKbLink') }}
            </div>
        </div>
    </div>
</template>
