/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import type {SignatureConfig} from '@/api/generated/schema'
import {useBlockEditorOptions} from '@/composables/useBlockEditorOptions'
import {renderMarkdown} from '@/util/markdown'

/**
 * A line to sign on in a letter, as the editor shows it: the line, who signs on it, and the short text
 * printed below, its placeholders as chips.
 */
const props = defineProps<{
    config: SignatureConfig
    content: string
}>()

const {t} = useI18n()
const options = useBlockEditorOptions()

const signer = computed(() => props.config.signer
    ? t('documentTemplates.signatureFor', {signer: t(`documentTemplates.signer.${props.config.signer}`)})
    : t('documentTemplates.signerMissing'))

const below = computed(() => options.value.tokens?.prepare(props.content) ?? props.content)
</script>

<template>
    <div class="space-y-1" data-testid="signature-cell">
        <div class="h-12 border-b border-(--text)"/>
        <MutedText tag="p">{{ signer }}</MutedText>
        <div v-if="content" class="markdown-content text-sm" v-html="renderMarkdown(below)"/>
    </div>
</template>
