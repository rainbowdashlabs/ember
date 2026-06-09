/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {defineSitemapEventHandler, asSitemapUrl} from '#imports'

const API_BASE = process.env.NUXT_API_BASE || 'http://localhost:8080'

interface DiscoveryStation {
  stationUid: string
  name: string
  hasPublicCalendar: boolean
  hasPublicKb: boolean
}

export default defineSitemapEventHandler(async () => {
  const now = new Date().toISOString()

  const urls = [
    asSitemapUrl({loc: '/', priority: 1.0, changefreq: 'monthly', lastmod: now}),
    asSitemapUrl({loc: '/discovery', priority: 0.9, changefreq: 'daily', lastmod: now}),
    asSitemapUrl({loc: '/login', priority: 0.3, changefreq: 'monthly'}),
    asSitemapUrl({loc: '/imprint', priority: 0.2, changefreq: 'yearly'}),
    asSitemapUrl({loc: '/privacy', priority: 0.2, changefreq: 'yearly'}),
  ]

  try {
    const res = await fetch(`${API_BASE}/api/v1/public/discovery`)
    if (res.ok) {
      const stations: DiscoveryStation[] = await res.json()
      for (const station of stations) {
        const base = `/public/station/${station.stationUid}`
        if (station.hasPublicCalendar) {
          urls.push(asSitemapUrl({loc: `${base}/calendar`, priority: 0.7, changefreq: 'daily', lastmod: now}))
        }
        if (station.hasPublicKb) {
          urls.push(asSitemapUrl({loc: `${base}/knowledge`, priority: 0.6, changefreq: 'weekly', lastmod: now}))
        }
      }
    }
  } catch {
    // API unavailable during build — static pages only
  }

  return urls
})
