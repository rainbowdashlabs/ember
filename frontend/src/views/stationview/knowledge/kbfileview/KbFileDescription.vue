/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * The line under an article's title, and the small edit it turns into. Shown even when empty for a
 * reader who may write, so the way to give an article a description is where the description is.
 */
defineProps<{
    description: string
    canEdit: boolean
}>()

const editing = defineModel<boolean>('editing', {required: true})
const value = defineModel<string>('value', {required: true})

const emit = defineEmits<{
    start: []
    save: []
}>()

const {t} = useI18n()
</script>

<template>
    <div v-if="editing" class="flex items-center gap-2 mb-4">
        <TextAreaInput v-model="value" class="flex-1 !text-sm" :placeholder="t('kb.description')"/>
        <PrimaryButton :aria-label="t('common.save')" :title="t('common.save')" @click="emit('save')">
            <font-awesome-icon :icon="['fas', 'check']"/>
        </PrimaryButton>
        <SecondaryButton :aria-label="t('common.cancel')" :title="t('common.cancel')" @click="editing = false">
            <font-awesome-icon :icon="['fas', 'xmark']"/>
        </SecondaryButton>
    </div>
    <MutedText v-else-if="description || canEdit" tag="p" size="sm" class="group/desc">
        {{ description || t('kb.description') }}
        <IconButton
            v-if="canEdit"
            :icon="['fas', 'pen']"
            :label="t('kb.edit')"
            class="opacity-0 group-hover/desc:opacity-100 ml-1 text-[var(--primary)] !p-0 text-xs"
            @click="emit('start')"
        />
    </MutedText>
</template>
