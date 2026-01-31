CREATE SEQUENCE ${ohdsiSchema}.cdm_cache_seq;

CREATE TABLE ${ohdsiSchema}.cdm_cache
(
    id                      BIGINT NOT NULL DEFAULT NEXT VALUE FOR ${ohdsiSchema}.cdm_cache_seq,
    concept_id              INT NOT NULL,
    source_id               INT NOT NULL,
    record_count            BIGINT NULL,
    descendant_record_count BIGINT NULL,
    person_count            BIGINT NULL,
    descendant_person_count BIGINT NULL,
    CONSTRAINT cdm_cache_pk PRIMARY KEY (id),
    CONSTRAINT cdm_cache_un UNIQUE (concept_id, source_id),
    CONSTRAINT cdm_cache_fk FOREIGN KEY (source_id) REFERENCES ${ohdsiSchema}.source (source_id) ON DELETE CASCADE
);

CREATE INDEX cdm_cache_concept_id_idx ON ${ohdsiSchema}.cdm_cache (concept_id, source_id);
