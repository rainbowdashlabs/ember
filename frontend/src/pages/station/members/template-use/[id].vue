/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
/**
 * How the station uses a template of its association moved with the templates into the documents
 * menu. This keeps the address of every such template working, because somebody has one bookmarked.
 */
definePageMeta({
  layout: 'station',
  name: 'member-document-template-use-legacy',
  redirect: to => `/station/documents/templates/use/${to.params.id}`,
})
</script>

<template>
  <div/>
</template>
