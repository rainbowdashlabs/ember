#!/usr/bin/env node
/**
 * Generates Nuxt page files from the existing vue-router config.
 * Usage: node scripts/generate-nuxt-pages.mjs [--dry-run]
 */
import fs from 'fs'
import path from 'path'

const DRY_RUN = process.argv.includes('--dry-run')
const PAGES_DIR = path.resolve('src/pages')
const ROUTER_FILE = path.resolve('src/router/index.ts')

const LICENSE = `/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */`

const src = fs.readFileSync(ROUTER_FILE, 'utf-8')
const lines = src.split('\n')

// Build import map
const importMap = new Map()
for (const m of src.matchAll(/const\s+(\w+)\s*=\s*\(\)\s*=>\s*import\(['"]@\/views\/([^'"]+)['"]\)/g))
    importMap.set(m[1], m[2])
for (const m of src.matchAll(/import\s+(\w+)\s+from\s+['"]@\/views\/([^'"]+)['"]/g))
    importMap.set(m[1], m[2])

const LAYOUT_VIEWS = new Set(['DashboardView', 'AdminView', 'HelpCenterStationView', 'HelpCenterAdminView'])

function getLayout(fullPath) {
    if (fullPath.startsWith('/station')) return 'station'
    if (fullPath.startsWith('/admin')) return 'admin'
    if (fullPath.startsWith('/helpcenter')) return 'helpcenter'
    return 'default'
}

// Parse line by line, tracking path context via a depth stack
const routes = []
const pathStack = ['']  // stack of parent paths
let currentRoute = null // accumulator for the current route object

for (const line of lines) {
    const trimmed = line.trim()

    // Detect children: [ — push current path onto stack
    if (trimmed === 'children: [' || trimmed.startsWith('children: [')) {
        // The current route's path becomes the parent for children
        if (currentRoute?.path !== undefined) {
            let full = currentRoute.path.startsWith('/')
                ? currentRoute.path
                : pathStack[pathStack.length - 1].replace(/\/$/, '') + '/' + currentRoute.path
            pathStack.push(full)
        }
        currentRoute = null
        continue
    }

    // Detect start of a route object: line is just {
    if (trimmed === '{') {
        currentRoute = {}
        continue
    }

    // Detect end of a route object: line starts with } (possibly },)
    if (trimmed.startsWith('}') && currentRoute) {
        // Finalize route
        if (currentRoute.path !== undefined && currentRoute.name && currentRoute.viewPath) {
            let fullPath = currentRoute.path.startsWith('/')
                ? currentRoute.path
                : pathStack[pathStack.length - 1].replace(/\/$/, '') + '/' + currentRoute.path
            // Skip layout-only routes
            if (!currentRoute.isLayout) {
                routes.push({ fullPath, name: currentRoute.name, viewPath: currentRoute.viewPath })
            }
        }
        currentRoute = null
        continue
    }

    // Detect closing of children array: ],
    if (trimmed === '],' || trimmed === ']') {
        if (pathStack.length > 1) pathStack.pop()
        continue
    }

    // Parse route properties
    if (!currentRoute) continue

    const pathMatch = trimmed.match(/^path:\s*['"]([^'"]*)['"]\s*,?$/)
    if (pathMatch) {
        currentRoute.path = pathMatch[1]
    }

    const nameMatch = trimmed.match(/^name:\s*['"]([^'"]*)['"]\s*,?$/)
    if (nameMatch) {
        currentRoute.name = nameMatch[1]
    }

    // Inline component import
    const inlineMatch = trimmed.match(/^component:\s*\(\)\s*=>\s*import\(['"]@\/views\/([^'"]+)['"]\)\s*,?$/)
    if (inlineMatch) {
        currentRoute.viewPath = inlineMatch[1]
    }

    // Named component reference
    const namedMatch = trimmed.match(/^component:\s*(\w+)\s*,?$/)
    if (namedMatch) {
        const name = namedMatch[1]
        if (LAYOUT_VIEWS.has(name)) {
            currentRoute.isLayout = true
        } else if (importMap.has(name)) {
            currentRoute.viewPath = importMap.get(name)
        }
    }
}

console.log(`Found ${routes.length} routes`)

let generated = 0, skipped = 0

for (const { fullPath, name, viewPath } of routes) {
    let filePath = fullPath.replace(/:(\w+)\?/g, '[[$1]]').replace(/:(\w+)/g, '[$1]').replace(/\*/g, '[...slug]')
    if (filePath === '' || filePath === '/') filePath = '/index'
    if (filePath.endsWith('/')) filePath += 'index'
    // Remove pathMatch pattern for 404
    filePath = filePath.replace(/\(\.\*\)\*/, '[...slug]')

    const pageFile = path.join(PAGES_DIR, filePath + '.vue')
    if (fs.existsSync(pageFile)) { skipped++; continue }

    const layout = getLayout(fullPath)
    const componentName = path.basename(viewPath, '.vue')
    const importPathStr = '~/views/' + viewPath.replace(/\.vue$/, '')

    const content = `${LICENSE}
<script setup lang="ts">
import ${componentName} from '${importPathStr}'

definePageMeta({
  layout: '${layout}',
  name: '${name}',
})
</script>

<template>
  <${componentName} />
</template>
`

    if (DRY_RUN) {
        console.log(`  ${filePath}.vue  (${name})`)
    } else {
        fs.mkdirSync(path.dirname(pageFile), { recursive: true })
        fs.writeFileSync(pageFile, content)
    }
    generated++
}

console.log(`\nGenerated: ${generated}, Skipped: ${skipped}`)
