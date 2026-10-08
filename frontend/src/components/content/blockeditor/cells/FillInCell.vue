/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import type {FillInConfig} from '@/api/generated/schema'

/**
 * A box in a letter that a signer fills in when they sign, as the editor shows it: the label printed
 * above it, a star where it is required, the box, and whose signature it goes with.
 */
const props = defineProps<{
    config: FillInConfig
}>()

const {t} = useI18n()

const label = computed(() => {
    const text = props.config.label?.trim() || t('documentTemplates.fillIn.labelMissing')
    return props.config.required ? `${text} *` : text
})

const signer = computed(() => props.config.signer
    ? t('documentTemplates.fillIn.filledInBy', {signer: t(`documentTemplates.signer.${props.config.signer}`)})
    : t('documentTemplates.signerMissing'))
</script>

<template>
    <div class="space-y-1" data-testid="fill-in-cell">
        <p class="text-sm">{{ label }}</p>
        <div class="h-7 border-b border-(--text)"/>
        <MutedText tag="p">{{ signer }}</MutedText>
    </div>
</template>
