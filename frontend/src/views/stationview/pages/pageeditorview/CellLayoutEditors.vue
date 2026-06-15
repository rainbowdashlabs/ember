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
import {CalloutVariant, type LayoutKindName} from '@/api/pageManage'

const props = defineProps<{
    kind: LayoutKindName
    content: string
    config: Record<string, unknown>
}>()

const emit = defineEmits<{
    'update:content': [value: string]
    'update:config': [value: Record<string, unknown>]
}>()

const {t} = useI18n()

const cfg = computed(() => props.config as Record<string, unknown>)

function patch(partial: Record<string, unknown>) {
    emit('update:config', {...cfg.value, ...partial})
}

function readJsonItems(key: string): string {
    const items = cfg.value[key]
    if (!items) return '[]'
    try { return JSON.stringify(items, null, 2) } catch { return '[]' }
}

function writeJsonItems(key: string, raw: string) {
    try {
        const parsed = JSON.parse(raw)
        patch({[key]: parsed})
    } catch {
        // Swallow parse errors — keep last good value; user will fix the textarea.
    }
}

const TS = (k: string) => t(`stationPages.editor.${k}`)
</script>

<template>
    <div class="space-y-3">
        <!-- CALLOUT -->
        <template v-if="kind === 'CALLOUT'">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                    <FieldLabel hint class="mb-1">{{ TS('calloutVariant') }}</FieldLabel>
                    <SelectInput :model-value="(cfg.variant as string) ?? CalloutVariant.INFO" @update:model-value="patch({variant: $event})">
                        <option :value="CalloutVariant.INFO">{{ t('stationPages.callout.info') }}</option>
                        <option :value="CalloutVariant.WARNING">{{ t('stationPages.callout.warning') }}</option>
                        <option :value="CalloutVariant.SUCCESS">{{ t('stationPages.callout.success') }}</option>
                        <option :value="CalloutVariant.TIP">{{ t('stationPages.callout.tip') }}</option>
                    </SelectInput>
                </div>
                <div>
                    <FieldLabel hint class="mb-1">{{ TS('calloutTitle') }}</FieldLabel>
                    <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
                </div>
            </div>
            <FieldLabel hint class="mb-1">{{ TS('calloutBody') }}</FieldLabel>
            <TextAreaInput :model-value="content" rows="4" @update:model-value="emit('update:content', $event ?? '')"/>
        </template>

        <!-- QUOTE -->
        <template v-else-if="kind === 'QUOTE'">
            <FieldLabel hint class="mb-1">{{ TS('quoteText') }}</FieldLabel>
            <TextAreaInput :model-value="content" rows="4" @update:model-value="emit('update:content', $event ?? '')"/>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div><FieldLabel hint class="mb-1">{{ TS('quoteAuthor') }}</FieldLabel><TextInput :model-value="(cfg.author as string) ?? ''" @update:model-value="patch({author: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('quoteAttribution') }}</FieldLabel><TextInput :model-value="(cfg.attributionUrl as string) ?? ''" placeholder="https://…" @update:model-value="patch({attributionUrl: $event})"/></div>
            </div>
        </template>

        <!-- DIVIDER -->
        <template v-else-if="kind === 'DIVIDER'">
            <FieldLabel hint class="mb-1">{{ TS('dividerLabel') }}</FieldLabel>
            <TextInput :model-value="(cfg.label as string) ?? ''" :placeholder="TS('dividerLabelPlaceholder')" @update:model-value="patch({label: $event})"/>
        </template>

        <!-- SPACER -->
        <template v-else-if="kind === 'SPACER'">
            <FieldLabel hint class="mb-1">{{ TS('spacerHeight') }}</FieldLabel>
            <NumberInput :model-value="(cfg.heightPx as number) ?? 32" @update:model-value="patch({heightPx: $event || null})"/>
        </template>

        <!-- ACCORDION -->
        <template v-else-if="kind === 'ACCORDION'">
            <FieldLabel hint class="mb-1">{{ TS('accordionTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
            <label class="flex items-center gap-2 text-xs"><ToggleInput :model-value="!!cfg.openByDefault" @update:model-value="patch({openByDefault: $event})"/>{{ TS('accordionOpenByDefault') }}</label>
            <FieldLabel hint class="mb-1">{{ TS('accordionBody') }}</FieldLabel>
            <TextAreaInput :model-value="content" rows="6" @update:model-value="emit('update:content', $event ?? '')"/>
        </template>

        <!-- PDF -->
        <template v-else-if="kind === 'PDF'">
            <FieldLabel hint class="mb-1">{{ TS('pdfUrl') }}</FieldLabel>
            <TextInput :model-value="(cfg.url as string) ?? ''" placeholder="https://…/datei.pdf" @update:model-value="patch({url: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('pdfHeight') }}</FieldLabel>
            <NumberInput :model-value="(cfg.heightPx as number) ?? 600" @update:model-value="patch({heightPx: $event || null})"/>
        </template>

        <!-- FILE_DOWNLOAD -->
        <template v-else-if="kind === 'FILE_DOWNLOAD'">
            <FieldLabel hint class="mb-1">{{ TS('fileUrl') }}</FieldLabel>
            <TextInput :model-value="(cfg.url as string) ?? ''" placeholder="https://…" @update:model-value="patch({url: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('fileLabel') }}</FieldLabel>
            <TextInput :model-value="(cfg.label as string) ?? ''" @update:model-value="patch({label: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('fileDescription') }}</FieldLabel>
            <TextInput :model-value="(cfg.description as string) ?? ''" @update:model-value="patch({description: $event})"/>
        </template>

        <!-- COUNTDOWN -->
        <template v-else-if="kind === 'COUNTDOWN'">
            <FieldLabel hint class="mb-1">{{ TS('countdownTarget') }}</FieldLabel>
            <TextInput :model-value="(cfg.targetDate as string) ?? ''" placeholder="2026-12-31T18:00:00Z" @update:model-value="patch({targetDate: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('countdownLabel') }}</FieldLabel>
            <TextInput :model-value="(cfg.label as string) ?? ''" @update:model-value="patch({label: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('countdownSublabel') }}</FieldLabel>
            <TextInput :model-value="(cfg.sublabel as string) ?? ''" @update:model-value="patch({sublabel: $event})"/>
        </template>

        <!-- FEATURED_EVENT -->
        <template v-else-if="kind === 'FEATURED_EVENT'">
            <FieldLabel hint class="mb-1">{{ TS('eventTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div><FieldLabel hint class="mb-1">{{ TS('eventDate') }}</FieldLabel><TextInput :model-value="(cfg.date as string) ?? ''" @update:model-value="patch({date: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('eventLocation') }}</FieldLabel><TextInput :model-value="(cfg.location as string) ?? ''" @update:model-value="patch({location: $event})"/></div>
            </div>
            <FieldLabel hint class="mb-1">{{ TS('eventDescription') }}</FieldLabel>
            <TextAreaInput :model-value="(cfg.description as string) ?? ''" rows="3" @update:model-value="patch({description: $event ?? ''})"/>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div><FieldLabel hint class="mb-1">{{ TS('ctaText') }}</FieldLabel><TextInput :model-value="(cfg.ctaText as string) ?? ''" @update:model-value="patch({ctaText: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('ctaUrl') }}</FieldLabel><TextInput :model-value="(cfg.ctaUrl as string) ?? ''" @update:model-value="patch({ctaUrl: $event})"/></div>
            </div>
        </template>

        <!-- UPCOMING_EVENTS, PARTNER_STATIONS, OFFICERS_ROW, STATS_COUNTER, TABS, ACHIEVEMENTS, IMAGE_GALLERY → JSON list -->
        <template v-else-if="['UPCOMING_EVENTS', 'PARTNER_STATIONS', 'OFFICERS_ROW', 'STATS_COUNTER', 'TABS', 'ACHIEVEMENTS', 'IMAGE_GALLERY'].includes(kind)">
            <div v-if="kind !== 'STATS_COUNTER' && kind !== 'IMAGE_GALLERY'">
                <FieldLabel hint class="mb-1">{{ TS('listTitle') }}</FieldLabel>
                <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
            </div>
            <div v-if="kind === 'IMAGE_GALLERY'">
                <FieldLabel hint class="mb-1">{{ TS('galleryColumns') }}</FieldLabel>
                <NumberInput :model-value="(cfg.columns as number) ?? 3" @update:model-value="patch({columns: $event || null})"/>
                <FieldLabel hint class="mb-1 mt-2">{{ TS('galleryImageIds') }}</FieldLabel>
                <TextAreaInput
                    :model-value="JSON.stringify(cfg.imageIds ?? [])"
                    rows="3"
                    placeholder="[1, 2, 3]"
                    @update:model-value="(v: string | undefined) => { try { patch({imageIds: JSON.parse(v ?? '[]')}) } catch {} }"
                />
            </div>
            <div v-else>
                <FieldLabel hint class="mb-1">{{ TS('jsonItems') }}</FieldLabel>
                <TextAreaInput
                    :model-value="readJsonItems('items')"
                    rows="6"
                    @update:model-value="(v: string | undefined) => writeJsonItems('items', v ?? '[]')"
                />
                <p class="text-xs text-(--text-muted) italic">{{ TS('jsonItemsHint') }}</p>
            </div>
        </template>

        <!-- KB_ARTICLE -->
        <template v-else-if="kind === 'KB_ARTICLE'">
            <FieldLabel hint class="mb-1">{{ TS('articleId') }}</FieldLabel>
            <NumberInput :model-value="(cfg.articleId as number) ?? undefined" @update:model-value="patch({articleId: $event || null})"/>
            <FieldLabel hint class="mb-1">{{ TS('linkTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.fallbackTitle as string) ?? ''" @update:model-value="patch({fallbackTitle: $event})"/>
        </template>

        <!-- NEWS_TEASER -->
        <template v-else-if="kind === 'NEWS_TEASER'">
            <FieldLabel hint class="mb-1">{{ TS('newsTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div><FieldLabel hint class="mb-1">{{ TS('newsDate') }}</FieldLabel><TextInput :model-value="(cfg.date as string) ?? ''" @update:model-value="patch({date: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('linkUrl') }}</FieldLabel><TextInput :model-value="(cfg.url as string) ?? ''" @update:model-value="patch({url: $event})"/></div>
            </div>
            <FieldLabel hint class="mb-1">{{ TS('newsSummary') }}</FieldLabel>
            <TextAreaInput :model-value="(cfg.summary as string) ?? ''" rows="3" @update:model-value="patch({summary: $event ?? ''})"/>
            <FieldLabel hint class="mb-1">{{ TS('imageUrl') }}</FieldLabel>
            <TextInput :model-value="(cfg.imageUrl as string) ?? ''" @update:model-value="patch({imageUrl: $event})"/>
        </template>

        <!-- PAGE_LINK -->
        <template v-else-if="kind === 'PAGE_LINK'">
            <FieldLabel hint class="mb-1">{{ TS('pageId') }}</FieldLabel>
            <NumberInput :model-value="(cfg.pageId as number) ?? undefined" @update:model-value="patch({pageId: $event || null})"/>
            <FieldLabel hint class="mb-1">{{ TS('linkTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.fallbackTitle as string) ?? ''" @update:model-value="patch({fallbackTitle: $event})"/>
        </template>

        <!-- MAP -->
        <template v-else-if="kind === 'MAP'">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div><FieldLabel hint class="mb-1">{{ TS('mapLat') }}</FieldLabel><NumberInput :model-value="(cfg.latitude as number) ?? undefined" @update:model-value="patch({latitude: $event || null})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('mapLon') }}</FieldLabel><NumberInput :model-value="(cfg.longitude as number) ?? undefined" @update:model-value="patch({longitude: $event || null})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('mapZoom') }}</FieldLabel><NumberInput :model-value="(cfg.zoom as number) ?? 14" @update:model-value="patch({zoom: $event || null})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('mapHeight') }}</FieldLabel><NumberInput :model-value="(cfg.heightPx as number) ?? 320" @update:model-value="patch({heightPx: $event || null})"/></div>
            </div>
            <FieldLabel hint class="mb-1">{{ TS('mapLabel') }}</FieldLabel>
            <TextInput :model-value="(cfg.label as string) ?? ''" @update:model-value="patch({label: $event})"/>
        </template>

        <!-- ADDRESS_CARD -->
        <template v-else-if="kind === 'ADDRESS_CARD'">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div><FieldLabel hint class="mb-1">{{ TS('addrLabel') }}</FieldLabel><TextInput :model-value="(cfg.label as string) ?? ''" @update:model-value="patch({label: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('addrLine') }}</FieldLabel><TextInput :model-value="(cfg.addressLine as string) ?? ''" @update:model-value="patch({addressLine: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('addrPostal') }}</FieldLabel><TextInput :model-value="(cfg.postalCode as string) ?? ''" @update:model-value="patch({postalCode: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('addrCity') }}</FieldLabel><TextInput :model-value="(cfg.city as string) ?? ''" @update:model-value="patch({city: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('addrCountry') }}</FieldLabel><TextInput :model-value="(cfg.country as string) ?? ''" @update:model-value="patch({country: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('addrMapUrl') }}</FieldLabel><TextInput :model-value="(cfg.mapUrl as string) ?? ''" @update:model-value="patch({mapUrl: $event})"/></div>
            </div>
        </template>

        <!-- FEDERATED_EVENT -->
        <template v-else-if="kind === 'FEDERATED_EVENT'">
            <FieldLabel hint class="mb-1">{{ TS('eventTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div><FieldLabel hint class="mb-1">{{ TS('eventDate') }}</FieldLabel><TextInput :model-value="(cfg.date as string) ?? ''" @update:model-value="patch({date: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('partnerName') }}</FieldLabel><TextInput :model-value="(cfg.partnerName as string) ?? ''" @update:model-value="patch({partnerName: $event})"/></div>
            </div>
            <FieldLabel hint class="mb-1">{{ TS('linkUrl') }}</FieldLabel>
            <TextInput :model-value="(cfg.url as string) ?? ''" @update:model-value="patch({url: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('eventDescription') }}</FieldLabel>
            <TextAreaInput :model-value="(cfg.description as string) ?? ''" rows="3" @update:model-value="patch({description: $event ?? ''})"/>
        </template>

        <!-- MEMBER_SPOTLIGHT -->
        <template v-else-if="kind === 'MEMBER_SPOTLIGHT'">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div><FieldLabel hint class="mb-1">{{ TS('memberName') }}</FieldLabel><TextInput :model-value="(cfg.name as string) ?? ''" @update:model-value="patch({name: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('memberRole') }}</FieldLabel><TextInput :model-value="(cfg.role as string) ?? ''" @update:model-value="patch({role: $event})"/></div>
            </div>
            <FieldLabel hint class="mb-1">{{ TS('imageUrl') }}</FieldLabel>
            <TextInput :model-value="(cfg.imageUrl as string) ?? ''" @update:model-value="patch({imageUrl: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('memberBlurb') }}</FieldLabel>
            <TextAreaInput :model-value="(cfg.blurb as string) ?? ''" rows="3" @update:model-value="patch({blurb: $event ?? ''})"/>
        </template>

        <!-- HERO_BANNER -->
        <template v-else-if="kind === 'HERO_BANNER'">
            <FieldLabel hint class="mb-1">{{ TS('imageId') }}</FieldLabel>
            <NumberInput :model-value="(cfg.imageId as number) ?? undefined" @update:model-value="patch({imageId: $event || null})"/>
            <FieldLabel hint class="mb-1">{{ TS('headline') }}</FieldLabel>
            <TextInput :model-value="(cfg.headline as string) ?? ''" @update:model-value="patch({headline: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('subtitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.subtitle as string) ?? ''" @update:model-value="patch({subtitle: $event})"/>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div><FieldLabel hint class="mb-1">{{ TS('ctaText') }}</FieldLabel><TextInput :model-value="(cfg.ctaText as string) ?? ''" @update:model-value="patch({ctaText: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('ctaUrl') }}</FieldLabel><TextInput :model-value="(cfg.ctaUrl as string) ?? ''" @update:model-value="patch({ctaUrl: $event})"/></div>
            </div>
        </template>

        <!-- PAST_EVENT_RECAP -->
        <template v-else-if="kind === 'PAST_EVENT_RECAP'">
            <FieldLabel hint class="mb-1">{{ TS('eventTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div><FieldLabel hint class="mb-1">{{ TS('eventDate') }}</FieldLabel><TextInput :model-value="(cfg.date as string) ?? ''" @update:model-value="patch({date: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('imageId') }}</FieldLabel><NumberInput :model-value="(cfg.imageId as number) ?? undefined" @update:model-value="patch({imageId: $event || null})"/></div>
            </div>
            <FieldLabel hint class="mb-1">{{ TS('eventDescription') }}</FieldLabel>
            <TextAreaInput :model-value="(cfg.summary as string) ?? ''" rows="4" @update:model-value="patch({summary: $event ?? ''})"/>
        </template>

        <!-- EXTERNAL_LINK_CARD -->
        <template v-else-if="kind === 'EXTERNAL_LINK_CARD'">
            <FieldLabel hint class="mb-1">{{ TS('linkUrl') }}</FieldLabel>
            <TextInput :model-value="(cfg.url as string) ?? ''" @update:model-value="patch({url: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('linkTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('linkDescription') }}</FieldLabel>
            <TextAreaInput :model-value="(cfg.description as string) ?? ''" rows="2" @update:model-value="patch({description: $event ?? ''})"/>
            <FieldLabel hint class="mb-1">{{ TS('imageUrl') }}</FieldLabel>
            <TextInput :model-value="(cfg.imageUrl as string) ?? ''" @update:model-value="patch({imageUrl: $event})"/>
        </template>

        <!-- NEWSLETTER_SIGNUP -->
        <template v-else-if="kind === 'NEWSLETTER_SIGNUP'">
            <FieldLabel hint class="mb-1">{{ TS('newsletterTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('newsletterDescription') }}</FieldLabel>
            <TextAreaInput :model-value="(cfg.description as string) ?? ''" rows="2" @update:model-value="patch({description: $event ?? ''})"/>
            <FieldLabel hint class="mb-1">{{ TS('feedUrl') }}</FieldLabel>
            <TextInput :model-value="(cfg.feedUrl as string) ?? ''" @update:model-value="patch({feedUrl: $event})"/>
        </template>

        <!-- AUDIO_EMBED -->
        <template v-else-if="kind === 'AUDIO_EMBED'">
            <FieldLabel hint class="mb-1">{{ TS('audioUrl') }}</FieldLabel>
            <TextInput :model-value="(cfg.url as string) ?? ''" @update:model-value="patch({url: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('audioTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
        </template>

        <!-- POLL_EMBED -->
        <template v-else-if="kind === 'POLL_EMBED'">
            <FieldLabel hint class="mb-1">{{ TS('pollTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('linkUrl') }}</FieldLabel>
            <TextInput :model-value="(cfg.url as string) ?? ''" @update:model-value="patch({url: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('pollDescription') }}</FieldLabel>
            <TextAreaInput :model-value="(cfg.description as string) ?? ''" rows="2" @update:model-value="patch({description: $event ?? ''})"/>
        </template>

        <!-- QUIZ_TEASER -->
        <template v-else-if="kind === 'QUIZ_TEASER'">
            <FieldLabel hint class="mb-1">{{ TS('quizTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('quizDescription') }}</FieldLabel>
            <TextAreaInput :model-value="(cfg.description as string) ?? ''" rows="2" @update:model-value="patch({description: $event ?? ''})"/>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div><FieldLabel hint class="mb-1">{{ TS('ctaText') }}</FieldLabel><TextInput :model-value="(cfg.ctaText as string) ?? ''" @update:model-value="patch({ctaText: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('linkUrl') }}</FieldLabel><TextInput :model-value="(cfg.url as string) ?? ''" @update:model-value="patch({url: $event})"/></div>
            </div>
        </template>

        <!-- APPLICATION_CTA -->
        <template v-else-if="kind === 'APPLICATION_CTA'">
            <FieldLabel hint class="mb-1">{{ TS('headline') }}</FieldLabel>
            <TextInput :model-value="(cfg.headline as string) ?? ''" @update:model-value="patch({headline: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('body') }}</FieldLabel>
            <TextAreaInput :model-value="(cfg.body as string) ?? ''" rows="3" @update:model-value="patch({body: $event ?? ''})"/>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div><FieldLabel hint class="mb-1">{{ TS('ctaText') }}</FieldLabel><TextInput :model-value="(cfg.ctaText as string) ?? ''" @update:model-value="patch({ctaText: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('ctaUrl') }}</FieldLabel><TextInput :model-value="(cfg.ctaUrl as string) ?? ''" @update:model-value="patch({ctaUrl: $event})"/></div>
            </div>
        </template>

        <!-- CODE_BLOCK -->
        <template v-else-if="kind === 'CODE_BLOCK'">
            <FieldLabel hint class="mb-1">{{ TS('codeLanguage') }}</FieldLabel>
            <TextInput :model-value="(cfg.language as string) ?? ''" placeholder="java, ts, …" @update:model-value="patch({language: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('codeContent') }}</FieldLabel>
            <TextAreaInput :model-value="content" rows="8" @update:model-value="emit('update:content', $event ?? '')"/>
        </template>
    </div>
</template>
