/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {defineEventHandler, setResponseHeader} from 'h3'
import {readdirSync, statSync} from 'fs'
import {join, relative} from 'path'

const SITE_URL = process.env.NUXT_PUBLIC_SITE_URL || 'http://localhost:3000'

function escapeXml(s: string): string {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function scanHelpcenterPages(baseDir: string): string[] {
    const routes: string[] = []
    function walk(dir: string) {
        let entries: string[]
        try { entries = readdirSync(dir) } catch { return }
        for (const entry of entries) {
            const full = join(dir, entry)
            if (statSync(full).isDirectory()) {
                walk(full)
            } else if (entry.endsWith('.vue')) {
                let route = '/helpcenter/' + relative(baseDir, full)
                    .replace(/\.vue$/, '')
                    .replace(/\/index$/, '')
                // Skip dynamic param routes
                if (route.includes('[')) continue
                routes.push(route)
            }
        }
    }
    walk(baseDir)
    return routes
}

export default defineEventHandler(async (event) => {
    const now = new Date().toISOString()

    const urls: {loc: string, priority: string, changefreq: string}[] = [
        {loc: '/', priority: '1.0', changefreq: 'monthly'},
        {loc: '/discovery', priority: '0.9', changefreq: 'daily'},
        {loc: '/login', priority: '0.3', changefreq: 'monthly'},
        {loc: '/imprint', priority: '0.2', changefreq: 'yearly'},
        {loc: '/privacy', priority: '0.2', changefreq: 'yearly'},
    ]

    // Scan helpcenter pages
    const searchDirs = [
        join(process.cwd(), 'src', 'pages', 'helpcenter'),
        join(process.cwd(), '..', 'frontend', 'src', 'pages', 'helpcenter'),
    ]
    for (const dir of searchDirs) {
        try {
            statSync(dir)
            const helpcenterRoutes = scanHelpcenterPages(dir)
            for (const route of helpcenterRoutes) {
                urls.push({loc: route, priority: '0.4', changefreq: 'monthly'})
            }
            break
        } catch { /* try next */ }
    }

    const entries = urls.map(u =>
        `  <url>\n    <loc>${escapeXml(SITE_URL + u.loc)}</loc>\n    <priority>${u.priority}</priority>\n    <changefreq>${u.changefreq}</changefreq>\n    <lastmod>${now}</lastmod>\n  </url>`
    ).join('\n')

    setResponseHeader(event, 'content-type', 'application/xml; charset=utf-8')
    setResponseHeader(event, 'cache-control', 'public, max-age=3600')
    return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${entries}\n</urlset>`
})
