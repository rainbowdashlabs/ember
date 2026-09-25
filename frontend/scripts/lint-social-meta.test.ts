/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {
    lateHeadSources,
    rendersNothing,
    serverRenderedRoutes,
    signInFreeRoutes,
    socialMetaGaps,
} from './lint-social-meta.mjs'

/**
 * What the rule catches and what it leaves alone is the whole of its worth.
 *
 * @vitest-environment happy-dom
 *
 * Too eager and it asks a page that forwards the reader elsewhere to describe a screen nobody sees;
 * too shy and a public page goes on unfurling as a blank card, which is the failure it was written
 * for.
 */
describe('social meta of a public page', () => {
    it('reports a page that sets no head at all', () => {
        expect(socialMetaGaps(`
<script setup lang="ts">
const events = ref<PublicEvent[]>([])
</script>

<template>
  <ViewContent :title="t('pages.public-station-calendar.title')"/>
</template>
`)).toEqual(['sets no head at all'])
    })

    it('reports a page that names itself and stops there', () => {
        const gaps = socialMetaGaps(`
<script setup lang="ts">
useHead({
  title: 'Anmelden',
})
</script>
`)

        expect(gaps).toContain('carries no description')
        expect(gaps).toContain('carries no OpenGraph title and description')
        expect(gaps).toContain('carries no Twitter card')
    })

    it('leaves alone a page that builds its tags with the shared builder', () => {
        expect(socialMetaGaps(`
<script setup lang="ts">
import {socialMeta} from '@/util/socialMeta'

useHead(computed(() => ({
  title: headTitle.value,
  meta: socialMeta({title: headTitle.value, description: description.value}),
})))
</script>
`)).toEqual([])
    })

    it('leaves alone a page that writes the tags out by hand', () => {
        expect(socialMetaGaps(`
<script setup lang="ts">
useHead({
  title: 'Stationen entdecken',
  meta: [
    {name: 'description', content: 'Finde Wachen in deiner Nähe.'},
    {property: 'og:title', content: 'Stationen entdecken - Ember'},
    {property: 'og:description', content: 'Finde Wachen in deiner Nähe.'},
    {property: 'og:type', content: 'website'},
    {name: 'twitter:card', content: 'summary'},
    {name: 'twitter:title', content: 'Stationen entdecken - Ember'},
    {name: 'twitter:description', content: 'Finde Wachen in deiner Nähe.'},
  ],
})
</script>
`)).toEqual([])
    })

    it('does not accept the title of a feed link as the page naming itself', () => {
        expect(socialMetaGaps(`
<script setup lang="ts">
useHead({
  link: [
    {rel: 'alternate', type: 'application/rss+xml', href: rssUrl.value, title: 'RSS'},
  ],
  meta: [
    {name: 'description', content: desc},
    {property: 'og:title', content: ogTitle},
    {property: 'og:description', content: desc},
    {name: 'twitter:card', content: 'summary'},
    {name: 'twitter:title', content: ogTitle},
  ],
})
</script>
`)).toEqual(['names no title'])
    })

    it('reads the page and the view it renders as one', () => {
        const page = `
<script setup lang="ts">
import PublicBlogView from '~/views/public/PublicBlogView.vue'
</script>
`
        const view = `
<script setup lang="ts">
useHead(computed(() => ({title: headTitle.value, meta: socialMeta({title: headTitle.value, description: desc})})))
</script>
`

        expect(socialMetaGaps([page, view].join('\n'))).toEqual([])
    })
})

/**
 * A head filled in after the page has been sent is a head nothing outside the browser ever reads,
 * so the tags being present says nothing about their being worth anything.
 */
describe('a head that arrives too late', () => {
    it('reports one read straight out of a loader that runs on mount', () => {
        expect(lateHeadSources(`
<script setup lang="ts">
const entry = ref<PublicBlogEntry | null>(null)

const {loading, error} = useAsyncLoader(async () => {
  entry.value = await news.getPublicBlogEntry(stationUid.value, blogId.value)
})

useHead(computed(() => {
  const e = entry.value
  if (!e) return {}
  return {title: e.title, meta: socialMeta({title: e.title, description: e.title})}
}))
</script>
`)).toEqual(['entry'])
    })

    it('follows a function the loader calls', () => {
        expect(lateHeadSources(`
<script setup lang="ts">
async function loadStationInfo() {
    stationInfo.value = await publicKb.getStationInfo(stationUid.value)
}

onMounted(() => {
    loadStationInfo()
})

useHead(computed(() => {
    if (!stationInfo.value) return {}
    return {title: stationInfo.value.stationName}
}))
</script>
`)).toEqual(['stationInfo'])
    })

    it('follows a computed standing on what the loader filled', () => {
        expect(lateHeadSources(`
<script setup lang="ts">
const allEvents = ref<PublicEvent[]>([])

const events = computed(() => allEvents.value.filter(e => e.startTime))

onMounted(async () => {
  allEvents.value = await listPublicEvents(stationUid.value)
})

useHead(computed(() => ({script: [{innerHTML: JSON.stringify(events.value)}]})))
</script>
`)).toEqual(['events'])
    })

    it('leaves alone a head built from what the server fetched', () => {
        expect(lateHeadSources(`
<script setup lang="ts">
const {data: entry} = await useAsyncData(
    () => 'public-blog-entry',
    () => $fetch<PublicBlogEntry>(url),
)

useHead(computed(() => ({title: entry.value?.title})))
</script>
`)).toEqual([])
    })

    it('leaves alone a page that loads on mount but names itself from something else', () => {
        expect(lateHeadSources(`
<script setup lang="ts">
const lists = ref<PublicWaitlistSummary[]>([])

const {loading} = useAsyncLoader(async () => {
  lists.value = await waitingList.listPublicWaitlists(stationUid.value)
})

useHead(computed(() => ({
  title: headTitle.value,
  meta: socialMeta({title: headTitle.value, description: t('publicStation.meta.waitlist')}),
})))
</script>
`)).toEqual([])
    })
})

/** A page that draws nothing is a doorway rather than a page, and has nothing to describe. */
describe('a view that draws nothing', () => {
    it('recognises one that only forwards the reader', () => {
        expect(rendersNothing(`
<script setup lang="ts">
router.replace(base + '/calendar')
</script>

<template>
  <div/>
</template>
`)).toBe(true)
    })

    it('does not mistake a view that draws something for one', () => {
        expect(rendersNothing(`
<template>
  <ViewContent :title="title">
    <p>{{ entry.title }}</p>
  </ViewContent>
</template>
`)).toBe(false)
    })
})

/** Which pages the rule reaches is read out of the two files that already decide it. */
describe('the pages the rule reaches', () => {
    it('takes the route prefixes the configuration renders on the server', () => {
        const routes = serverRenderedRoutes(`
  routeRules: {
    '/': {ssr: true},
    '/discovery': {ssr: true},
    '/public/**': {ssr: true},
    '/f/**': {ssr: true, headers: {'X-Robots-Tag': 'noindex, nofollow'}},
    '/helpcenter/**': {isr: 3600},
    '/station/**': {ssr: false},
    '/admin/**': {ssr: false},
  },
`)

        expect(routes).toEqual(['/', '/discovery', '/public/**', '/f/**'])
    })

    it('takes the paths let through without a session, and not the gates that only look like them', () => {
        const {prefixes, exact} = signInFreeRoutes(`
    if (to.path === '/' || to.path === '/login') return
    if (to.path.startsWith('/helpcenter')) return

    const publicPaths = [
        '/discovery', '/public', '/waiting-list',
    ]
    if (publicPaths.some(p => to.path.startsWith(p))) return

    if (to.path === '/admin' || to.path.startsWith('/admin/')) {
        return navigateTo('/station/dashboard/overview')
    }
`)

        expect(exact).toEqual(['/', '/login'])
        expect(prefixes).toEqual(['/discovery', '/public', '/waiting-list', '/helpcenter'])
    })
})
