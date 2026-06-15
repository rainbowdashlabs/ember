/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, onMounted} from 'vue'
import {marked} from 'marked'
import {
    CalloutVariant,
    pageImageUrl,
    type LayoutKindName,
    type CalloutConfig, type QuoteConfig, type DividerConfig, type SpacerConfig,
    type AccordionConfig, type PdfConfig, type FileDownloadConfig,
    type CountdownConfig, type FeaturedEventConfig, type UpcomingEventsConfig,
    type KbArticleConfig, type NewsTeaserConfig, type PageLinkConfig,
    type MapConfig, type AddressCardConfig, type PartnerStationsConfig,
    type FederatedEventConfig, type MemberSpotlightConfig, type OfficersRowConfig,
    type StatsCounterConfig, type ImageGalleryConfig,
    type HeroBannerConfig, type PastEventRecapConfig, type TabsConfig,
    type AchievementsConfig, type ExternalLinkCardConfig, type NewsletterSignupConfig,
    type AudioEmbedConfig, type PollEmbedConfig, type QuizTeaserConfig,
    type ApplicationCtaConfig, type CodeBlockConfig,
} from '@/api/pageManage'

const props = defineProps<{
    kind: LayoutKindName
    content: string
    config: Record<string, unknown>
    stationUid?: string
}>()

function asCfg<T>(): T { return props.config as T }

const callout = computed(() => asCfg<CalloutConfig>())
const quote = computed(() => asCfg<QuoteConfig>())
const divider = computed(() => asCfg<DividerConfig>())
const spacer = computed(() => asCfg<SpacerConfig>())
const accordion = computed(() => asCfg<AccordionConfig>())
const pdf = computed(() => asCfg<PdfConfig>())
const file = computed(() => asCfg<FileDownloadConfig>())
const countdown = computed(() => asCfg<CountdownConfig>())
const featuredEvent = computed(() => asCfg<FeaturedEventConfig>())
const upcomingEvents = computed(() => asCfg<UpcomingEventsConfig>())
const kbArticle = computed(() => asCfg<KbArticleConfig>())
const newsTeaser = computed(() => asCfg<NewsTeaserConfig>())
const pageLink = computed(() => asCfg<PageLinkConfig>())
const map = computed(() => asCfg<MapConfig>())
const address = computed(() => asCfg<AddressCardConfig>())
const partners = computed(() => asCfg<PartnerStationsConfig>())
const federatedEvent = computed(() => asCfg<FederatedEventConfig>())
const memberSpotlight = computed(() => asCfg<MemberSpotlightConfig>())
const officers = computed(() => asCfg<OfficersRowConfig>())
const stats = computed(() => asCfg<StatsCounterConfig>())
const gallery = computed(() => asCfg<ImageGalleryConfig>())
const hero = computed(() => asCfg<HeroBannerConfig>())
const pastEvent = computed(() => asCfg<PastEventRecapConfig>())
const tabs = computed(() => asCfg<TabsConfig>())
const achievements = computed(() => asCfg<AchievementsConfig>())
const externalLink = computed(() => asCfg<ExternalLinkCardConfig>())
const newsletter = computed(() => asCfg<NewsletterSignupConfig>())
const audio = computed(() => asCfg<AudioEmbedConfig>())
const poll = computed(() => asCfg<PollEmbedConfig>())
const quiz = computed(() => asCfg<QuizTeaserConfig>())
const application = computed(() => asCfg<ApplicationCtaConfig>())
const codeBlock = computed(() => asCfg<CodeBlockConfig>())

const calloutStyle = computed(() => {
    switch (callout.value.variant ?? CalloutVariant.INFO) {
        case CalloutVariant.WARNING: return {bg: 'bg-warning/10 dark:bg-warning/15', border: 'border-warning/40', text: 'text-warning', icon: ['fas', 'triangle-exclamation']}
        case CalloutVariant.SUCCESS: return {bg: 'bg-success/10 dark:bg-success/15', border: 'border-success/40', text: 'text-success', icon: ['fas', 'circle-check']}
        case CalloutVariant.TIP: return {bg: 'bg-primary/10 dark:bg-primary/15', border: 'border-primary/40', text: 'text-primary', icon: ['fas', 'lightbulb']}
        case CalloutVariant.INFO:
        default: return {bg: 'bg-info/10 dark:bg-info/15', border: 'border-info/40', text: 'text-info', icon: ['fas', 'circle-info']}
    }
})

const renderedContent = computed(() => {
    if (!props.content) return ''
    try { return marked.parse(props.content) as string } catch { return props.content }
})

const accordionOpen = ref<boolean>(!!accordion.value.openByDefault)

// Countdown
const countdownState = ref({days: 0, hours: 0, minutes: 0, seconds: 0, expired: true})
let countdownTimer: ReturnType<typeof setInterval> | null = null
function tickCountdown() {
    const target = countdown.value.targetDate ? new Date(countdown.value.targetDate).getTime() : 0
    const diff = target - Date.now()
    if (diff <= 0) {
        countdownState.value = {days: 0, hours: 0, minutes: 0, seconds: 0, expired: true}
        return
    }
    countdownState.value = {
        days: Math.floor(diff / 86_400_000),
        hours: Math.floor((diff % 86_400_000) / 3_600_000),
        minutes: Math.floor((diff % 3_600_000) / 60_000),
        seconds: Math.floor((diff % 60_000) / 1000),
        expired: false,
    }
}
onMounted(() => {
    if (props.kind === 'COUNTDOWN') {
        tickCountdown()
        countdownTimer = setInterval(tickCountdown, 1000)
    }
})

const mapUrl = computed(() => {
    const lat = map.value.latitude ?? 0
    const lon = map.value.longitude ?? 0
    const z = map.value.zoom ?? 14
    const span = 0.01 * Math.pow(2, 14 - z)
    return `https://www.openstreetmap.org/export/embed.html?bbox=${lon - span},${lat - span},${lon + span},${lat + span}&layer=mapnik&marker=${lat},${lon}`
})

const galleryColumns = computed(() => Math.max(1, Math.min(6, gallery.value.columns ?? 3)))

const activeTab = ref(0)
</script>

<template>
    <!-- CALLOUT -->
    <div v-if="kind === 'CALLOUT'" class="border-l-4 rounded-r px-4 py-3 flex gap-3" :class="[calloutStyle.bg, calloutStyle.border]">
        <font-awesome-icon :icon="calloutStyle.icon" class="mt-0.5" :class="calloutStyle.text"/>
        <div class="flex-1 min-w-0">
            <p v-if="callout.title" class="font-semibold" :class="calloutStyle.text">{{ callout.title }}</p>
            <div v-if="content" class="markdown-content text-sm" v-html="renderedContent"/>
        </div>
    </div>

    <!-- QUOTE -->
    <blockquote v-else-if="kind === 'QUOTE'" class="border-l-4 border-primary/60 pl-4 py-2 italic">
        <p class="text-base whitespace-pre-line">{{ content || '…' }}</p>
        <footer v-if="quote.author" class="mt-2 text-xs not-italic text-(--text-muted)">
            —
            <a v-if="quote.attributionUrl" :href="quote.attributionUrl" class="hover:underline" target="_blank" rel="noopener noreferrer">{{ quote.author }}</a>
            <span v-else>{{ quote.author }}</span>
        </footer>
    </blockquote>

    <!-- DIVIDER -->
    <div v-else-if="kind === 'DIVIDER'" class="flex items-center gap-3 py-2">
        <div class="flex-1 h-px bg-(--border)"/>
        <span v-if="divider.label" class="text-xs uppercase tracking-wider text-(--text-muted)">{{ divider.label }}</span>
        <div v-if="divider.label" class="flex-1 h-px bg-(--border)"/>
    </div>

    <!-- SPACER -->
    <div v-else-if="kind === 'SPACER'" :style="{height: `${spacer.heightPx ?? 32}px`}"/>

    <!-- ACCORDION -->
    <details v-else-if="kind === 'ACCORDION'" class="rounded-theme border border-(--border) overflow-hidden" :open="accordionOpen" @toggle="(e: Event) => { accordionOpen = (e.target as HTMLDetailsElement).open }">
        <summary class="cursor-pointer px-3 py-2 font-medium bg-bg-light-accent/30 dark:bg-bg-dark-accent/30 select-none">
            {{ accordion.title || '…' }}
        </summary>
        <div v-if="content" class="markdown-content px-3 py-2" v-html="renderedContent"/>
    </details>

    <!-- PDF -->
    <div v-else-if="kind === 'PDF' && pdf.url" class="rounded-theme overflow-hidden border border-(--border)">
        <iframe :src="pdf.url" :style="{height: `${pdf.heightPx ?? 600}px`}" class="w-full block" loading="lazy"/>
    </div>
    <p v-else-if="kind === 'PDF'" class="text-sm text-(--text-muted) italic">PDF-URL fehlt</p>

    <!-- FILE_DOWNLOAD -->
    <a v-else-if="kind === 'FILE_DOWNLOAD' && file.url" :href="file.url" target="_blank" rel="noopener noreferrer" class="flex items-center gap-3 rounded-theme border border-(--border) hover:border-primary hover:bg-primary/5 transition-colors px-4 py-3" download>
        <font-awesome-icon :icon="['fas', 'file']" class="text-2xl text-primary shrink-0"/>
        <div class="flex-1 min-w-0">
            <p class="font-medium truncate">{{ file.label || file.url.split('/').pop() || 'Datei' }}</p>
            <p v-if="file.description" class="text-xs text-(--text-muted) truncate">{{ file.description }}</p>
        </div>
        <font-awesome-icon :icon="['fas', 'download']" class="text-(--text-muted)"/>
    </a>
    <p v-else-if="kind === 'FILE_DOWNLOAD'" class="text-sm text-(--text-muted) italic">Datei-URL fehlt</p>

    <!-- COUNTDOWN -->
    <div v-else-if="kind === 'COUNTDOWN'" class="rounded-theme border border-(--border) bg-bg-light-accent/30 dark:bg-bg-dark-accent/20 p-4 text-center space-y-2">
        <p v-if="countdown.label" class="text-sm font-semibold">{{ countdown.label }}</p>
        <div v-if="!countdownState.expired" class="flex justify-center gap-3 sm:gap-6">
            <div v-for="part in [
                {n: countdownState.days, l: 'Tage'},
                {n: countdownState.hours, l: 'Std'},
                {n: countdownState.minutes, l: 'Min'},
                {n: countdownState.seconds, l: 'Sek'},
            ]" :key="part.l" class="text-center">
                <div class="text-3xl font-bold text-primary tabular-nums">{{ part.n }}</div>
                <div class="text-xs text-(--text-muted) uppercase">{{ part.l }}</div>
            </div>
        </div>
        <p v-else class="text-lg font-semibold text-primary">⏰</p>
        <p v-if="countdown.sublabel" class="text-xs text-(--text-muted)">{{ countdown.sublabel }}</p>
    </div>

    <!-- FEATURED_EVENT -->
    <div v-else-if="kind === 'FEATURED_EVENT'" class="rounded-theme border border-primary/40 bg-primary/5 p-4 space-y-2">
        <div class="flex items-start gap-3">
            <font-awesome-icon :icon="['fas', 'calendar-days']" class="text-2xl text-primary mt-1"/>
            <div class="flex-1 min-w-0">
                <p class="font-semibold text-base">{{ featuredEvent.title || 'Termin' }}</p>
                <p v-if="featuredEvent.date" class="text-sm text-(--text-muted)">{{ featuredEvent.date }}</p>
                <p v-if="featuredEvent.location" class="text-xs text-(--text-muted)">
                    <font-awesome-icon :icon="['fas', 'location-dot']" class="mr-1"/>{{ featuredEvent.location }}
                </p>
            </div>
        </div>
        <p v-if="featuredEvent.description" class="text-sm whitespace-pre-line">{{ featuredEvent.description }}</p>
        <a v-if="featuredEvent.ctaUrl" :href="featuredEvent.ctaUrl" target="_blank" rel="noopener noreferrer" class="inline-block px-3 py-1.5 rounded-theme bg-primary text-primary-text text-sm font-medium hover:bg-primary-accent">{{ featuredEvent.ctaText || 'Mehr erfahren' }}</a>
    </div>

    <!-- UPCOMING_EVENTS -->
    <div v-else-if="kind === 'UPCOMING_EVENTS'" class="space-y-2">
        <p v-if="upcomingEvents.title" class="font-semibold">{{ upcomingEvents.title }}</p>
        <ul class="space-y-2">
            <li v-for="(item, i) in upcomingEvents.items ?? []" :key="i" class="flex items-start gap-3 rounded-theme border border-(--border) px-3 py-2">
                <font-awesome-icon :icon="['fas', 'calendar']" class="text-primary mt-0.5"/>
                <div class="flex-1 min-w-0">
                    <a v-if="item.url" :href="item.url" class="font-medium hover:text-primary hover:underline">{{ item.title || '…' }}</a>
                    <span v-else class="font-medium">{{ item.title || '…' }}</span>
                    <p class="text-xs text-(--text-muted)">{{ [item.date, item.location].filter(Boolean).join(' · ') }}</p>
                </div>
            </li>
            <li v-if="!upcomingEvents.items || upcomingEvents.items.length === 0" class="text-xs text-(--text-muted) italic">Keine Einträge.</li>
        </ul>
    </div>

    <!-- KB_ARTICLE -->
    <a v-else-if="kind === 'KB_ARTICLE'" :href="stationUid ? `/public/${stationUid}/kb/${kbArticle.articleId ?? ''}` : '#'" class="flex items-center gap-3 rounded-theme border border-(--border) hover:border-primary hover:bg-primary/5 transition-colors px-4 py-3">
        <font-awesome-icon :icon="['fas', 'book']" class="text-xl text-primary"/>
        <div class="flex-1 min-w-0">
            <p class="font-medium truncate">{{ kbArticle.fallbackTitle || 'Wiki-Artikel' }}</p>
            <p class="text-xs text-(--text-muted) truncate">Wissensdatenbank</p>
        </div>
        <font-awesome-icon :icon="['fas', 'arrow-right']" class="text-(--text-muted)"/>
    </a>

    <!-- NEWS_TEASER -->
    <a v-else-if="kind === 'NEWS_TEASER'" :href="newsTeaser.url || '#'" class="block rounded-theme border border-(--border) hover:border-primary hover:bg-primary/5 transition-colors overflow-hidden">
        <img v-if="newsTeaser.imageUrl" :src="newsTeaser.imageUrl" :alt="newsTeaser.title ?? ''" class="w-full h-32 object-cover"/>
        <div class="p-3 space-y-1">
            <p class="font-semibold">{{ newsTeaser.title || 'Neuigkeit' }}</p>
            <p v-if="newsTeaser.date" class="text-xs text-(--text-muted)">{{ newsTeaser.date }}</p>
            <p v-if="newsTeaser.summary" class="text-sm text-(--text-muted)">{{ newsTeaser.summary }}</p>
        </div>
    </a>

    <!-- PAGE_LINK -->
    <a v-else-if="kind === 'PAGE_LINK'" :href="stationUid && pageLink.pageId ? `/public/${stationUid}/page/${pageLink.pageId}` : '#'" class="flex items-center gap-3 rounded-theme border border-(--border) hover:border-primary hover:bg-primary/5 transition-colors px-4 py-3">
        <font-awesome-icon :icon="['fas', 'file-lines']" class="text-xl text-primary"/>
        <div class="flex-1 min-w-0">
            <p class="font-medium truncate">{{ pageLink.fallbackTitle || 'Seite' }}</p>
        </div>
        <font-awesome-icon :icon="['fas', 'arrow-right']" class="text-(--text-muted)"/>
    </a>

    <!-- MAP -->
    <div v-else-if="kind === 'MAP' && map.latitude != null && map.longitude != null" class="rounded-theme overflow-hidden border border-(--border)">
        <iframe :src="mapUrl" :style="{height: `${map.heightPx ?? 320}px`}" class="w-full block" loading="lazy"/>
        <p v-if="map.label" class="text-center text-xs text-(--text-muted) py-1">{{ map.label }}</p>
    </div>
    <p v-else-if="kind === 'MAP'" class="text-sm text-(--text-muted) italic">Koordinaten fehlen</p>

    <!-- ADDRESS_CARD -->
    <div v-else-if="kind === 'ADDRESS_CARD'" class="rounded-theme border border-(--border) p-4 flex gap-3 items-start">
        <font-awesome-icon :icon="['fas', 'location-dot']" class="text-2xl text-primary mt-1"/>
        <div class="flex-1 min-w-0 space-y-1">
            <p v-if="address.label" class="font-semibold">{{ address.label }}</p>
            <p v-if="address.addressLine" class="text-sm">{{ address.addressLine }}</p>
            <p v-if="address.postalCode || address.city" class="text-sm">{{ [address.postalCode, address.city].filter(Boolean).join(' ') }}</p>
            <p v-if="address.country" class="text-sm text-(--text-muted)">{{ address.country }}</p>
            <a v-if="address.mapUrl" :href="address.mapUrl" target="_blank" rel="noopener noreferrer" class="inline-block text-xs text-primary hover:underline mt-1">In Karten öffnen ↗</a>
        </div>
    </div>

    <!-- PARTNER_STATIONS -->
    <div v-else-if="kind === 'PARTNER_STATIONS'" class="space-y-2">
        <p v-if="partners.title" class="font-semibold">{{ partners.title }}</p>
        <ul class="grid grid-cols-1 sm:grid-cols-2 gap-2">
            <li v-for="(p, i) in partners.items ?? []" :key="i" class="rounded-theme border border-(--border) px-3 py-2">
                <a v-if="p.url" :href="p.url" target="_blank" rel="noopener noreferrer" class="font-medium hover:text-primary hover:underline">{{ p.name }}</a>
                <span v-else class="font-medium">{{ p.name }}</span>
                <p v-if="p.distanceKm != null" class="text-xs text-(--text-muted)">{{ p.distanceKm.toFixed(1) }} km entfernt</p>
            </li>
        </ul>
    </div>

    <!-- FEDERATED_EVENT -->
    <a v-else-if="kind === 'FEDERATED_EVENT'" :href="federatedEvent.url || '#'" target="_blank" rel="noopener noreferrer" class="block rounded-theme border border-info/40 bg-info/5 p-4 space-y-1 hover:bg-info/10 transition-colors">
        <p class="font-semibold">{{ federatedEvent.title || 'Partnertermin' }}</p>
        <p v-if="federatedEvent.partnerName" class="text-xs text-info">{{ federatedEvent.partnerName }}</p>
        <p v-if="federatedEvent.date" class="text-sm text-(--text-muted)">{{ federatedEvent.date }}</p>
        <p v-if="federatedEvent.description" class="text-sm">{{ federatedEvent.description }}</p>
    </a>

    <!-- MEMBER_SPOTLIGHT -->
    <div v-else-if="kind === 'MEMBER_SPOTLIGHT'" class="flex gap-3 items-start rounded-theme border border-(--border) p-4">
        <img v-if="memberSpotlight.imageUrl" :src="memberSpotlight.imageUrl" :alt="memberSpotlight.name ?? ''" class="w-16 h-16 rounded-full object-cover shrink-0"/>
        <div v-else class="w-16 h-16 rounded-full bg-primary/15 text-primary flex items-center justify-center shrink-0">
            <font-awesome-icon :icon="['fas', 'user']" class="text-2xl"/>
        </div>
        <div class="flex-1 min-w-0">
            <p class="font-semibold">{{ memberSpotlight.name || '…' }}</p>
            <p v-if="memberSpotlight.role" class="text-xs text-primary">{{ memberSpotlight.role }}</p>
            <p v-if="memberSpotlight.blurb" class="text-sm text-(--text-muted) mt-1">{{ memberSpotlight.blurb }}</p>
        </div>
    </div>

    <!-- OFFICERS_ROW -->
    <div v-else-if="kind === 'OFFICERS_ROW'" class="space-y-3">
        <p v-if="officers.title" class="font-semibold">{{ officers.title }}</p>
        <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
            <div v-for="(o, i) in officers.items ?? []" :key="i" class="text-center space-y-1">
                <img v-if="o.imageUrl" :src="o.imageUrl" :alt="o.name ?? ''" class="w-20 h-20 mx-auto rounded-full object-cover"/>
                <div v-else class="w-20 h-20 mx-auto rounded-full bg-primary/15 text-primary flex items-center justify-center">
                    <font-awesome-icon :icon="['fas', 'user']" class="text-2xl"/>
                </div>
                <p class="font-medium text-sm">{{ o.name }}</p>
                <p class="text-xs text-(--text-muted)">{{ o.role }}</p>
            </div>
        </div>
    </div>

    <!-- STATS_COUNTER -->
    <div v-else-if="kind === 'STATS_COUNTER'" class="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div v-for="(s, i) in stats.items ?? []" :key="i" class="text-center rounded-theme border border-(--border) p-3">
            <div class="text-3xl font-bold text-primary tabular-nums">{{ s.value }}<span v-if="s.suffix" class="text-base font-normal">{{ s.suffix }}</span></div>
            <div class="text-xs text-(--text-muted) uppercase tracking-wider mt-1">{{ s.label }}</div>
        </div>
    </div>

    <!-- IMAGE_GALLERY -->
    <div v-else-if="kind === 'IMAGE_GALLERY'" :class="`grid grid-cols-${galleryColumns} gap-2`">
        <img v-for="id in gallery.imageIds ?? []" :key="id" :src="stationUid ? pageImageUrl(stationUid, id) : ''" class="w-full aspect-square object-cover rounded" loading="lazy" alt=""/>
    </div>

    <!-- HERO_BANNER -->
    <div v-else-if="kind === 'HERO_BANNER'" class="relative w-full rounded-theme overflow-hidden">
        <img v-if="hero.imageId != null && stationUid" :src="pageImageUrl(stationUid, hero.imageId)" alt="" class="w-full h-64 object-cover"/>
        <div v-else class="w-full h-64 bg-gradient-to-br from-primary/40 to-primary-accent/40"/>
        <div class="absolute inset-0 bg-black/40 flex flex-col items-center justify-center text-center text-white p-6">
            <div v-if="hero.headline" class="text-3xl sm:text-4xl font-bold">{{ hero.headline }}</div>
            <p v-if="hero.subtitle" class="mt-2 text-lg">{{ hero.subtitle }}</p>
            <a v-if="hero.ctaUrl" :href="hero.ctaUrl" class="mt-4 inline-block px-4 py-2 rounded-theme bg-primary text-primary-text font-medium hover:bg-primary-accent">{{ hero.ctaText || 'Mehr erfahren' }}</a>
        </div>
    </div>

    <!-- PAST_EVENT_RECAP -->
    <div v-else-if="kind === 'PAST_EVENT_RECAP'" class="rounded-theme border border-(--border) overflow-hidden">
        <img v-if="pastEvent.imageId != null && stationUid" :src="pageImageUrl(stationUid, pastEvent.imageId)" alt="" class="w-full h-40 object-cover"/>
        <div class="p-3 space-y-1">
            <p class="font-semibold">{{ pastEvent.title || '…' }}</p>
            <p v-if="pastEvent.date" class="text-xs text-(--text-muted)">{{ pastEvent.date }}</p>
            <p v-if="pastEvent.summary" class="text-sm whitespace-pre-line">{{ pastEvent.summary }}</p>
        </div>
    </div>

    <!-- TABS -->
    <div v-else-if="kind === 'TABS'" class="rounded-theme border border-(--border) overflow-hidden">
        <div class="flex border-b border-(--border) bg-bg-light-accent/30 dark:bg-bg-dark-accent/30">
            <a v-for="(tab, i) in tabs.items ?? []" :key="i" role="tab" tabindex="0" :class="['px-3 py-2 text-sm font-medium cursor-pointer select-none', activeTab === i ? 'border-b-2 border-primary text-primary' : 'text-(--text-muted) hover:text-(--text)']" @click="activeTab = i" @keydown.enter="activeTab = i">{{ tab.title || `Tab ${i + 1}` }}</a>
        </div>
        <div class="p-3 markdown-content whitespace-pre-line">{{ tabs.items?.[activeTab]?.body ?? '' }}</div>
    </div>

    <!-- ACHIEVEMENTS -->
    <div v-else-if="kind === 'ACHIEVEMENTS'" class="space-y-2">
        <p v-if="achievements.title" class="font-semibold">{{ achievements.title }}</p>
        <ul class="grid grid-cols-1 sm:grid-cols-2 gap-2">
            <li v-for="(a, i) in achievements.items ?? []" :key="i" class="rounded-theme border border-(--border) px-3 py-2 flex gap-2">
                <font-awesome-icon :icon="['fas', 'trophy']" class="text-yellow-500 text-xl mt-0.5"/>
                <div>
                    <p class="font-medium">{{ a.title }}</p>
                    <p v-if="a.year" class="text-xs text-primary">{{ a.year }}</p>
                    <p v-if="a.description" class="text-xs text-(--text-muted)">{{ a.description }}</p>
                </div>
            </li>
        </ul>
    </div>

    <!-- EXTERNAL_LINK_CARD -->
    <a v-else-if="kind === 'EXTERNAL_LINK_CARD'" :href="externalLink.url || '#'" target="_blank" rel="noopener noreferrer" class="block rounded-theme border border-(--border) hover:border-primary hover:bg-primary/5 transition-colors overflow-hidden">
        <img v-if="externalLink.imageUrl" :src="externalLink.imageUrl" :alt="externalLink.title ?? ''" class="w-full h-32 object-cover"/>
        <div class="p-3 space-y-1">
            <p class="font-semibold">{{ externalLink.title || externalLink.url }}</p>
            <p v-if="externalLink.description" class="text-sm text-(--text-muted)">{{ externalLink.description }}</p>
            <p v-if="externalLink.url" class="text-xs text-primary truncate">{{ externalLink.url }} ↗</p>
        </div>
    </a>

    <!-- NEWSLETTER_SIGNUP -->
    <div v-else-if="kind === 'NEWSLETTER_SIGNUP'" class="rounded-theme border border-(--border) bg-primary/5 p-4 space-y-2 text-center">
        <font-awesome-icon :icon="['fas', 'envelope']" class="text-3xl text-primary"/>
        <p class="font-semibold">{{ newsletter.title || 'Bleib informiert' }}</p>
        <p v-if="newsletter.description" class="text-sm text-(--text-muted)">{{ newsletter.description }}</p>
        <a v-if="newsletter.feedUrl" :href="newsletter.feedUrl" class="inline-block px-3 py-1.5 rounded-theme bg-primary text-primary-text text-sm font-medium hover:bg-primary-accent">Abonnieren</a>
    </div>

    <!-- AUDIO_EMBED -->
    <div v-else-if="kind === 'AUDIO_EMBED' && audio.url" class="rounded-theme border border-(--border) p-3 space-y-2">
        <p v-if="audio.title" class="font-medium text-sm">{{ audio.title }}</p>
        <audio :src="audio.url" controls class="w-full"/>
    </div>
    <p v-else-if="kind === 'AUDIO_EMBED'" class="text-sm text-(--text-muted) italic">Audio-URL fehlt</p>

    <!-- POLL_EMBED -->
    <a v-else-if="kind === 'POLL_EMBED'" :href="poll.url || '#'" class="block rounded-theme border border-secondary/40 bg-secondary/5 p-4 hover:bg-secondary/10 transition-colors">
        <div class="flex items-center gap-3">
            <font-awesome-icon :icon="['fas', 'square-poll-vertical']" class="text-2xl text-secondary"/>
            <div class="flex-1">
                <p class="font-semibold">{{ poll.title || 'Umfrage' }}</p>
                <p v-if="poll.description" class="text-sm text-(--text-muted)">{{ poll.description }}</p>
            </div>
        </div>
    </a>

    <!-- QUIZ_TEASER -->
    <a v-else-if="kind === 'QUIZ_TEASER'" :href="quiz.url || '#'" class="block rounded-theme border border-(--border) hover:border-primary hover:bg-primary/5 transition-colors p-4 space-y-2">
        <div class="flex items-center gap-3">
            <font-awesome-icon :icon="['fas', 'graduation-cap']" class="text-2xl text-primary"/>
            <p class="font-semibold flex-1">{{ quiz.title || 'Teste dein Wissen' }}</p>
        </div>
        <p v-if="quiz.description" class="text-sm text-(--text-muted)">{{ quiz.description }}</p>
        <span class="inline-block text-xs text-primary font-medium">{{ quiz.ctaText || 'Quiz starten' }} ↗</span>
    </a>

    <!-- APPLICATION_CTA -->
    <div v-else-if="kind === 'APPLICATION_CTA'" class="rounded-theme border-2 border-primary bg-primary/10 p-6 text-center space-y-2">
        <p class="text-xl font-bold">{{ application.headline || 'Mach mit!' }}</p>
        <p v-if="application.body" class="text-sm whitespace-pre-line">{{ application.body }}</p>
        <a v-if="application.ctaUrl" :href="application.ctaUrl" class="inline-block px-4 py-2 rounded-theme bg-primary text-primary-text font-semibold hover:bg-primary-accent">{{ application.ctaText || 'Jetzt bewerben' }}</a>
    </div>

    <!-- CODE_BLOCK -->
    <pre v-else-if="kind === 'CODE_BLOCK'" class="rounded-theme border border-(--border) bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 p-3 overflow-x-auto text-xs"><code :class="codeBlock.language ? `language-${codeBlock.language}` : ''">{{ content }}</code></pre>
</template>
