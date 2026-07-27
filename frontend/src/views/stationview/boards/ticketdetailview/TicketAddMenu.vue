/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'

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
            <DropdownMenuItem v-if="canAddChecklist" :icon="['fas', 'list-check']" icon-class="text-(--text-muted)" @click="emit('addChecklist'); showMenu = false">
                {{ t('boards.checklist') }}
            </DropdownMenuItem>
            <DropdownMenuItem :icon="['fas', 'link']" icon-class="text-(--text-muted)" @click="emit('addLink'); showMenu = false">
                {{ t('boards.addLink') }}
            </DropdownMenuItem>
            <DropdownMenuItem :icon="['fas', 'globe']" icon-class="text-(--text-muted)" @click="emit('addWeblink'); showMenu = false">
                {{ t('boards.addWeblink') }}
            </DropdownMenuItem>
            <DropdownMenuItem :icon="['fas', 'paperclip']" icon-class="text-(--text-muted)" @click="emit('addAttachment'); showMenu = false">
                {{ t('boards.addAttachment') }}
            </DropdownMenuItem>
            <DropdownMenuItem :icon="['fas', 'book']" icon-class="text-(--text-muted)" @click="emit('addKbLink'); showMenu = false">
                {{ t('boards.addKbLink') }}
            </DropdownMenuItem>
        </div>
    </div>
</template>
