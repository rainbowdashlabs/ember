/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
</script>

<template>
    <HelpArticle title="Boards verwalten">
        <HelpSection title="Boards erstellen und bearbeiten">
            <p>Hier kannst du neue Boards erstellen, bestehende bearbeiten oder entfernen.</p>
            <p>Jedes Board hat ein Kuerzel (z.B. PLAN), das als Praefix fuer Ticket-Nummern verwendet wird.</p>
            <p>Ueber das Zahnrad-Symbol gelangst du zu den Einstellungen eines Boards (Spalten, Felder, Zugriffsrechte).</p>
        </HelpSection>
    </HelpArticle>
</template>
