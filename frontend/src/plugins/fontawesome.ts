/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {library, config} from '@fortawesome/fontawesome-svg-core'
import '@fortawesome/fontawesome-svg-core/styles.css'
import {FontAwesomeIcon} from '@fortawesome/vue-fontawesome'
import {
    faAngleDown, faAngleUp, faAnglesDown, faAnglesUp, faArrowDownWideShort, faArrowRight,
    faArrowsRotate, faArrowUpWideShort, faEllipsis, faEquals, faAsterisk, faBan, faBars, faBell, faBold,
    faBoxesStacked, faBoxOpen, faBuilding, faBullhorn, faCalendarDays, faCalendarPlus, faChartLine, faCheck,
    faCheckDouble, faChevronDown, faChevronLeft, faChevronRight, faChevronUp, faCircle,
    faCircleCheck, faCircleDot, faCircleUp, faCircleInfo, faClipboardCheck, faClipboardList, faClipboardUser,
    faCode, faClockRotateLeft, faComment, faCopy, faDownload, faEye, faFileCode, faFileExport,
    faFire, faFolderPlus, faGauge, faGear, faGears, faGlobe, faGripVertical, faHardDrive, faHashtag, faHeading,
    faHighlighter, faTextHeight, faHouse, faIdCard, faItalic, faLayerGroup, faAlignCenter, faAlignJustify, faAlignLeft,
    faAlignRight, faLink, faLinkSlash, faList, faListOl, faListUl, faLock, faLockOpen, faMedal, faMinus,
    faNewspaper, faMobileScreen, faMoon, faParagraph, faPen, faPenToSquare, faPeopleGroup,
    faPhone, faPlus, faQuoteLeft, faRainbow, faRightFromBracket, faRightLeft, faRightToBracket, faRss,
    faRotate, faScissors, faShield, faShuffle, faScaleBalanced, faSitemap, faSliders, faSort, faSortDown,
    faSortUp, faSpinner, faStrikethrough, faSun, faTableColumns, faTableList, faTrash, faTriangleExclamation,
    faUnderline, faUpload, faUser, faUserCheck, faUserGroup, faUserMinus, faUserPlus, faUserSlash, faUsers,
    faUsersGear, faXmark, faXmarkCircle, faCircleQuestion, faFilter, faTags, faBarcode,
    faSquarePollVertical, faStar, faHeart, faThumbsUp, faGripLines, faLocationDot, faCamera,
    faToggleOn, faArrowRightArrowLeft, faPuzzlePiece, faPalette, faBook, faServer, faDatabase,
    faEnvelope, faEnvelopeOpenText, faArrowDown, faBug, faFlag, faPaperclip, faInbox, faExpand, faRocket, faShirt,
    faShareNodes, faHandshake, faRobot, faCircleXmark, faRotateLeft, faCodeCompare, faTrophy,
    faRedo, faArrowLeft, faHandHolding, faCalendarXmark, faCheckCircle, faEyeSlash, faDesktop,
    faUserGear, faCalendar, faChartBar, faChartPie, faClock, faHand, faImage, faPaperPlane,
    faPlug, faTag, faUmbrellaBeach, faUserShield, faUserTie, faListCheck, faCircleHalfStroke,
    faSquare, faSquareCheck, faGraduationCap, faBrain, faFileLines, faFilePdf, faFileImport, faFilm, faMusic,
    faFlask, faPlay, faBookOpen, faFolder, faFolderOpen, faFile, faFileSignature, faFileCirclePlus, faFont, faSignature, faMagnifyingGlass, faCompass,
    faMagnifyingGlassPlus, faMagnifyingGlassMinus,
    faDiagramProject, faFileCsv, faReply, faShieldHalved, faAt, faPaste, faClone, faHourglassHalf, faArrowUp,
    faSatelliteDish, faMapLocationDot, faUserClock, faFloppyDisk, faBoxArchive, faCakeCandles,
    faFilePowerpoint, faDisplay, faKey, faArrowsUpDown, faLightbulb, faBroom, faTowerBroadcast,
    faEllipsisVertical, faWarehouse, faBox, faSuitcase, faCube, faFingerprint, faPrint,
    faHelmetSafety, faVest, faVestPatches, faMitten, faShoePrints, faSocks, faMask, faHeadSideMask,
    faGlasses, faRadio, faWalkieTalkie, faHeadphones, faStapler, faToolbox, faScrewdriverWrench, faWrench, faHammer,
    faFireExtinguisher, faWaterLadder, faBatteryFull, faTrowel, faKitMedical, faSuitcaseMedical,
    faTruckMedical, faDice, faDiceD20, faTent, faRuler, faBagShopping, faBriefcase,
} from '@fortawesome/free-solid-svg-icons'
import {
    faGithub, faWindows, faApple, faLinux, faAndroid, faChrome, faFirefoxBrowser, faSafari,
    faEdge, faOpera, faYoutube,
} from '@fortawesome/free-brands-svg-icons'

config.autoAddCss = false

library.add(
    faDiagramProject, faSun, faMoon, faCheck, faAngleDown, faAngleUp, faAnglesDown, faAnglesUp, faEllipsis,
    faEquals, faXmark, faXmarkCircle, faSpinner, faCircleInfo, faCircleCheck, faCircle,
    faCircleDot, faCircleUp, faTriangleExclamation, faDownload, faUpload, faTrash, faPen, faLock, faLockOpen,
    faRightFromBracket, faRightToBracket, faBars, faGauge, faChevronDown, faChevronRight,
    faBullhorn,
    faHouse, faChartLine, faShield, faSitemap, faBuilding, faGears, faUsers, faUserGroup, faUserPlus,
    faRightLeft, faList,
    faLayerGroup, faBoxesStacked, faBoxOpen, faClipboardUser, faCalendarPlus, faClockRotateLeft,
    faClipboardCheck, faUsersGear, faPlus, faChevronLeft, faChevronUp, faGripVertical, faCopy,
    faBell, faPhone, faMobileScreen, faIdCard, faHashtag, faFire, faMedal, faRainbow,
    faCalendarDays, faPenToSquare, faFolderPlus, faClipboardList, faUser, faSort, faSortUp,
    faSortDown, faAsterisk, faEye, faLink, faTableColumns, faTableList, faBan, faBarcode, faComment, faCheckDouble,
    faMinus, faRotate, faRss, faScissors, faNewspaper, faGear, faPeopleGroup, faArrowRight,
    faFileExport, faGithub, faWindows, faApple, faLinux, faAndroid, faChrome, faFirefoxBrowser,
    faSafari, faEdge, faOpera, faGlobe, faUserSlash, faUserCheck, faCircleQuestion, faFilter,
    faTags, faSquarePollVertical, faStar, faHeart, faThumbsUp, faGripLines, faLocationDot,
    faCamera, faToggleOn, faArrowRightArrowLeft, faPuzzlePiece, faBook, faServer, faDatabase,
    faHardDrive, faArrowsRotate, faEnvelope, faEnvelopeOpenText, faArrowDown, faUserGear, faCalendar, faChartBar, faChartPie, faClock, faHand,
    faImage, faPaperPlane, faPlug, faTag, faUmbrellaBeach, faUserMinus, faUserShield, faUserTie,
    faListCheck, faScaleBalanced, faSliders, faCircleHalfStroke, faPalette, faSquare,
    faSquareCheck, faGraduationCap, faBrain, faFileLines, faFilePdf, faFileImport, faFilm, faMusic, faFlask,
    faShuffle, faPlay, faBookOpen, faFolder, faFolderOpen, faFile, faFileSignature, faFileCirclePlus, faFont, faSignature, faMagnifyingGlass, faYoutube,
    faMagnifyingGlassPlus, faMagnifyingGlassMinus,
    faBold, faItalic, faUnderline, faStrikethrough, faCode, faListUl, faListOl, faQuoteLeft,
    faFileCode, faParagraph, faHeading, faHighlighter, faTextHeight, faLinkSlash, faAlignLeft, faAlignCenter,
    faAlignRight, faAlignJustify, faBug, faShareNodes, faHandshake, faCompass, faRobot, faCircleXmark,
    faRotateLeft, faCodeCompare, faTrophy, faRedo, faArrowLeft, faHandHolding, faCalendarXmark,
    faCheckCircle, faEyeSlash, faDesktop, faFlag, faPaperclip, faInbox, faExpand, faRocket,
    faArrowDownWideShort, faArrowUpWideShort, faFileCsv, faReply, faShieldHalved, faAt,
    faPaste, faClone, faHourglassHalf, faArrowUp, faSatelliteDish, faMapLocationDot,
    faUserClock, faFloppyDisk, faBoxArchive, faCakeCandles, faFilePowerpoint, faDisplay, faKey,
    faArrowsUpDown, faLightbulb, faBroom, faTowerBroadcast, faEllipsisVertical,
    faWarehouse, faBox, faSuitcase, faCube, faFingerprint, faShirt,
    faHelmetSafety, faVest, faVestPatches, faMitten, faShoePrints, faSocks, faMask, faHeadSideMask,
    faGlasses, faRadio, faWalkieTalkie, faHeadphones, faStapler, faToolbox, faScrewdriverWrench, faWrench, faHammer,
    faFireExtinguisher, faWaterLadder, faBatteryFull, faTrowel, faKitMedical, faSuitcaseMedical,
    faTruckMedical, faDice, faDiceD20, faTent, faRuler, faBagShopping, faBriefcase, faPrint,
)

export default defineNuxtPlugin((nuxtApp) => {
    nuxtApp.vueApp.component('font-awesome-icon', FontAwesomeIcon)
})
