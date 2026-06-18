/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PartnerStationListEditor from '../PartnerStationListEditor.vue'

const props = defineProps<{
    config: Record<string, unknown>
    stationUid: string
}>()

const emit = defineEmits<{
    'update:config': [value: Record<string, unknown>]
}>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

function patch(partial: Record<string, unknown>) {
    emit('update:config', {...props.config, ...partial})
}
</script>

<template>
    <FieldLabel hint class="mb-1">{{ TS('listTitle') }}</FieldLabel>
    <TextInput :model-value="(config.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
    <div class="flex items-end gap-2 pb-1">
        <ToggleInput
            :model-value="!!config.autoFillFromPartners"
            @update:model-value="patch({autoFillFromPartners: $event})"
        />
        <FieldLabel hint class="mb-0">{{ TS('autoFillFromPartners') }}</FieldLabel>
    </div>
    <template v-if="!config.autoFillFromPartners">
        <FieldLabel hint class="mb-1">{{ TS('partnerStationsList') }}</FieldLabel>
        <PartnerStationListEditor
            :station-uid="stationUid"
            :model-value="(config.stationUids as string[]) ?? []"
            @update:model-value="patch({stationUids: $event})"
        />
    </template>
</template>
