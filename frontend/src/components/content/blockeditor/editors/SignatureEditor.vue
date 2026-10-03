/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SignerSelect from '@/components/documents/SignerSelect.vue'
import {SignatureRole} from '@/api/generated/schema'
import {useConfigPatch} from '@/composables/useConfigPatch'
import CellMarkdownInline from '../CellMarkdownInline.vue'
import type {CellEditorContentEmits, CellEditorContentProps} from '../cellTypes'

/**
 * Who signs on a signature line and the short text below it. A new line starts with the person who
 * issues the letter, which is the most common one.
 */
const props = defineProps<CellEditorContentProps>()
const emit = defineEmits<CellEditorContentEmits>()

const {t} = useI18n()

const patch = useConfigPatch(() => props.config, emit)

onMounted(() => {
    if (!props.config.signer) patch({signer: SignatureRole.ISSUER})
})
</script>

<template>
    <SignerSelect :model-value="(config.signer as SignatureRole | undefined)" @update:model-value="patch({signer: $event})"/>
    <FieldLabel hint class="mb-1">{{ t('documentTemplates.signatureText') }}</FieldLabel>
    <CellMarkdownInline :content="content" @update:content="emit('update:content', $event)"/>
</template>
