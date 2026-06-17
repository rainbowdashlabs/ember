/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import MarkdownFieldInput from '@/components/input/text/MarkdownFieldInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {CalloutVariant} from '@/api/pageManage'

const props = defineProps<{
    config: Record<string, unknown>
    content: string
}>()

const emit = defineEmits<{
    'update:config': [value: Record<string, unknown>]
    'update:content': [value: string]
}>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

function patch(partial: Record<string, unknown>) {
    emit('update:config', {...props.config, ...partial})
}
</script>

<template>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
            <FieldLabel hint class="mb-1">{{ TS('calloutVariant') }}</FieldLabel>
            <SelectInput :model-value="(config.variant as string) ?? CalloutVariant.INFO" @update:model-value="patch({variant: $event})">
                <option :value="CalloutVariant.INFO">{{ t('stationPages.callout.info') }}</option>
                <option :value="CalloutVariant.WARNING">{{ t('stationPages.callout.warning') }}</option>
                <option :value="CalloutVariant.SUCCESS">{{ t('stationPages.callout.success') }}</option>
                <option :value="CalloutVariant.TIP">{{ t('stationPages.callout.tip') }}</option>
            </SelectInput>
        </div>
        <div>
            <FieldLabel hint class="mb-1">{{ TS('calloutTitle') }}</FieldLabel>
            <TextInput :model-value="(config.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
        </div>
    </div>
    <FieldLabel hint class="mb-1">{{ TS('calloutBody') }}</FieldLabel>
    <MarkdownFieldInput :model-value="content" @update:model-value="emit('update:content', $event ?? '')"/>
</template>
