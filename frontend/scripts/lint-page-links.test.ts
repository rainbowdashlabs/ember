/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {rowsThatShouldBeLinks} from './lint-page-links.mjs'

/**
 * What the rule catches and what it leaves alone is the whole of its worth.
 *
 * @vitest-environment happy-dom
 *
 * It decides what a sweep over every list in the product has to change, and both halves are easy to
 * get wrong in a way that only shows up much later: too eager and it asks for links where going on
 * after an act is meant, too shy and the lists it was written for stay unreachable by address.
 */
describe('rows that should be links', () => {
    it('reports a row whose click handler is nothing but a push', () => {
        const found = rowsThatShouldBeLinks(`
<script setup lang="ts">
function openCatalog() {
  router.push({name: 'quiz-catalog', params: {id: props.catalog.id}})
}
</script>

<template>
  <NeutralContainer @click="openCatalog">{{ catalog.name }}</NeutralContainer>
</template>
`)

        expect(found).toHaveLength(1)
        expect(found[0].element).toBe('NeutralContainer')
    })

    it('reports a push written in the template', () => {
        const found = rowsThatShouldBeLinks(`
<template>
  <div @click="router.push({name: 'quiz-catalog'})">{{ catalog.name }}</div>
</template>
`)

        expect(found).toHaveLength(1)
        expect(found[0].element).toBe('div')
    })

    it('leaves alone a handler that saves before it navigates', () => {
        const found = rowsThatShouldBeLinks(`
<script setup lang="ts">
async function saveAndOpen() {
  const created = await quiz.createCatalog(draft.value)
  router.push({name: 'quiz-catalog', params: {id: created.id}})
}
</script>

<template>
  <NeutralContainer @click="saveAndOpen">{{ draft.name }}</NeutralContainer>
</template>
`)

        expect(found).toEqual([])
    })

    it('leaves alone a button, whose navigation is a design decision', () => {
        const found = rowsThatShouldBeLinks(`
<script setup lang="ts">
function goBack() {
  router.push({name: 'quiz-catalogs'})
}
</script>

<template>
  <SecondaryButton @click="goBack">{{ t('common.back') }}</SecondaryButton>
</template>
`)

        expect(found).toEqual([])
    })

    it('leaves alone an entry of a menu, which is a button under its name', () => {
        const found = rowsThatShouldBeLinks(`
<script setup lang="ts">
function openResults() {
  router.push({name: 'quiz-test-results'})
}
</script>

<template>
  <DropdownMenuItem @click="openResults">{{ t('quiz.results') }}</DropdownMenuItem>
</template>
`)

        expect(found).toEqual([])
    })

    it('leaves alone a row that is already a link', () => {
        const found = rowsThatShouldBeLinks(`
<template>
  <RowLink :to="{name: 'quiz-catalog'}" @click="router.push({name: 'quiz-catalog'})">
    {{ catalog.name }}
  </RowLink>
</template>
`)

        expect(found).toEqual([])
    })

    it('leaves alone a handler that does something other than navigate', () => {
        const found = rowsThatShouldBeLinks(`
<script setup lang="ts">
function expand() {
  open.value = !open.value
}
</script>

<template>
  <NeutralContainer @click="expand">{{ catalog.name }}</NeutralContainer>
</template>
`)

        expect(found).toEqual([])
    })
})
