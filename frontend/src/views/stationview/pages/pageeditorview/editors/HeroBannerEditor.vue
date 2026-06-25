/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import LinkSearchInput from '@/components/input/text/LinkSearchInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import CellImagePicker from '../CellImagePicker.vue'
import {useConfigPatch} from '@/composables/useConfigPatch'
import type {CellEditorEmits, CellEditorPageProps} from '../cellTypes'

const props = defineProps<CellEditorPageProps>()
const emit = defineEmits<CellEditorEmits>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const patch = useConfigPatch(() => props.config, emit)
</script>

<template>
    <FieldLabel hint class="mb-1">{{ TS('heroImage') }}</FieldLabel>
    <CellImagePicker
        :image-hash="(config.imageHash as string) ?? null"
        :page-id="pageId"
        :station-uid="stationUid"
        @update:image-hash="patch({imageHash: $event})"
    />
    <FieldLabel hint class="mb-1">{{ TS('headline') }}</FieldLabel>
    <TextInput :model-value="(config.headline as string) ?? ''" @update:model-value="patch({headline: $event})"/>
    <FieldLabel hint class="mb-1">{{ TS('subtitle') }}</FieldLabel>
    <TextInput :model-value="(config.subtitle as string) ?? ''" @update:model-value="patch({subtitle: $event})"/>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
            <FieldLabel hint class="mb-1">{{ TS('ctaText') }}</FieldLabel>
            <TextInput :model-value="(config.ctaText as string) ?? ''" @update:model-value="patch({ctaText: $event})"/>
        </div>
        <div>
            <FieldLabel hint class="mb-1">{{ TS('ctaUrl') }}</FieldLabel>
            <LinkSearchInput :model-value="(config.ctaUrl as string) ?? ''" :station-uid="stationUid" no-files @update:model-value="patch({ctaUrl: $event})"/>
        </div>
    </div>
</template>
