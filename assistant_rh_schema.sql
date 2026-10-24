--
-- PostgreSQL database dump
--

\restrict BEHmv4VX6F7vm0E8muSMfmTm2UzVSl3MXdCDAct57VGcQN7D1GRy3PT5MuYu628

-- Dumped from database version 18.0
-- Dumped by pg_dump version 18.0

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA public;


--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


--
-- Name: analyser_poste(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.analyser_poste(p_poste_id integer) RETURNS json
    LANGUAGE sql
    AS $$
    SELECT json_build_object(
        'poste',
        json_build_object(
            'id', p.id,
            'intitule', p.intitule,
            'description', p.description,
            'service', s.nom
        ),

        'competences_requises',
        (
            SELECT json_agg(
                json_build_object(
                    'id', c.id,
                    'nom', c.nom,
                    'niveau_requis', pc.niveau_requis
                )
                ORDER BY pc.niveau_requis DESC, c.nom
            )
            FROM poste_competence pc
            JOIN competence c
                ON c.id = pc.competence_id
            WHERE pc.poste_id = p.id
        ),

        'candidats',
        (
            SELECT json_agg(
                json_build_object(
                    'employe_id', m.employe_id,
                    'nom', m.nom,
                    'prenom', m.prenom,
                    'score', m.score_couverture,
                    'competences_satisfaites', m.competences_satisfaites,
                    'competences_insuffisantes', m.competences_insuffisantes,
                    'competences_manquantes', m.competences_manquantes,
                    'competences_insuffisantes_liste', m.competences_insuffisantes_liste,
                    'competences_manquantes_liste', m.competences_manquantes_liste
                )
                ORDER BY m.score_couverture DESC
            )
            FROM get_matching_employees(p.id) m
        )
    )
    FROM poste p
    JOIN service s
        ON s.id = p.service_id
    WHERE p.id = p_poste_id;
$$;


--
-- Name: get_matching_employees(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_matching_employees(p_poste_id integer) RETURNS TABLE(employe_id integer, nom character varying, prenom character varying, score_couverture numeric, competences_satisfaites bigint, competences_insuffisantes bigint, competences_manquantes bigint, competences_insuffisantes_liste text, competences_manquantes_liste text)
    LANGUAGE sql
    AS $$
    WITH details AS (
        SELECT
            e.id AS employe_id,
            e.nom,
            e.prenom,
            c.nom AS competence,
            pc.niveau_requis,
            COALESCE(ec.niveau, 0) AS niveau_employe,
            CASE
                WHEN COALESCE(ec.niveau, 0) >= pc.niveau_requis
                    THEN 'SATISFAIT'
                WHEN COALESCE(ec.niveau, 0) = 0
                    THEN 'MANQUANTE'
                ELSE 'INSUFFISANT'
            END AS resultat
        FROM employe e
        CROSS JOIN poste_competence pc
        JOIN competence c
            ON c.id = pc.competence_id
        LEFT JOIN employe_competence ec
            ON ec.employe_id = e.id
            AND ec.competence_id = pc.competence_id
        WHERE pc.poste_id = p_poste_id
    )

    SELECT
        employe_id,
        nom,
        prenom,

        ROUND(
            AVG(
                LEAST(
                    niveau_employe::numeric / niveau_requis,
                    1
                )
            ) * 100,
            2
        ) AS score_couverture,

        COUNT(*) FILTER (
            WHERE resultat = 'SATISFAIT'
        ) AS competences_satisfaites,

        COUNT(*) FILTER (
            WHERE resultat = 'INSUFFISANT'
        ) AS competences_insuffisantes,

        COUNT(*) FILTER (
            WHERE resultat = 'MANQUANTE'
        ) AS competences_manquantes,

        STRING_AGG(
            competence,
            ', '
        ) FILTER (
            WHERE resultat = 'INSUFFISANT'
        ) AS competences_insuffisantes_liste,

        STRING_AGG(
            competence,
            ', '
        ) FILTER (
            WHERE resultat = 'MANQUANTE'
        ) AS competences_manquantes_liste

    FROM details

    GROUP BY employe_id, nom, prenom

    ORDER BY score_couverture DESC;
$$;


--
-- Name: get_poste_by_name(character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_poste_by_name(p_intitule character varying) RETURNS TABLE(poste_id integer, intitule character varying, description text, service character varying)
    LANGUAGE sql
    AS $$
    SELECT
        p.id AS poste_id,
        p.intitule,
        p.description,
        s.nom AS service
    FROM poste p
    JOIN service s
        ON s.id = p.service_id
    WHERE LOWER(p.intitule) = LOWER(p_intitule);
$$;


--
-- Name: get_poste_competences(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_poste_competences(p_poste_id integer) RETURNS TABLE(competence_id integer, competence character varying, niveau_requis integer)
    LANGUAGE sql
    AS $$
    SELECT
        c.id AS competence_id,
        c.nom AS competence,
        pc.niveau_requis
    FROM poste_competence pc
    JOIN competence c
        ON c.id = pc.competence_id
    WHERE pc.poste_id = p_poste_id
    ORDER BY pc.niveau_requis DESC, c.nom;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: activite; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.activite (
    id bigint NOT NULL,
    acteur_id bigint,
    employe_id bigint,
    type_action character varying(100) NOT NULL,
    description text NOT NULL,
    date_heure timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: activite_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.activite ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.activite_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: affectation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.affectation (
    id integer NOT NULL,
    employe_id integer NOT NULL,
    poste_id integer NOT NULL,
    service_id integer NOT NULL,
    date_debut date NOT NULL,
    date_fin date,
    reference_acte text,
    observation text,
    CONSTRAINT ck_affectation_dates CHECK (((date_fin IS NULL) OR (date_fin >= date_debut)))
);


--
-- Name: affectation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.affectation ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.affectation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: avancement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.avancement (
    id integer NOT NULL,
    employe_id integer NOT NULL,
    type_avancement character varying(100) NOT NULL,
    situation_avant_id integer,
    situation_apres_id integer,
    date_demande date,
    date_proposition date,
    date_decision date,
    date_effet date,
    statut character varying(100) NOT NULL,
    reference_acte text,
    observation text
);


--
-- Name: avancement_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.avancement ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.avancement_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: categorie; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categorie (
    id integer NOT NULL,
    code character varying(10) NOT NULL,
    corps_id integer
);


--
-- Name: categorie_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.categorie ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.categorie_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: classe; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.classe (
    id integer NOT NULL,
    libelle character varying(200) NOT NULL,
    ordre integer
);


--
-- Name: classe_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.classe ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.classe_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: corps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.corps (
    id integer NOT NULL,
    code character varying(100) NOT NULL,
    libelle character varying(200) NOT NULL,
    description text
);


--
-- Name: corps_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.corps ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.corps_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: direction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.direction (
    id integer NOT NULL,
    nom character varying(200) NOT NULL,
    description text
);


--
-- Name: direction_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.direction ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.direction_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: document; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.document (
    id integer CONSTRAINT document_administratif_id_not_null NOT NULL,
    employe_id integer,
    type_document_id integer CONSTRAINT document_administratif_type_document_id_not_null NOT NULL,
    reference_document character varying(150),
    objet text,
    date_document date,
    date_effet date,
    statut character varying(50),
    fichier_path text,
    observation text,
    destinataire character varying(255),
    date_creation timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    donnees jsonb DEFAULT '{}'::jsonb NOT NULL,
    CONSTRAINT ck_document_dates CHECK (((date_effet IS NULL) OR (date_document IS NULL) OR (date_effet >= date_document))),
    CONSTRAINT ck_document_donnees_object CHECK ((jsonb_typeof(donnees) = 'object'::text))
);


--
-- Name: document_administratif_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.document ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.document_administratif_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: domaine_etude; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.domaine_etude (
    id integer NOT NULL,
    libelle character varying(200) NOT NULL,
    actif boolean DEFAULT true NOT NULL
);


--
-- Name: domaine_etude_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.domaine_etude ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.domaine_etude_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: echelon; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.echelon (
    id integer NOT NULL,
    classe_id integer NOT NULL,
    ordre integer NOT NULL,
    duree_min integer
);


--
-- Name: echelon_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.echelon ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.echelon_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: employe; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employe (
    id integer NOT NULL,
    user_id integer,
    matricule character varying(30) NOT NULL,
    nom character varying(100) NOT NULL,
    prenom character varying(100),
    sexe character varying(20),
    adresse character varying(255),
    cin character varying(30),
    telephone character varying(30),
    date_naissance date,
    lieu_naissance character varying(150),
    date_embauche date,
    type_emploi_id integer,
    categorie_id integer,
    lieu_travail character varying(200),
    photo character varying(500),
    localite_residence_id bigint
);


--
-- Name: employe_diplome; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employe_diplome (
    id integer NOT NULL,
    employe_id integer NOT NULL,
    date_obtention date NOT NULL,
    numero_diplome character varying(100),
    principal boolean DEFAULT false NOT NULL,
    observation text,
    niveau_id integer NOT NULL,
    domaine_id integer NOT NULL,
    etablissement_id integer NOT NULL,
    mention_id integer
);


--
-- Name: employe_diplome_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.employe_diplome ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.employe_diplome_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: employe_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.employe ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.employe_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: etablissement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.etablissement (
    id integer NOT NULL,
    nom character varying(250) NOT NULL,
    ville character varying(150),
    actif boolean DEFAULT true NOT NULL,
    statut_validation character varying(20) DEFAULT 'VALIDE'::character varying NOT NULL,
    CONSTRAINT etablissement_statut_validation_check CHECK (((statut_validation)::text = ANY ((ARRAY['VALIDE'::character varying, 'A_VALIDER'::character varying])::text[])))
);


--
-- Name: etablissement_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.etablissement ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.etablissement_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: historique_carriere; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.historique_carriere (
    id integer CONSTRAINT situation_carriere_id_not_null NOT NULL,
    employe_id integer CONSTRAINT situation_carriere_employe_id_not_null NOT NULL,
    date_debut date CONSTRAINT situation_carriere_date_debut_not_null NOT NULL,
    date_fin date,
    echelon_id integer NOT NULL,
    categorie_id integer,
    CONSTRAINT ck_historique_carriere_dates CHECK (((date_fin IS NULL) OR (date_fin >= date_debut)))
);


--
-- Name: localite; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.localite (
    id bigint NOT NULL,
    niveau character varying(16) NOT NULL,
    nom character varying(200) NOT NULL,
    parent_id bigint,
    parent_niveau character varying(16),
    origine character varying(16) DEFAULT 'HDX_OCHA'::character varying NOT NULL,
    source_name character varying(100),
    source_code character varying(64),
    source_version character varying(32),
    source_valid_on date,
    source_valid_to date,
    source_license character varying(100),
    imported_at timestamp with time zone,
    CONSTRAINT ck_localite_parent_level CHECK (((((niveau)::text = 'REGION'::text) AND (parent_id IS NULL) AND (parent_niveau IS NULL)) OR (((niveau)::text = 'DISTRICT'::text) AND (parent_id IS NOT NULL) AND ((parent_niveau)::text = 'REGION'::text)) OR (((niveau)::text = 'COMMUNE'::text) AND (parent_id IS NOT NULL) AND ((parent_niveau)::text = 'DISTRICT'::text)) OR (((niveau)::text = 'FOKONTANY'::text) AND (parent_id IS NOT NULL) AND ((parent_niveau)::text = 'COMMUNE'::text)))),
    CONSTRAINT ck_localite_source_code CHECK (((((origine)::text = 'HDX_OCHA'::text) AND (source_name IS NOT NULL) AND (source_code IS NOT NULL)) OR (((origine)::text = 'MANUEL'::text) AND (source_code IS NULL)))),
    CONSTRAINT localite_niveau_check CHECK (((niveau)::text = ANY ((ARRAY['REGION'::character varying, 'DISTRICT'::character varying, 'COMMUNE'::character varying, 'FOKONTANY'::character varying])::text[]))),
    CONSTRAINT localite_nom_check CHECK ((btrim((nom)::text) <> ''::text)),
    CONSTRAINT localite_origine_check CHECK (((origine)::text = ANY ((ARRAY['HDX_OCHA'::character varying, 'MANUEL'::character varying])::text[])))
);


--
-- Name: localite_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.localite ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.localite_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: mention; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mention (
    id integer NOT NULL,
    libelle character varying(100) NOT NULL,
    actif boolean DEFAULT true NOT NULL
);


--
-- Name: mention_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.mention ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.mention_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: niveau_diplome; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.niveau_diplome (
    id integer NOT NULL,
    code character varying(50) NOT NULL,
    libelle character varying(200) NOT NULL,
    categorie_id integer NOT NULL,
    ordre_academique integer,
    CONSTRAINT ck_niveau_diplome_ordre_academique CHECK (((ordre_academique IS NULL) OR (ordre_academique > 0)))
);


--
-- Name: COLUMN niveau_diplome.ordre_academique; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.niveau_diplome.ordre_academique IS 'Ordre configuré par le Service du Personnel; les valeurs les plus élevées représentent les niveaux supérieurs.';


--
-- Name: niveau_diplome_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.niveau_diplome ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.niveau_diplome_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: notification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification (
    id bigint NOT NULL,
    destinataire_id integer NOT NULL,
    type character varying(40) NOT NULL,
    titre character varying(160) NOT NULL,
    message text NOT NULL,
    employe_id integer,
    acteur_id integer,
    cle_deduplication character varying(160) NOT NULL,
    cree_le timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    lu_le timestamp without time zone,
    CONSTRAINT notification_type_check CHECK (((type)::text = ANY ((ARRAY['NOUVEL_AGENT'::character varying, 'ANCIENNETE_UN_MOIS'::character varying, 'ANCIENNETE_ATTEINTE'::character varying])::text[])))
);


--
-- Name: notification_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.notification ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: offre_formation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offre_formation (
    id integer NOT NULL,
    niveau_id integer NOT NULL,
    domaine_id integer NOT NULL,
    etablissement_id integer NOT NULL,
    actif boolean DEFAULT true NOT NULL
);


--
-- Name: offre_formation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.offre_formation ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.offre_formation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: poste; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.poste (
    id integer NOT NULL,
    intitule character varying(150) NOT NULL,
    description text,
    service_id integer NOT NULL
);


--
-- Name: poste_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.poste ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.poste_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: service; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.service (
    id integer NOT NULL,
    code character varying(20) NOT NULL,
    nom character varying(200) NOT NULL,
    description text,
    direction_id integer
);


--
-- Name: service_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.service ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.service_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: situation_carriere_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.historique_carriere ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.situation_carriere_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: statut_agent; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.statut_agent (
    id integer NOT NULL,
    code character varying(50) NOT NULL,
    libelle character varying(150) NOT NULL,
    description text,
    date_debut_validite date,
    date_fin_validite date
);


--
-- Name: statut_agent_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.statut_agent ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.statut_agent_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: type_document; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.type_document (
    id integer CONSTRAINT type_document_administratif_id_not_null NOT NULL,
    code character varying(50) CONSTRAINT type_document_administratif_code_not_null NOT NULL,
    libelle character varying(150) CONSTRAINT type_document_administratif_libelle_not_null NOT NULL,
    description text,
    date_debut_validite date,
    date_fin_validite date
);


--
-- Name: type_document_administratif_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.type_document ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.type_document_administratif_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: type_emploi; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.type_emploi (
    id integer NOT NULL,
    nom character varying(100) NOT NULL
);


--
-- Name: type_emploi_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.type_emploi ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.type_emploi_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: utilisateur; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.utilisateur (
    id integer NOT NULL,
    email character varying(150) NOT NULL,
    password_hash character varying(255),
    role character varying(50) DEFAULT 'SPERS_AGENT'::character varying NOT NULL,
    statut_compte character varying(30) DEFAULT 'ACTIF'::character varying NOT NULL
);


--
-- Name: utilisateur_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.utilisateur ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.utilisateur_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: ville; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ville (
    source_type character varying(16) NOT NULL,
    source_id character varying(32) NOT NULL,
    nom character varying(200) NOT NULL,
    CONSTRAINT ck_ville_source_type CHECK (((source_type)::text = ANY ((ARRAY['node'::character varying, 'way'::character varying, 'relation'::character varying, 'geonames'::character varying])::text[]))),
    CONSTRAINT ville_nom_check CHECK ((btrim((nom)::text) <> ''::text))
);


--
-- Name: activite activite_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activite
    ADD CONSTRAINT activite_pkey PRIMARY KEY (id);


--
-- Name: affectation affectation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.affectation
    ADD CONSTRAINT affectation_pkey PRIMARY KEY (id);


--
-- Name: avancement avancement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avancement
    ADD CONSTRAINT avancement_pkey PRIMARY KEY (id);


--
-- Name: categorie categorie_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categorie
    ADD CONSTRAINT categorie_code_key UNIQUE (code);


--
-- Name: categorie categorie_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categorie
    ADD CONSTRAINT categorie_pkey PRIMARY KEY (id);


--
-- Name: classe classe_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classe
    ADD CONSTRAINT classe_pkey PRIMARY KEY (id);


--
-- Name: corps corps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.corps
    ADD CONSTRAINT corps_pkey PRIMARY KEY (id);


--
-- Name: direction direction_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.direction
    ADD CONSTRAINT direction_pkey PRIMARY KEY (id);


--
-- Name: document document_administratif_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT document_administratif_pkey PRIMARY KEY (id);


--
-- Name: domaine_etude domaine_etude_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.domaine_etude
    ADD CONSTRAINT domaine_etude_pkey PRIMARY KEY (id);


--
-- Name: echelon echelon_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.echelon
    ADD CONSTRAINT echelon_pkey PRIMARY KEY (id);


--
-- Name: employe employe_cin_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employe
    ADD CONSTRAINT employe_cin_key UNIQUE (cin);


--
-- Name: employe_diplome employe_diplome_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employe_diplome
    ADD CONSTRAINT employe_diplome_pkey PRIMARY KEY (id);


--
-- Name: employe employe_matricule_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employe
    ADD CONSTRAINT employe_matricule_key UNIQUE (matricule);


--
-- Name: employe employe_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employe
    ADD CONSTRAINT employe_pkey PRIMARY KEY (id);


--
-- Name: employe employe_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employe
    ADD CONSTRAINT employe_user_id_key UNIQUE (user_id);


--
-- Name: etablissement etablissement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etablissement
    ADD CONSTRAINT etablissement_pkey PRIMARY KEY (id);


--
-- Name: historique_carriere historique_carriere_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historique_carriere
    ADD CONSTRAINT historique_carriere_pkey PRIMARY KEY (id);


--
-- Name: localite localite_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.localite
    ADD CONSTRAINT localite_pkey PRIMARY KEY (id);


--
-- Name: mention mention_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mention
    ADD CONSTRAINT mention_pkey PRIMARY KEY (id);


--
-- Name: niveau_diplome niveau_diplome_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.niveau_diplome
    ADD CONSTRAINT niveau_diplome_code_key UNIQUE (code);


--
-- Name: niveau_diplome niveau_diplome_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.niveau_diplome
    ADD CONSTRAINT niveau_diplome_pkey PRIMARY KEY (id);


--
-- Name: notification notification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);


--
-- Name: offre_formation offre_formation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offre_formation
    ADD CONSTRAINT offre_formation_pkey PRIMARY KEY (id);


--
-- Name: poste poste_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.poste
    ADD CONSTRAINT poste_pkey PRIMARY KEY (id);


--
-- Name: service service_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service
    ADD CONSTRAINT service_code_key UNIQUE (code);


--
-- Name: service service_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service
    ADD CONSTRAINT service_pkey PRIMARY KEY (id);


--
-- Name: statut_agent statut_agent_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.statut_agent
    ADD CONSTRAINT statut_agent_code_key UNIQUE (code);


--
-- Name: statut_agent statut_agent_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.statut_agent
    ADD CONSTRAINT statut_agent_pkey PRIMARY KEY (id);


--
-- Name: type_document type_document_administratif_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.type_document
    ADD CONSTRAINT type_document_administratif_code_key UNIQUE (code);


--
-- Name: type_document type_document_administratif_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.type_document
    ADD CONSTRAINT type_document_administratif_pkey PRIMARY KEY (id);


--
-- Name: type_emploi type_emploi_nom_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.type_emploi
    ADD CONSTRAINT type_emploi_nom_key UNIQUE (nom);


--
-- Name: type_emploi type_emploi_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.type_emploi
    ADD CONSTRAINT type_emploi_pkey PRIMARY KEY (id);


--
-- Name: classe uq_classe_ordre; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classe
    ADD CONSTRAINT uq_classe_ordre UNIQUE (ordre);


--
-- Name: corps uq_corps_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.corps
    ADD CONSTRAINT uq_corps_code UNIQUE (code);


--
-- Name: echelon uq_echelon_classe_ordre; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.echelon
    ADD CONSTRAINT uq_echelon_classe_ordre UNIQUE (classe_id, ordre);


--
-- Name: localite uq_localite_id_niveau; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.localite
    ADD CONSTRAINT uq_localite_id_niveau UNIQUE (id, niveau);


--
-- Name: notification uq_notification_destinataire_cle; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT uq_notification_destinataire_cle UNIQUE (destinataire_id, cle_deduplication);


--
-- Name: offre_formation uq_offre_formation; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offre_formation
    ADD CONSTRAINT uq_offre_formation UNIQUE (niveau_id, domaine_id, etablissement_id);


--
-- Name: utilisateur utilisateur_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.utilisateur
    ADD CONSTRAINT utilisateur_email_key UNIQUE (email);


--
-- Name: utilisateur utilisateur_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.utilisateur
    ADD CONSTRAINT utilisateur_pkey PRIMARY KEY (id);


--
-- Name: ville ville_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ville
    ADD CONSTRAINT ville_pkey PRIMARY KEY (source_type, source_id);


--
-- Name: idx_direction_nom_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_direction_nom_trgm ON public.direction USING gin (translate(lower((COALESCE(nom, ''::character varying))::text), 'àáâãäåèéêëìíîïòóôõöùúûüçñýÿ'::text, 'aaaaaaeeeeiiiiooooouuuucnyy'::text) public.gin_trgm_ops);


--
-- Name: idx_employe_nom; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_employe_nom ON public.employe USING btree (nom);


--
-- Name: idx_employe_nom_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_employe_nom_trgm ON public.employe USING gin (translate(lower((COALESCE(nom, ''::character varying))::text), 'àáâãäåèéêëìíîïòóôõöùúûüçñýÿ'::text, 'aaaaaaeeeeiiiiooooouuuucnyy'::text) public.gin_trgm_ops);


--
-- Name: idx_employe_prenom; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_employe_prenom ON public.employe USING btree (prenom);


--
-- Name: idx_employe_prenom_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_employe_prenom_trgm ON public.employe USING gin (translate(lower((COALESCE(prenom, ''::character varying))::text), 'àáâãäåèéêëìíîïòóôõöùúûüçñýÿ'::text, 'aaaaaaeeeeiiiiooooouuuucnyy'::text) public.gin_trgm_ops);


--
-- Name: idx_notification_destinataire_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_destinataire_date ON public.notification USING btree (destinataire_id, cree_le DESC, id DESC);


--
-- Name: idx_notification_non_lue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_non_lue ON public.notification USING btree (destinataire_id, id) WHERE (lu_le IS NULL);


--
-- Name: idx_poste_intitule_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_poste_intitule_trgm ON public.poste USING gin (translate(lower((COALESCE(intitule, ''::character varying))::text), 'àáâãäåèéêëìíîïòóôõöùúûüçñýÿ'::text, 'aaaaaaeeeeiiiiooooouuuucnyy'::text) public.gin_trgm_ops);


--
-- Name: idx_poste_service; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_poste_service ON public.poste USING btree (service_id);


--
-- Name: idx_service_direction; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_service_direction ON public.service USING btree (direction_id);


--
-- Name: idx_service_nom_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_service_nom_trgm ON public.service USING gin (translate(lower((COALESCE(nom, ''::character varying))::text), 'àáâãäåèéêëìíîïòóôõöùúûüçñýÿ'::text, 'aaaaaaeeeeiiiiooooouuuucnyy'::text) public.gin_trgm_ops);


--
-- Name: ix_employe_localite_residence; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_employe_localite_residence ON public.employe USING btree (localite_residence_id) WHERE (localite_residence_id IS NOT NULL);


--
-- Name: ix_localite_nom_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_localite_nom_trgm ON public.localite USING gin (lower((nom)::text) public.gin_trgm_ops);


--
-- Name: ix_localite_parent_niveau; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_localite_parent_niveau ON public.localite USING btree (parent_id, niveau, id);


--
-- Name: ix_ville_nom; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_ville_nom ON public.ville USING btree (lower((nom)::text), source_type, source_id);


--
-- Name: uq_affectation_employe_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_affectation_employe_active ON public.affectation USING btree (employe_id) WHERE (date_fin IS NULL);


--
-- Name: uq_domaine_etude_libelle; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_domaine_etude_libelle ON public.domaine_etude USING btree (lower(btrim((libelle)::text)));


--
-- Name: uq_echelon_classe_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_echelon_classe_id ON public.echelon USING btree (classe_id, id);


--
-- Name: uq_employe_diplome_normalise_sans_reference; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_employe_diplome_normalise_sans_reference ON public.employe_diplome USING btree (employe_id, niveau_id, domaine_id, date_obtention, etablissement_id, COALESCE(mention_id, 0)) WHERE ((numero_diplome IS NULL) OR (btrim((numero_diplome)::text) = ''::text));


--
-- Name: uq_employe_diplome_reference; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_employe_diplome_reference ON public.employe_diplome USING btree (employe_id, lower(btrim((numero_diplome)::text))) WHERE ((numero_diplome IS NOT NULL) AND (btrim((numero_diplome)::text) <> ''::text));


--
-- Name: uq_etablissement_nom; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_etablissement_nom ON public.etablissement USING btree (lower(btrim((nom)::text)));


--
-- Name: uq_historique_carriere_employe_actuelle; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_historique_carriere_employe_actuelle ON public.historique_carriere USING btree (employe_id) WHERE (date_fin IS NULL);


--
-- Name: uq_localite_source_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_localite_source_code ON public.localite USING btree (source_name, source_code) WHERE (source_code IS NOT NULL);


--
-- Name: uq_mention_libelle; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_mention_libelle ON public.mention USING btree (lower(btrim((libelle)::text)));


--
-- Name: uq_niveau_diplome_ordre_academique; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_niveau_diplome_ordre_academique ON public.niveau_diplome USING btree (ordre_academique) WHERE (ordre_academique IS NOT NULL);


--
-- Name: activite fk_activite_acteur; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activite
    ADD CONSTRAINT fk_activite_acteur FOREIGN KEY (acteur_id) REFERENCES public.utilisateur(id);


--
-- Name: activite fk_activite_employe; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activite
    ADD CONSTRAINT fk_activite_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: affectation fk_affectation_employe; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.affectation
    ADD CONSTRAINT fk_affectation_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: affectation fk_affectation_poste; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.affectation
    ADD CONSTRAINT fk_affectation_poste FOREIGN KEY (poste_id) REFERENCES public.poste(id);


--
-- Name: affectation fk_affectation_service; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.affectation
    ADD CONSTRAINT fk_affectation_service FOREIGN KEY (service_id) REFERENCES public.service(id);


--
-- Name: avancement fk_avancement_employe; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avancement
    ADD CONSTRAINT fk_avancement_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: avancement fk_avancement_historique_apres; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avancement
    ADD CONSTRAINT fk_avancement_historique_apres FOREIGN KEY (situation_apres_id) REFERENCES public.historique_carriere(id);


--
-- Name: avancement fk_avancement_historique_avant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avancement
    ADD CONSTRAINT fk_avancement_historique_avant FOREIGN KEY (situation_avant_id) REFERENCES public.historique_carriere(id);


--
-- Name: categorie fk_categorie_corps; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categorie
    ADD CONSTRAINT fk_categorie_corps FOREIGN KEY (corps_id) REFERENCES public.corps(id);


--
-- Name: document fk_document_employe; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT fk_document_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: document fk_document_type; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT fk_document_type FOREIGN KEY (type_document_id) REFERENCES public.type_document(id);


--
-- Name: echelon fk_echelon_classe; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.echelon
    ADD CONSTRAINT fk_echelon_classe FOREIGN KEY (classe_id) REFERENCES public.classe(id);


--
-- Name: employe fk_employe_categorie; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employe
    ADD CONSTRAINT fk_employe_categorie FOREIGN KEY (categorie_id) REFERENCES public.categorie(id);


--
-- Name: employe_diplome fk_employe_diplome_domaine; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employe_diplome
    ADD CONSTRAINT fk_employe_diplome_domaine FOREIGN KEY (domaine_id) REFERENCES public.domaine_etude(id);


--
-- Name: employe_diplome fk_employe_diplome_employe; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employe_diplome
    ADD CONSTRAINT fk_employe_diplome_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: employe_diplome fk_employe_diplome_etablissement; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employe_diplome
    ADD CONSTRAINT fk_employe_diplome_etablissement FOREIGN KEY (etablissement_id) REFERENCES public.etablissement(id);


--
-- Name: employe_diplome fk_employe_diplome_mention; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employe_diplome
    ADD CONSTRAINT fk_employe_diplome_mention FOREIGN KEY (mention_id) REFERENCES public.mention(id);


--
-- Name: employe_diplome fk_employe_diplome_niveau; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employe_diplome
    ADD CONSTRAINT fk_employe_diplome_niveau FOREIGN KEY (niveau_id) REFERENCES public.niveau_diplome(id);


--
-- Name: employe fk_employe_localite_residence; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employe
    ADD CONSTRAINT fk_employe_localite_residence FOREIGN KEY (localite_residence_id) REFERENCES public.localite(id);


--
-- Name: employe fk_employe_type_emploi; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employe
    ADD CONSTRAINT fk_employe_type_emploi FOREIGN KEY (type_emploi_id) REFERENCES public.type_emploi(id);


--
-- Name: employe fk_employe_utilisateur; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employe
    ADD CONSTRAINT fk_employe_utilisateur FOREIGN KEY (user_id) REFERENCES public.utilisateur(id);


--
-- Name: historique_carriere fk_historique_carriere_categorie; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historique_carriere
    ADD CONSTRAINT fk_historique_carriere_categorie FOREIGN KEY (categorie_id) REFERENCES public.categorie(id);


--
-- Name: historique_carriere fk_historique_carriere_echelon; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historique_carriere
    ADD CONSTRAINT fk_historique_carriere_echelon FOREIGN KEY (echelon_id) REFERENCES public.echelon(id);


--
-- Name: historique_carriere fk_historique_carriere_employe; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historique_carriere
    ADD CONSTRAINT fk_historique_carriere_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: localite fk_localite_parent_niveau; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.localite
    ADD CONSTRAINT fk_localite_parent_niveau FOREIGN KEY (parent_id, parent_niveau) REFERENCES public.localite(id, niveau);


--
-- Name: niveau_diplome fk_niveau_diplome_categorie; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.niveau_diplome
    ADD CONSTRAINT fk_niveau_diplome_categorie FOREIGN KEY (categorie_id) REFERENCES public.categorie(id);


--
-- Name: poste fk_poste_service; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.poste
    ADD CONSTRAINT fk_poste_service FOREIGN KEY (service_id) REFERENCES public.service(id);


--
-- Name: service fk_service_direction; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service
    ADD CONSTRAINT fk_service_direction FOREIGN KEY (direction_id) REFERENCES public.direction(id);


--
-- Name: notification notification_acteur_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_acteur_id_fkey FOREIGN KEY (acteur_id) REFERENCES public.utilisateur(id) ON DELETE SET NULL;


--
-- Name: notification notification_destinataire_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_destinataire_id_fkey FOREIGN KEY (destinataire_id) REFERENCES public.utilisateur(id) ON DELETE CASCADE;


--
-- Name: notification notification_employe_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_employe_id_fkey FOREIGN KEY (employe_id) REFERENCES public.employe(id) ON DELETE SET NULL;


--
-- Name: offre_formation offre_formation_domaine_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offre_formation
    ADD CONSTRAINT offre_formation_domaine_id_fkey FOREIGN KEY (domaine_id) REFERENCES public.domaine_etude(id);


--
-- Name: offre_formation offre_formation_etablissement_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offre_formation
    ADD CONSTRAINT offre_formation_etablissement_id_fkey FOREIGN KEY (etablissement_id) REFERENCES public.etablissement(id);


--
-- Name: offre_formation offre_formation_niveau_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offre_formation
    ADD CONSTRAINT offre_formation_niveau_id_fkey FOREIGN KEY (niveau_id) REFERENCES public.niveau_diplome(id);


--
-- PostgreSQL database dump complete
--

\unrestrict BEHmv4VX6F7vm0E8muSMfmTm2UzVSl3MXdCDAct57VGcQN7D1GRy3PT5MuYu628

