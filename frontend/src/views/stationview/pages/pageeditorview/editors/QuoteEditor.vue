/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import LinkSearchInput from '@/components/input/text/LinkSearchInput.vue'
import MarkdownFieldInput from '@/components/input/text/MarkdownFieldInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {useConfigPatch} from '@/composables/useConfigPatch'
import type {CellEditorContentEmits, CellEditorContentStationProps} from '../cellTypes'

const props = defineProps<CellEditorContentStationProps>()
const emit = defineEmits<CellEditorContentEmits>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const patch = useConfigPatch(() => props.config, emit)
</script>

<template>
    <FieldLabel hint class="mb-1">{{ TS('quoteText') }}</FieldLabel>
    <MarkdownFieldInput :model-value="content" @update:model-value="emit('update:content', $event ?? '')"/>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
            <FieldLabel hint class="mb-1">{{ TS('quoteAuthor') }}</FieldLabel>
            <TextInput :model-value="(config.author as string) ?? ''" @update:model-value="patch({author: $event})"/>
        </div>
        <div>
            <FieldLabel hint class="mb-1">{{ TS('quoteAttribution') }}</FieldLabel>
            <LinkSearchInput :model-value="(config.attributionUrl as string) ?? ''" :station-uid="stationUid" no-files @update:model-value="patch({attributionUrl: $event})"/>
        </div>
    </div>
</template>
