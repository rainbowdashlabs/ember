/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'

defineProps<{
    shortKey: string
    ticketNumber: number
    isWatching: boolean
    canEdit: boolean
}>()

const emit = defineEmits<{
    back: []
    toggleWatch: []
    requestDelete: []
}>()

const { t } = useI18n()
const showMenu = ref(false)

function closeMenu() {
    if (showMenu.value) showMenu.value = false
}

onMounted(() => document.addEventListener('click', closeMenu))
onBeforeUnmount(() => document.removeEventListener('click', closeMenu))
</script>

<template>
    <div class="flex items-center gap-3 mb-6">
        <IconButton :icon="['fas', 'chevron-left']" label="Back" @click="emit('back')" />
        <span class="font-mono text-[var(--text-muted)]">{{ shortKey }}-{{ ticketNumber }}</span>
        <div class="ml-auto flex items-center gap-1">
            <IconButton
                :icon="['fas', isWatching ? 'eye-slash' : 'eye']"
                :label="isWatching ? t('boards.unwatch') : t('boards.watch')"
                :class="isWatching ? 'text-[var(--accent)]' : 'text-[var(--text-muted)]'"
                @click="emit('toggleWatch')"
            />
            <div v-if="canEdit" class="relative">
                <IconButton :icon="['fas', 'ellipsis']" label="Menu" class="text-[var(--text-muted)]" @click.stop="showMenu = !showMenu" />
                <div v-if="showMenu" class="absolute right-0 mt-1 w-40 rounded-theme border border-[var(--border)] bg-[var(--bg)] shadow-lg z-20">
                    <DeleteButton class="w-full text-left px-3 py-2 text-sm" @click="emit('requestDelete'); showMenu = false">
                        {{ t('common.delete') }}
                    </DeleteButton>
                </div>
            </div>
        </div>
    </div>
</template>
