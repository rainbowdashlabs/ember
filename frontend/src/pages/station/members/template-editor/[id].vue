/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
/**
 * The template editor moved with the templates into the documents menu. This keeps the address of
 * every template working, because somebody has one bookmarked.
 */
definePageMeta({
  layout: 'station',
  name: 'member-document-template-edit-legacy',
  redirect: to => ({path: `/station/documents/templates/${to.params.id}`, query: to.query}),
})
</script>

<template>
  <div/>
</template>
