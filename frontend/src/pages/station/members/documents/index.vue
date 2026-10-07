/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
/**
 * The document store moved out of the members into a menu of its own. This keeps the address it used
 * to live at working, because bookmarks and older mails still point here.
 */
definePageMeta({
  layout: 'station',
  name: 'member-documents-legacy',
  redirect: '/station/documents/store',
})
</script>

<template>
  <div/>
</template>
