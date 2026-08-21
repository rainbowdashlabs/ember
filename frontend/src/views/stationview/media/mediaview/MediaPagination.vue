/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'

defineProps<{
    totalPages: number
    pageSizeOptions: readonly number[]
}>()

const currentPage = defineModel<number>('currentPage', {required: true})
const pageSize = defineModel<number>('pageSize', {required: true})

const {t} = useI18n()
</script>

<template>
    <div class="flex flex-wrap items-center justify-between gap-3 pt-2 text-sm">
        <div class="flex items-center gap-2">
            <span class="text-(--text-muted)">{{ t('stationPages.editor.perPage') }}</span>
            <SelectInput :model-value="String(pageSize)"
                         @update:model-value="(v: string | number | null | undefined) => pageSize = +(v ?? 0)">
                <option v-for="opt in pageSizeOptions" :key="opt" :value="String(opt)">{{ opt }}</option>
            </SelectInput>
        </div>
        <div class="flex items-center gap-2">
            <SecondaryButton :disabled="currentPage <= 1"
                             @click="currentPage = Math.max(1, currentPage - 1)">
                <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-1"/>
                {{ t('stationPages.editor.prevPage') }}
            </SecondaryButton>
            <span class="text-(--text-muted)">
                {{ t('stationPages.editor.pageOf', {current: currentPage, total: totalPages}) }}
            </span>
            <SecondaryButton :disabled="currentPage >= totalPages"
                             @click="currentPage = Math.min(totalPages, currentPage + 1)">
                {{ t('stationPages.editor.nextPage') }}
                <font-awesome-icon :icon="['fas', 'chevron-right']" class="ml-1"/>
            </SecondaryButton>
        </div>
    </div>
</template>
