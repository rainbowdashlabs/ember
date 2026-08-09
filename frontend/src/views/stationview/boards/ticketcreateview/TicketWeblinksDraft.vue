/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'

export interface DraftWeblink { key: number; url: string; title: string }

defineProps<{
    weblinks: DraftWeblink[]
    newUrl: string
    newTitle: string
}>()

const emit = defineEmits<{
    (e: 'update:newUrl', value: string): void
    (e: 'update:newTitle', value: string): void
    (e: 'add'): void
    (e: 'remove', key: number): void
}>()

const { t } = useI18n()
</script>

<template>
    <NeutralContainer>
        <SubHeader class="mb-2">{{ t('boards.weblinks') }}</SubHeader>
        <div v-for="wl in weblinks" :key="wl.key" class="flex items-center gap-2 py-0.5 group">
            <MutedIcon :icon="['fas', 'globe']" size="inline" class="shrink-0"/>
            <span class="text-sm truncate flex-1">{{ wl.title || wl.url }}</span>
            <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-xs sm:opacity-0 sm:group-hover:opacity-100" @click="emit('remove', wl.key)" />
        </div>
        <div class="flex gap-2 mt-2 items-center">
            <TextInput :model-value="newUrl" placeholder="https://..." class="flex-1 text-sm" @update:model-value="v => emit('update:newUrl', String(v))" />
            <TextInput :model-value="newTitle" :placeholder="t('boards.weblinkTitle')" class="flex-1 text-sm" @update:model-value="v => emit('update:newTitle', String(v))" @keydown.enter="emit('add')" />
            <IconButton :icon="['fas', 'plus']" :label="t('common.add')" class="text-(--text-muted)" @click="emit('add')" />
        </div>
    </NeutralContainer>
</template>
