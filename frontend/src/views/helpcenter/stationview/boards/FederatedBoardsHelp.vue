/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
</script>

<template>
    <HelpArticle title="Föderierte Boards" subtitle="Boards von Partnerwachen nutzen">
        <HelpSection title="Was sind föderierte Boards?">
            <p>Föderierte Boards sind Kanban-Boards, die von Partnerwachen mit deiner Wache geteilt werden. So könnt ihr gemeinsam an Aufgaben arbeiten oder den Fortschritt der anderen Wache verfolgen.</p>
        </HelpSection>

        <HelpSection title="Zugriffsmodi">
            <p>Jedes geteilte Board hat einen Zugriffsmodus:</p>
            <ul class="list-disc ml-4 space-y-1">
                <li><strong>Nur Lesen:</strong> Du kannst das Board und alle Tickets sehen, aber nichts erstellen, bearbeiten oder kommentieren. Dies ist ein reiner Einblick.</li>
                <li><strong>Vollzugriff:</strong> Du kannst Tickets erstellen, bearbeiten, verschieben, kommentieren und beobachten — genau wie bei einem eigenen Board.</li>
            </ul>
        </HelpSection>

        <HelpSection title="Board entdecken">
            <p>In der Übersicht siehst du alle Boards, die von Partnerwachen geteilt werden. Die Boards sind nach Partnerwache gruppiert. Neben jedem Board siehst du ein Symbol, das den Zugriffsmodus anzeigt.</p>
        </HelpSection>

        <HelpSection title="Lesezeichen">
            <p>Setze ein Lesezeichen auf Boards, die du häufig nutzt. Gemerkte Boards erscheinen in der Seitenleiste unter deinen eigenen Boards, sodass du sie mit einem Klick erreichen kannst.</p>
        </HelpSection>

        <HelpSection title="Zugriffseinschränkungen">
            <p>Euer Board-Manager kann den Zugriff auf föderierte Boards zusätzlich einschränken. Diese lokalen Einschränkungen können nur den Zugriff weiter begrenzen, nie erweitern. So kann der Manager bestimmte Rollen, Gruppen oder Tags festlegen, die Zugriff auf ein geteiltes Board haben.</p>
        </HelpSection>

        <HelpTip>
            <p>Wenn ein geteiltes Board nicht mehr sichtbar ist, wurde es möglicherweise von der Partnerwache nicht mehr geteilt. Die Lesezeichen werden automatisch entfernt.</p>
        </HelpTip>
    </HelpArticle>
</template>
