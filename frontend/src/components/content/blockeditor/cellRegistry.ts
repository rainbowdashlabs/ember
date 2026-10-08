/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Component} from 'vue'
import type {LayoutKindName} from '@/api/pageManage'
import CellLayoutRenderEvents from './CellLayoutRenderEvents.vue'
import CellPeopleRenders from './CellPeopleRenders.vue'
import CellEventEditors from './CellEventEditors.vue'
import CellQuizEditor from './CellQuizEditor.vue'
import CellMemberListEditor from './CellMemberListEditor.vue'
import CalloutCell from './cells/CalloutCell.vue'
import QuoteCell from './cells/QuoteCell.vue'
import DividerCell from './cells/DividerCell.vue'
import SpacerCell from './cells/SpacerCell.vue'
import AccordionCell from './cells/AccordionCell.vue'
import PdfCell from './cells/PdfCell.vue'
import FileDownloadCell from './cells/FileDownloadCell.vue'
import CountdownCell from './cells/CountdownCell.vue'
import KbArticleCell from './cells/KbArticleCell.vue'
import NewsTeaserCell from './cells/NewsTeaserCell.vue'
import PageLinkCell from './cells/PageLinkCell.vue'
import MapCell from './cells/MapCell.vue'
import AddressCardCell from './cells/AddressCardCell.vue'
import PartnerStationsCell from './cells/PartnerStationsCell.vue'
import StatsCounterCell from './cells/StatsCounterCell.vue'
import ImageGalleryCell from './cells/ImageGalleryCell.vue'
import HeroBannerCell from './cells/HeroBannerCell.vue'
import TabsCell from './cells/TabsCell.vue'
import AchievementsCell from './cells/AchievementsCell.vue'
import ExternalLinkCardCell from './cells/ExternalLinkCardCell.vue'
import BlogSignupCell from './cells/BlogSignupCell.vue'
import AudioEmbedCell from './cells/AudioEmbedCell.vue'
import PollEmbedCell from './cells/PollEmbedCell.vue'
import QuizTeaserCell from './cells/QuizTeaserCell.vue'
import FormsCtaCell from './cells/FormsCtaCell.vue'
import CodeBlockCell from './cells/CodeBlockCell.vue'
import SignatureCell from './cells/SignatureCell.vue'
import FillInCell from './cells/FillInCell.vue'
import CalloutEditor from './editors/CalloutEditor.vue'
import QuoteEditor from './editors/QuoteEditor.vue'
import DividerEditor from './editors/DividerEditor.vue'
import SpacerEditor from './editors/SpacerEditor.vue'
import AccordionEditor from './editors/AccordionEditor.vue'
import PdfEditor from './editors/PdfEditor.vue'
import FileDownloadEditor from './editors/FileDownloadEditor.vue'
import CountdownEditor from './editors/CountdownEditor.vue'
import PartnerStationsEditor from './editors/PartnerStationsEditor.vue'
import StatsCounterEditor from './editors/StatsCounterEditor.vue'
import TabsEditor from './editors/TabsEditor.vue'
import AchievementsEditor from './editors/AchievementsEditor.vue'
import ImageGalleryEditor from './editors/ImageGalleryEditor.vue'
import KbArticleEditor from './editors/KbArticleEditor.vue'
import NewsTeaserEditor from './editors/NewsTeaserEditor.vue'
import PageLinkEditor from './editors/PageLinkEditor.vue'
import MapEditor from './editors/MapEditor.vue'
import AddressCardEditor from './editors/AddressCardEditor.vue'
import MemberSpotlightEditor from './editors/MemberSpotlightEditor.vue'
import HeroBannerEditor from './editors/HeroBannerEditor.vue'
import ExternalLinkCardEditor from './editors/ExternalLinkCardEditor.vue'
import BlogSignupEditor from './editors/BlogSignupEditor.vue'
import AudioEmbedEditor from './editors/AudioEmbedEditor.vue'
import PollEmbedEditor from './editors/PollEmbedEditor.vue'
import FormsCtaEditor from './editors/FormsCtaEditor.vue'
import CodeBlockEditor from './editors/CodeBlockEditor.vue'
import SignatureEditor from './editors/SignatureEditor.vue'
import FillInEditor from './editors/FillInEditor.vue'

/**
 * What a cell component is handed besides its configuration, which every one of them takes.
 *
 * <p>`content` is the free text body, `stationUid` the station the cell reads from, `timezone` the
 * clock a date is written on, and `kind` the cell type itself, which only the components drawing
 * several related types need.
 */
export type CellInput = 'content' | 'stationUid' | 'timezone' | 'kind'

/** One component of a cell type together with the inputs it takes. */
export interface CellPart {
    component: Component
    takes: readonly CellInput[]
}

/** How one layout cell type is drawn on a page and how it is configured in the editor. */
export interface LayoutCell {
    render: CellPart
    editor: CellPart
}

function part(component: Component, ...takes: CellInput[]): CellPart {
    return {component, takes}
}

const EVENTS_RENDER = part(CellLayoutRenderEvents, 'kind', 'stationUid', 'timezone')
const EVENTS_EDITOR = part(CellEventEditors, 'kind', 'stationUid')
const PEOPLE_RENDER = part(CellPeopleRenders, 'kind')

/**
 * Every layout cell type, keyed by its kind: the one place a new type is added.
 *
 * <p>The page renderer and the editor panel both look a type up here rather than each keeping a
 * switch of their own, so the two can no longer fall out of step, and the record's type makes
 * a kind without an entry fail the type-check.
 */
export const LAYOUT_CELLS: Record<LayoutKindName, LayoutCell> = {
    CALLOUT: {render: part(CalloutCell, 'content'), editor: part(CalloutEditor, 'content')},
    QUOTE: {render: part(QuoteCell, 'content'), editor: part(QuoteEditor, 'content', 'stationUid')},
    DIVIDER: {render: part(DividerCell), editor: part(DividerEditor)},
    SPACER: {render: part(SpacerCell), editor: part(SpacerEditor)},
    ACCORDION: {render: part(AccordionCell, 'content'), editor: part(AccordionEditor, 'content')},
    PDF: {render: part(PdfCell), editor: part(PdfEditor, 'stationUid')},
    FILE_DOWNLOAD: {render: part(FileDownloadCell), editor: part(FileDownloadEditor, 'stationUid')},
    COUNTDOWN: {render: part(CountdownCell), editor: part(CountdownEditor)},
    FEATURED_EVENT: {render: EVENTS_RENDER, editor: EVENTS_EDITOR},
    UPCOMING_EVENTS: {render: EVENTS_RENDER, editor: EVENTS_EDITOR},
    PAST_EVENT_RECAP: {render: EVENTS_RENDER, editor: EVENTS_EDITOR},
    KB_ARTICLE: {render: part(KbArticleCell, 'stationUid'), editor: part(KbArticleEditor, 'stationUid')},
    NEWS_TEASER: {
        render: part(NewsTeaserCell, 'stationUid', 'timezone'),
        editor: part(NewsTeaserEditor, 'stationUid'),
    },
    PAGE_LINK: {render: part(PageLinkCell, 'stationUid'), editor: part(PageLinkEditor, 'stationUid')},
    MAP: {render: part(MapCell), editor: part(MapEditor)},
    ADDRESS_CARD: {render: part(AddressCardCell), editor: part(AddressCardEditor)},
    PARTNER_STATIONS: {
        render: part(PartnerStationsCell, 'stationUid'),
        editor: part(PartnerStationsEditor, 'stationUid'),
    },
    MEMBER_SPOTLIGHT: {render: PEOPLE_RENDER, editor: part(MemberSpotlightEditor)},
    MEMBER_LIST_SPOTLIGHT: {render: PEOPLE_RENDER, editor: part(CellMemberListEditor)},
    STATS_COUNTER: {render: part(StatsCounterCell), editor: part(StatsCounterEditor)},
    IMAGE_GALLERY: {
        render: part(ImageGalleryCell, 'stationUid'),
        editor: part(ImageGalleryEditor, 'stationUid'),
    },
    HERO_BANNER: {render: part(HeroBannerCell, 'stationUid'), editor: part(HeroBannerEditor, 'stationUid')},
    TABS: {render: part(TabsCell), editor: part(TabsEditor)},
    ACHIEVEMENTS: {render: part(AchievementsCell), editor: part(AchievementsEditor)},
    EXTERNAL_LINK_CARD: {
        render: part(ExternalLinkCardCell),
        editor: part(ExternalLinkCardEditor, 'stationUid'),
    },
    BLOG_SIGNUP: {render: part(BlogSignupCell, 'stationUid'), editor: part(BlogSignupEditor)},
    AUDIO_EMBED: {render: part(AudioEmbedCell), editor: part(AudioEmbedEditor, 'stationUid')},
    POLL_EMBED: {render: part(PollEmbedCell, 'stationUid'), editor: part(PollEmbedEditor)},
    QUIZ_TEASER: {render: part(QuizTeaserCell, 'stationUid'), editor: part(CellQuizEditor, 'stationUid')},
    FORMS_CTA: {render: part(FormsCtaCell, 'stationUid'), editor: part(FormsCtaEditor)},
    CODE_BLOCK: {render: part(CodeBlockCell, 'content'), editor: part(CodeBlockEditor, 'content')},
    SIGNATURE: {render: part(SignatureCell, 'content'), editor: part(SignatureEditor, 'content')},
    FILL_IN: {render: part(FillInCell), editor: part(FillInEditor)},
}

/**
 * The props a cell component is bound with: its configuration, and of the inputs on offer only the
 * ones it takes, so no component is handed an attribute it does not declare.
 */
export function cellProps(
    cellPart: CellPart,
    config: Record<string, unknown>,
    inputs: Partial<Record<CellInput, unknown>>,
): Record<string, unknown> {
    const props: Record<string, unknown> = {config}
    for (const input of cellPart.takes) props[input] = inputs[input]
    return props
}
