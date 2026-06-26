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
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {ExternalLinkImageDisplay} from '@/api/pageManage'
import {useConfigPatch} from '@/composables/useConfigPatch'
import type {CellEditorEmits, CellEditorStationProps} from '../cellTypes'

const props = defineProps<CellEditorStationProps>()
const emit = defineEmits<CellEditorEmits>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const patch = useConfigPatch(() => props.config, emit)
</script>

<template>
    <FieldLabel hint class="mb-1">{{ TS('linkUrl') }}</FieldLabel>
    <TextInput :model-value="(config.url as string) ?? ''" @update:model-value="patch({url: $event})"/>
    <FieldLabel hint class="mb-1">{{ TS('linkTitle') }}</FieldLabel>
    <TextInput :model-value="(config.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
    <FieldLabel hint class="mb-1">{{ TS('linkDescription') }}</FieldLabel>
    <MarkdownFieldInput :model-value="(config.description as string) ?? ''" @update:model-value="patch({description: $event ?? ''})"/>
    <FieldLabel hint class="mb-1">{{ TS('imageUrl') }}</FieldLabel>
    <LinkSearchInput :model-value="(config.imageUrl as string) ?? ''" :station-uid="stationUid" mime-prefix="image/" @update:model-value="patch({imageUrl: $event})"/>
    <template v-if="config.imageUrl">
        <FieldLabel hint class="mb-1">{{ TS('linkImageDisplay') }}</FieldLabel>
        <SelectInput :model-value="(config.imageDisplay as string) ?? ExternalLinkImageDisplay.BANNER" @update:model-value="patch({imageDisplay: $event})">
            <option :value="ExternalLinkImageDisplay.BANNER">{{ TS('linkImageDisplayBanner') }}</option>
            <option :value="ExternalLinkImageDisplay.ICON">{{ TS('linkImageDisplayIcon') }}</option>
        </SelectInput>
    </template>
</template>
