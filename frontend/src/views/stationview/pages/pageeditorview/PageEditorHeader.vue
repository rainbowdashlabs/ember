/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'

defineProps<{
    title: string
    hasUnsavedChanges: boolean
    preview: boolean
    save: () => Promise<unknown> | unknown
}>()

defineEmits<{
    back: []
    'toggle-preview': []
}>()

const {t} = useI18n()
</script>

<template>
    <div class="flex items-center justify-between flex-wrap gap-2">
        <div class="flex items-center gap-3">
            <IconButton
                :icon="['fas', 'arrow-left']"
                :label="t('common.back')"
                class="text-[var(--text-muted)] hover:text-[var(--text)]"
                @click="$emit('back')"
            />
            <PageHeader>{{ title }}</PageHeader>
            <span
                v-if="hasUnsavedChanges"
                class="text-xs text-info-accent font-medium"
            >
                {{ t('stationPages.editor.unsavedChanges') }}
            </span>
        </div>
        <div class="flex gap-2">
            <SecondaryButton @click="$emit('toggle-preview')">
                <font-awesome-icon :icon="['fas', preview ? 'pen' : 'eye']" class="mr-1"/>
                {{ preview ? t('stationPages.editor.edit') : t('stationPages.editor.preview') }}
            </SecondaryButton>
            <SaveButton :action="save"/>
        </div>
    </div>
</template>
