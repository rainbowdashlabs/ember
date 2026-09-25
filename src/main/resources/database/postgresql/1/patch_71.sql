-- A form and a page can each be given a link that reaches them and nothing else.
--
-- Sending somebody a poll meant putting it on a public page first, and a public page is part of a
-- station's site: in its menu, in its sitemap, found by anybody looking. A form now carries a link
-- of its own, so it can be sent to the people it is meant for and to nobody else, the way a waiting
-- list confirmation already is.
--
-- A page gains the same, and with it a third state. Until now a page was either a draft or public,
-- and the flag that said which becomes a visibility that can also say "reachable by its link". Such
-- a page stands outside the station's page tree and outside its menu: one page behind one link.
--
-- Both links can be replaced, which is what makes them worth having. A link sent to the wrong
-- people is the ordinary accident here, and a new link ends every copy of the old one.

ALTER TABLE ember_schema.form
    ADD COLUMN share_token TEXT UNIQUE,
    ADD COLUMN visibility  TEXT NOT NULL DEFAULT 'PUBLIC'
        CONSTRAINT form_visibility_known CHECK (visibility IN ('PUBLIC', 'UNLISTED'));

COMMENT ON COLUMN ember_schema.form.visibility IS
    'How far a form meant for people outside the station reaches. PUBLIC: anybody, at the form''s own stable address, which is what a form put on a public page needs. UNLISTED: only whoever was sent the link, and the stable address answers nothing, so replacing the link really does end every way in. Says nothing about whether the form takes answers; its status and dates decide that. Existing forms are public, which is what they already were.';

COMMENT ON COLUMN ember_schema.form.share_token IS
    'The link this form is reached by when it is sent to somebody, as 32 random bytes in URL-safe Base64. Null until somebody asks for the link, so a form that is never sent carries none. Replacing it ends every copy of the previous link, which is the only way to withdraw one: the form''s own start and end dates decide whether it takes answers, so the link itself never expires.';

ALTER TABLE ember_schema.station_page
    ADD COLUMN visibility   TEXT NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT station_page_visibility_known CHECK (visibility IN ('DRAFT', 'UNLISTED', 'PUBLIC')),
    ADD COLUMN share_token  TEXT UNIQUE;

COMMENT ON COLUMN ember_schema.station_page.visibility IS
    'Who reaches this page. DRAFT: nobody outside the station. UNLISTED: anybody holding its link, and it stands outside the page tree, out of the menu and out of the sitemap. PUBLIC: anybody, at the path its slugs spell. Replaces the published flag, which could not say the middle case.';
COMMENT ON COLUMN ember_schema.station_page.share_token IS
    'The link this page is reached by while it is unlisted, as 32 random bytes in URL-safe Base64. Minted when the page first becomes unlisted and kept afterwards, so a page opened to the public and closed again is reached by the same link as before. Replacing it ends every copy of the previous one.';

UPDATE ember_schema.station_page
SET visibility = 'PUBLIC'
WHERE published;

ALTER TABLE ember_schema.station_page
    DROP COLUMN published;
