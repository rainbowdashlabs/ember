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
import LinkSearchInput from '@/components/input/text/LinkSearchInput.vue'
import MarkdownFieldInput from '@/components/input/text/MarkdownFieldInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import TimeInput from '@/components/input/datetime/TimeInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {CalloutVariant, type LayoutKindName} from '@/api/pageManage'
import CellListItemsEditor, {type ItemFieldDef} from './CellListItemsEditor.vue'
import CellImagePicker from './CellImagePicker.vue'

const props = defineProps<{
    kind: LayoutKindName
    content: string
    config: Record<string, unknown>
    pageId: number
    stationUid: string
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

// Reusable field definitions for the inline list editor.
const EVENT_FIELDS: ItemFieldDef[] = [
    {key: 'title', label: TS('eventTitle'), type: 'text', span: 2},
    {key: 'date', label: TS('eventDate'), type: 'text'},
    {key: 'location', label: TS('eventLocation'), type: 'text'},
    {key: 'url', label: TS('linkUrl'), type: 'text', span: 2},
]
const PARTNER_FIELDS: ItemFieldDef[] = [
    {key: 'name', label: TS('partnerName'), type: 'text'},
    {key: 'url', label: TS('linkUrl'), type: 'text'},
    {key: 'distanceKm', label: TS('partnerDistance'), type: 'number'},
]
const OFFICER_FIELDS: ItemFieldDef[] = [
    {key: 'name', label: TS('memberName'), type: 'text'},
    {key: 'role', label: TS('memberRole'), type: 'text'},
    {key: 'imageUrl', label: TS('imageUrl'), type: 'text', span: 2},
]
const STAT_FIELDS: ItemFieldDef[] = [
    {key: 'label', label: TS('statLabel'), type: 'text'},
    {key: 'value', label: TS('statValue'), type: 'text'},
    {key: 'suffix', label: TS('statSuffix'), type: 'text'},
]
const TAB_FIELDS: ItemFieldDef[] = [
    {key: 'title', label: TS('tabTitle'), type: 'text'},
    {key: 'body', label: TS('tabBody'), type: 'textarea', span: 2},
]
const ACHIEVEMENT_FIELDS: ItemFieldDef[] = [
    {key: 'title', label: TS('achievementTitle'), type: 'text'},
    {key: 'year', label: TS('achievementYear'), type: 'text'},
    {key: 'description', label: TS('achievementDescription'), type: 'textarea', span: 2},
]
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
            <MarkdownFieldInput :model-value="content" @update:model-value="emit('update:content', $event ?? '')"/>
        </template>

        <!-- QUOTE -->
        <template v-else-if="kind === 'QUOTE'">
            <FieldLabel hint class="mb-1">{{ TS('quoteText') }}</FieldLabel>
            <MarkdownFieldInput :model-value="content" @update:model-value="emit('update:content', $event ?? '')"/>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div><FieldLabel hint class="mb-1">{{ TS('quoteAuthor') }}</FieldLabel><TextInput :model-value="(cfg.author as string) ?? ''" @update:model-value="patch({author: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('quoteAttribution') }}</FieldLabel><LinkSearchInput :model-value="(cfg.attributionUrl as string) ?? ''" :station-uid="stationUid" @update:model-value="patch({attributionUrl: $event})"/></div>
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
            <MarkdownFieldInput :model-value="content" @update:model-value="emit('update:content', $event ?? '')"/>
        </template>

        <!-- PDF -->
        <template v-else-if="kind === 'PDF'">
            <FieldLabel hint class="mb-1">{{ TS('pdfUrl') }}</FieldLabel>
            <LinkSearchInput :model-value="(cfg.url as string) ?? ''" :station-uid="stationUid" @update:model-value="patch({url: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('pdfHeight') }}</FieldLabel>
            <NumberInput :model-value="(cfg.heightPx as number) ?? 600" @update:model-value="patch({heightPx: $event || null})"/>
        </template>

        <!-- FILE_DOWNLOAD -->
        <template v-else-if="kind === 'FILE_DOWNLOAD'">
            <FieldLabel hint class="mb-1">{{ TS('fileUrl') }}</FieldLabel>
            <LinkSearchInput :model-value="(cfg.url as string) ?? ''" :station-uid="stationUid" @update:model-value="patch({url: $event})"/>
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
            <MarkdownFieldInput :model-value="(cfg.description as string) ?? ''" @update:model-value="patch({description: $event ?? ''})"/>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div><FieldLabel hint class="mb-1">{{ TS('ctaText') }}</FieldLabel><TextInput :model-value="(cfg.ctaText as string) ?? ''" @update:model-value="patch({ctaText: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('ctaUrl') }}</FieldLabel><LinkSearchInput :model-value="(cfg.ctaUrl as string) ?? ''" :station-uid="stationUid" @update:model-value="patch({ctaUrl: $event})"/></div>
            </div>
        </template>

        <!-- UPCOMING_EVENTS -->
        <template v-else-if="kind === 'UPCOMING_EVENTS'">
            <FieldLabel hint class="mb-1">{{ TS('listTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
            <CellListItemsEditor
                :items="cfg.items as Record<string, unknown>[]"
                :fields="EVENT_FIELDS"
                :add-label="TS('addEvent')"
                @update:items="patch({items: $event})"
            />
        </template>

        <!-- PARTNER_STATIONS -->
        <template v-else-if="kind === 'PARTNER_STATIONS'">
            <FieldLabel hint class="mb-1">{{ TS('listTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
            <CellListItemsEditor
                :items="cfg.items as Record<string, unknown>[]"
                :fields="PARTNER_FIELDS"
                :add-label="TS('addPartner')"
                :grid-cols="3"
                @update:items="patch({items: $event})"
            />
        </template>

        <!-- OFFICERS_ROW -->
        <template v-else-if="kind === 'OFFICERS_ROW'">
            <FieldLabel hint class="mb-1">{{ TS('listTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
            <CellListItemsEditor
                :items="cfg.items as Record<string, unknown>[]"
                :fields="OFFICER_FIELDS"
                :add-label="TS('addOfficer')"
                @update:items="patch({items: $event})"
            />
        </template>

        <!-- STATS_COUNTER -->
        <template v-else-if="kind === 'STATS_COUNTER'">
            <CellListItemsEditor
                :items="cfg.items as Record<string, unknown>[]"
                :fields="STAT_FIELDS"
                :add-label="TS('addStat')"
                :grid-cols="3"
                @update:items="patch({items: $event})"
            />
        </template>

        <!-- TABS -->
        <template v-else-if="kind === 'TABS'">
            <CellListItemsEditor
                :items="cfg.items as Record<string, unknown>[]"
                :fields="TAB_FIELDS"
                :add-label="TS('addTab')"
                @update:items="patch({items: $event})"
            />
        </template>

        <!-- ACHIEVEMENTS -->
        <template v-else-if="kind === 'ACHIEVEMENTS'">
            <FieldLabel hint class="mb-1">{{ TS('listTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
            <CellListItemsEditor
                :items="cfg.items as Record<string, unknown>[]"
                :fields="ACHIEVEMENT_FIELDS"
                :add-label="TS('addAchievement')"
                @update:items="patch({items: $event})"
            />
        </template>

        <!-- IMAGE_GALLERY -->
        <template v-else-if="kind === 'IMAGE_GALLERY'">
            <FieldLabel hint class="mb-1">{{ TS('galleryColumns') }}</FieldLabel>
            <NumberInput :model-value="(cfg.columns as number) ?? 3" @update:model-value="patch({columns: $event || null})"/>
            <FieldLabel hint class="mb-1 mt-2">{{ TS('galleryImages') }}</FieldLabel>
            <CellImagePicker
                multi
                :image-ids="cfg.imageIds as number[]"
                :page-id="pageId"
                :station-uid="stationUid"
                @update:image-ids="patch({imageIds: $event})"
            />
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
                <div><FieldLabel hint class="mb-1">{{ TS('linkUrl') }}</FieldLabel><LinkSearchInput :model-value="(cfg.url as string) ?? ''" :station-uid="stationUid" @update:model-value="patch({url: $event})"/></div>
            </div>
            <FieldLabel hint class="mb-1">{{ TS('newsSummary') }}</FieldLabel>
            <MarkdownFieldInput :model-value="(cfg.summary as string) ?? ''" @update:model-value="patch({summary: $event ?? ''})"/>
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
            <LinkSearchInput :model-value="(cfg.url as string) ?? ''" :station-uid="stationUid" @update:model-value="patch({url: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('eventDescription') }}</FieldLabel>
            <MarkdownFieldInput :model-value="(cfg.description as string) ?? ''" @update:model-value="patch({description: $event ?? ''})"/>
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
            <MarkdownFieldInput :model-value="(cfg.blurb as string) ?? ''" @update:model-value="patch({blurb: $event ?? ''})"/>
        </template>

        <!-- HERO_BANNER -->
        <template v-else-if="kind === 'HERO_BANNER'">
            <FieldLabel hint class="mb-1">{{ TS('heroImage') }}</FieldLabel>
            <CellImagePicker
                :image-id="(cfg.imageId as number) ?? null"
                :page-id="pageId"
                :station-uid="stationUid"
                @update:image-id="patch({imageId: $event})"
            />
            <FieldLabel hint class="mb-1">{{ TS('headline') }}</FieldLabel>
            <TextInput :model-value="(cfg.headline as string) ?? ''" @update:model-value="patch({headline: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('subtitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.subtitle as string) ?? ''" @update:model-value="patch({subtitle: $event})"/>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div><FieldLabel hint class="mb-1">{{ TS('ctaText') }}</FieldLabel><TextInput :model-value="(cfg.ctaText as string) ?? ''" @update:model-value="patch({ctaText: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('ctaUrl') }}</FieldLabel><LinkSearchInput :model-value="(cfg.ctaUrl as string) ?? ''" :station-uid="stationUid" @update:model-value="patch({ctaUrl: $event})"/></div>
            </div>
        </template>

        <!-- PAST_EVENT_RECAP -->
        <template v-else-if="kind === 'PAST_EVENT_RECAP'">
            <FieldLabel hint class="mb-1">{{ TS('eventTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div><FieldLabel hint class="mb-1">{{ TS('eventDate') }}</FieldLabel><TextInput :model-value="(cfg.date as string) ?? ''" @update:model-value="patch({date: $event})"/></div>
            </div>
            <FieldLabel hint class="mb-1">{{ TS('recapImage') }}</FieldLabel>
            <CellImagePicker
                :image-id="(cfg.imageId as number) ?? null"
                :page-id="pageId"
                :station-uid="stationUid"
                @update:image-id="patch({imageId: $event})"
            />
            <FieldLabel hint class="mb-1">{{ TS('eventDescription') }}</FieldLabel>
            <MarkdownFieldInput :model-value="(cfg.summary as string) ?? ''" @update:model-value="patch({summary: $event ?? ''})"/>
        </template>

        <!-- EXTERNAL_LINK_CARD -->
        <template v-else-if="kind === 'EXTERNAL_LINK_CARD'">
            <FieldLabel hint class="mb-1">{{ TS('linkUrl') }}</FieldLabel>
            <LinkSearchInput :model-value="(cfg.url as string) ?? ''" :station-uid="stationUid" @update:model-value="patch({url: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('linkTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('linkDescription') }}</FieldLabel>
            <MarkdownFieldInput :model-value="(cfg.description as string) ?? ''" @update:model-value="patch({description: $event ?? ''})"/>
            <FieldLabel hint class="mb-1">{{ TS('imageUrl') }}</FieldLabel>
            <TextInput :model-value="(cfg.imageUrl as string) ?? ''" @update:model-value="patch({imageUrl: $event})"/>
        </template>

        <!-- NEWSLETTER_SIGNUP -->
        <template v-else-if="kind === 'NEWSLETTER_SIGNUP'">
            <FieldLabel hint class="mb-1">{{ TS('newsletterTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('newsletterDescription') }}</FieldLabel>
            <MarkdownFieldInput :model-value="(cfg.description as string) ?? ''" @update:model-value="patch({description: $event ?? ''})"/>
            <FieldLabel hint class="mb-1">{{ TS('feedUrl') }}</FieldLabel>
            <TextInput :model-value="(cfg.feedUrl as string) ?? ''" @update:model-value="patch({feedUrl: $event})"/>
        </template>

        <!-- AUDIO_EMBED -->
        <template v-else-if="kind === 'AUDIO_EMBED'">
            <FieldLabel hint class="mb-1">{{ TS('audioUrl') }}</FieldLabel>
            <LinkSearchInput :model-value="(cfg.url as string) ?? ''" :station-uid="stationUid" @update:model-value="patch({url: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('audioTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
        </template>

        <!-- POLL_EMBED -->
        <template v-else-if="kind === 'POLL_EMBED'">
            <FieldLabel hint class="mb-1">{{ TS('pollTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('linkUrl') }}</FieldLabel>
            <LinkSearchInput :model-value="(cfg.url as string) ?? ''" :station-uid="stationUid" @update:model-value="patch({url: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('pollDescription') }}</FieldLabel>
            <MarkdownFieldInput :model-value="(cfg.description as string) ?? ''" @update:model-value="patch({description: $event ?? ''})"/>
        </template>

        <!-- QUIZ_TEASER -->
        <template v-else-if="kind === 'QUIZ_TEASER'">
            <FieldLabel hint class="mb-1">{{ TS('quizTitle') }}</FieldLabel>
            <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('quizDescription') }}</FieldLabel>
            <MarkdownFieldInput :model-value="(cfg.description as string) ?? ''" @update:model-value="patch({description: $event ?? ''})"/>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div><FieldLabel hint class="mb-1">{{ TS('ctaText') }}</FieldLabel><TextInput :model-value="(cfg.ctaText as string) ?? ''" @update:model-value="patch({ctaText: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('linkUrl') }}</FieldLabel><LinkSearchInput :model-value="(cfg.url as string) ?? ''" :station-uid="stationUid" @update:model-value="patch({url: $event})"/></div>
            </div>
        </template>

        <!-- APPLICATION_CTA -->
        <template v-else-if="kind === 'APPLICATION_CTA'">
            <FieldLabel hint class="mb-1">{{ TS('headline') }}</FieldLabel>
            <TextInput :model-value="(cfg.headline as string) ?? ''" @update:model-value="patch({headline: $event})"/>
            <FieldLabel hint class="mb-1">{{ TS('body') }}</FieldLabel>
            <MarkdownFieldInput :model-value="(cfg.body as string) ?? ''" @update:model-value="patch({body: $event ?? ''})"/>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div><FieldLabel hint class="mb-1">{{ TS('ctaText') }}</FieldLabel><TextInput :model-value="(cfg.ctaText as string) ?? ''" @update:model-value="patch({ctaText: $event})"/></div>
                <div><FieldLabel hint class="mb-1">{{ TS('ctaUrl') }}</FieldLabel><LinkSearchInput :model-value="(cfg.ctaUrl as string) ?? ''" :station-uid="stationUid" @update:model-value="patch({ctaUrl: $event})"/></div>
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
