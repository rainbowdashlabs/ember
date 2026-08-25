/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import { renderMarkdown } from '@/util/markdown'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ProseContent from '@/components/display/ProseContent.vue'

defineProps<{
    canEdit: boolean
}>()

const description = defineModel<string>('description', { default: '' })
const editing = defineModel<boolean>('editing', { default: false })

const emit = defineEmits<{
    save: []
}>()

const { t } = useI18n()
</script>

<template>
    <div>
        <FieldLabel class="mb-1">{{ t('boards.ticketDescription') }}</FieldLabel>
        <ProseContent v-if="!editing && description" class="rounded-theme p-2 min-h-[2rem]" :class="canEdit ? 'cursor-pointer hover:bg-[var(--bg-accent)]' : ''" @click="canEdit && (editing = true)" v-html="renderMarkdown(description)"/>
        <div v-else-if="!editing && canEdit" class="text-sm text-[var(--text-muted)] cursor-pointer rounded-theme p-2 hover:bg-[var(--bg-accent)] italic" @click="editing = true">
            {{ t('boards.clickToAddDescription') }}
        </div>
        <div v-else>
            <MarkdownEditor v-model="description" :placeholder="t('boards.ticketDescription')" />
            <div class="flex justify-end mt-2">
                <PrimaryButton @click="editing = false; emit('save')">{{ t('common.save') }}</PrimaryButton>
            </div>
        </div>
    </div>
</template>
