/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {
    CalloutVariant,
    type CalloutConfig,
    type QuoteConfig,
    type DividerConfig,
    type SpacerConfig,
    type AccordionConfig,
    type PdfConfig,
    type FileDownloadConfig,
} from '@/api/pageManage'

const props = defineProps<{
    kind: 'CALLOUT' | 'QUOTE' | 'DIVIDER' | 'SPACER' | 'ACCORDION' | 'PDF' | 'FILE_DOWNLOAD'
    content: string
    config: Record<string, unknown>
}>()

const emit = defineEmits<{
    'update:content': [value: string]
    'update:config': [value: Record<string, unknown>]
}>()

const {t} = useI18n()

const callout = computed(() => props.config as CalloutConfig)
const quote = computed(() => props.config as QuoteConfig)
const divider = computed(() => props.config as DividerConfig)
const spacer = computed(() => props.config as SpacerConfig)
const accordion = computed(() => props.config as AccordionConfig)
const pdf = computed(() => props.config as PdfConfig)
const file = computed(() => props.config as FileDownloadConfig)

function patch(partial: Record<string, unknown>) {
    emit('update:config', {...props.config, ...partial})
}
</script>

<template>
    <div class="space-y-3">
        <!-- CALLOUT -->
        <template v-if="kind === 'CALLOUT'">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                    <FieldLabel hint class="mb-1">{{ t('stationPages.editor.calloutVariant') }}</FieldLabel>
                    <SelectInput :model-value="callout.variant ?? CalloutVariant.INFO" @update:model-value="patch({variant: $event})">
                        <option :value="CalloutVariant.INFO">{{ t('stationPages.callout.info') }}</option>
                        <option :value="CalloutVariant.WARNING">{{ t('stationPages.callout.warning') }}</option>
                        <option :value="CalloutVariant.SUCCESS">{{ t('stationPages.callout.success') }}</option>
                        <option :value="CalloutVariant.TIP">{{ t('stationPages.callout.tip') }}</option>
                    </SelectInput>
                </div>
                <div>
                    <FieldLabel hint class="mb-1">{{ t('stationPages.editor.calloutTitle') }}</FieldLabel>
                    <TextInput :model-value="callout.title ?? ''" @update:model-value="patch({title: $event})"/>
                </div>
            </div>
            <div>
                <FieldLabel hint class="mb-1">{{ t('stationPages.editor.calloutBody') }}</FieldLabel>
                <TextAreaInput :model-value="content" rows="4" @update:model-value="emit('update:content', $event ?? '')"/>
            </div>
        </template>

        <!-- QUOTE -->
        <template v-else-if="kind === 'QUOTE'">
            <div>
                <FieldLabel hint class="mb-1">{{ t('stationPages.editor.quoteText') }}</FieldLabel>
                <TextAreaInput :model-value="content" rows="4" @update:model-value="emit('update:content', $event ?? '')"/>
            </div>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                    <FieldLabel hint class="mb-1">{{ t('stationPages.editor.quoteAuthor') }}</FieldLabel>
                    <TextInput :model-value="quote.author ?? ''" @update:model-value="patch({author: $event})"/>
                </div>
                <div>
                    <FieldLabel hint class="mb-1">{{ t('stationPages.editor.quoteAttribution') }}</FieldLabel>
                    <TextInput :model-value="quote.attributionUrl ?? ''" placeholder="https://…" @update:model-value="patch({attributionUrl: $event})"/>
                </div>
            </div>
        </template>

        <!-- DIVIDER -->
        <template v-else-if="kind === 'DIVIDER'">
            <div>
                <FieldLabel hint class="mb-1">{{ t('stationPages.editor.dividerLabel') }}</FieldLabel>
                <TextInput :model-value="divider.label ?? ''" :placeholder="t('stationPages.editor.dividerLabelPlaceholder')" @update:model-value="patch({label: $event})"/>
            </div>
        </template>

        <!-- SPACER -->
        <template v-else-if="kind === 'SPACER'">
            <div>
                <FieldLabel hint class="mb-1">{{ t('stationPages.editor.spacerHeight') }}</FieldLabel>
                <NumberInput :model-value="spacer.heightPx ?? 32" @update:model-value="patch({heightPx: $event || null})"/>
            </div>
        </template>

        <!-- ACCORDION -->
        <template v-else-if="kind === 'ACCORDION'">
            <div>
                <FieldLabel hint class="mb-1">{{ t('stationPages.editor.accordionTitle') }}</FieldLabel>
                <TextInput :model-value="accordion.title ?? ''" @update:model-value="patch({title: $event})"/>
            </div>
            <div class="flex items-center gap-2">
                <ToggleInput :model-value="!!accordion.openByDefault" @update:model-value="patch({openByDefault: $event})"/>
                <span class="text-xs">{{ t('stationPages.editor.accordionOpenByDefault') }}</span>
            </div>
            <div>
                <FieldLabel hint class="mb-1">{{ t('stationPages.editor.accordionBody') }}</FieldLabel>
                <TextAreaInput :model-value="content" rows="6" @update:model-value="emit('update:content', $event ?? '')"/>
            </div>
        </template>

        <!-- PDF -->
        <template v-else-if="kind === 'PDF'">
            <div>
                <FieldLabel hint class="mb-1">{{ t('stationPages.editor.pdfUrl') }}</FieldLabel>
                <TextInput :model-value="pdf.url ?? ''" placeholder="https://…/datei.pdf" @update:model-value="patch({url: $event})"/>
            </div>
            <div>
                <FieldLabel hint class="mb-1">{{ t('stationPages.editor.pdfHeight') }}</FieldLabel>
                <NumberInput :model-value="pdf.heightPx ?? 600" @update:model-value="patch({heightPx: $event || null})"/>
            </div>
        </template>

        <!-- FILE_DOWNLOAD -->
        <template v-else-if="kind === 'FILE_DOWNLOAD'">
            <div>
                <FieldLabel hint class="mb-1">{{ t('stationPages.editor.fileUrl') }}</FieldLabel>
                <TextInput :model-value="file.url ?? ''" placeholder="https://…" @update:model-value="patch({url: $event})"/>
            </div>
            <div>
                <FieldLabel hint class="mb-1">{{ t('stationPages.editor.fileLabel') }}</FieldLabel>
                <TextInput :model-value="file.label ?? ''" @update:model-value="patch({label: $event})"/>
            </div>
            <div>
                <FieldLabel hint class="mb-1">{{ t('stationPages.editor.fileDescription') }}</FieldLabel>
                <TextInput :model-value="file.description ?? ''" @update:model-value="patch({description: $event})"/>
            </div>
        </template>
    </div>
</template>
