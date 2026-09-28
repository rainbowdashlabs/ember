-- The text a document's search index was built from is kept beside the index.
--
-- The index is stemmed by the database, and a new major version of PostgreSQL may stem the same word
-- differently. An index built by the old version then no longer matches what the new one makes of a
-- search, and it can only be rebuilt from the text it came from. For the wiki that text is stored
-- already; for a document it was read out of the file once and thrown away, so rebuilding meant
-- reading every file back out of storage.

ALTER TABLE ember_schema.member_document_search
    ADD COLUMN source_text TEXT;

COMMENT ON COLUMN ember_schema.member_document_search.source_text IS
    'The text the search index was built from: the title and whatever could be read out of the file. Empty for documents indexed before it was kept.';
