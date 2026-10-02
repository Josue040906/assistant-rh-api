--
-- PostgreSQL database dump
--

\restrict KVcBOzPzv2eZZEdfJi3uyeeenh5v8KbYjyEWHIOhw3YYcM3EjjoY2lJsA6OGVzs

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
-- Name: pg_trgm; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;


--
-- Name: EXTENSION pg_trgm; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';


--
-- Name: analyser_poste(integer); Type: FUNCTION; Schema: public; Owner: postgres
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


ALTER FUNCTION public.analyser_poste(p_poste_id integer) OWNER TO postgres;

--
-- Name: get_matching_employees(integer); Type: FUNCTION; Schema: public; Owner: postgres
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


ALTER FUNCTION public.get_matching_employees(p_poste_id integer) OWNER TO postgres;

--
-- Name: get_poste_by_name(character varying); Type: FUNCTION; Schema: public; Owner: postgres
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


ALTER FUNCTION public.get_poste_by_name(p_intitule character varying) OWNER TO postgres;

--
-- Name: get_poste_competences(integer); Type: FUNCTION; Schema: public; Owner: postgres
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


ALTER FUNCTION public.get_poste_competences(p_poste_id integer) OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: activite; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.activite (
    id bigint NOT NULL,
    acteur_id bigint,
    employe_id bigint,
    type_action character varying(100) NOT NULL,
    description text NOT NULL,
    date_heure timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


ALTER TABLE public.activite OWNER TO postgres;

--
-- Name: activite_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
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
-- Name: affectation; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.affectation OWNER TO postgres;

--
-- Name: affectation_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
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
-- Name: article_juridique; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.article_juridique (
    id integer NOT NULL,
    texte_juridique_id integer NOT NULL,
    numero_article character varying(100) NOT NULL,
    titre character varying(255),
    contenu text,
    observation text
);


ALTER TABLE public.article_juridique OWNER TO postgres;

--
-- Name: article_juridique_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.article_juridique ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.article_juridique_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: avancement; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.avancement OWNER TO postgres;

--
-- Name: avancement_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
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
-- Name: cadre; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.cadre (
    id integer NOT NULL,
    code character varying(50) NOT NULL,
    libelle character varying(150) NOT NULL,
    description text,
    date_debut_validite date,
    date_fin_validite date
);


ALTER TABLE public.cadre OWNER TO postgres;

--
-- Name: cadre_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.cadre ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.cadre_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: categorie; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.categorie (
    id integer NOT NULL,
    code character varying(10) NOT NULL,
    diplome character varying(200)
);


ALTER TABLE public.categorie OWNER TO postgres;

--
-- Name: categorie_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
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
-- Name: cessation_service; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.cessation_service (
    id integer NOT NULL,
    employe_id integer NOT NULL,
    type_cessation_id integer NOT NULL,
    date_demande date,
    date_decision date,
    date_effet date NOT NULL,
    reference_acte text,
    type_acte character varying(100),
    motif text,
    observation text,
    CONSTRAINT ck_cessation_dates CHECK (((date_decision IS NULL) OR (date_demande IS NULL) OR (date_decision >= date_demande)))
);


ALTER TABLE public.cessation_service OWNER TO postgres;

--
-- Name: cessation_service_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.cessation_service ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.cessation_service_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: classe; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.classe (
    id integer NOT NULL,
    grade_carriere_id integer NOT NULL,
    code character varying(100) NOT NULL,
    libelle character varying(200) NOT NULL,
    ordre integer,
    description text,
    reference_juridique text,
    date_debut_validite date,
    date_fin_validite date
);


ALTER TABLE public.classe OWNER TO postgres;

--
-- Name: classe_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
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
-- Name: condition_regle_rh; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.condition_regle_rh (
    id integer NOT NULL,
    regle_id integer NOT NULL,
    code character varying(100) NOT NULL,
    libelle character varying(255) NOT NULL,
    type_condition character varying(100) NOT NULL,
    operateur character varying(50),
    valeur text,
    ordre integer NOT NULL,
    obligatoire boolean DEFAULT true NOT NULL,
    description text,
    CONSTRAINT ck_condition_ordre CHECK ((ordre > 0))
);


ALTER TABLE public.condition_regle_rh OWNER TO postgres;

--
-- Name: condition_regle_rh_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.condition_regle_rh ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.condition_regle_rh_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: corps; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.corps (
    id integer NOT NULL,
    echelle_id integer NOT NULL,
    code character varying(100) NOT NULL,
    libelle character varying(200) NOT NULL,
    description text,
    reference_juridique text,
    date_debut_validite date,
    date_fin_validite date
);


ALTER TABLE public.corps OWNER TO postgres;

--
-- Name: corps_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
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
-- Name: diplome; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.diplome (
    id integer NOT NULL,
    code character varying(100),
    libelle character varying(200) NOT NULL,
    niveau character varying(100),
    domaine character varying(200),
    description text,
    reference_juridique text,
    date_debut_validite date,
    date_fin_validite date
);


ALTER TABLE public.diplome OWNER TO postgres;

--
-- Name: diplome_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.diplome ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.diplome_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: direction; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.direction (
    id integer NOT NULL,
    nom character varying(200) NOT NULL,
    description text
);


ALTER TABLE public.direction OWNER TO postgres;

--
-- Name: direction_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
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
-- Name: document_administratif; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.document_administratif (
    id integer NOT NULL,
    employe_id integer,
    type_document_id integer NOT NULL,
    reference_document character varying(150),
    objet text,
    date_document date,
    date_effet date,
    statut character varying(50),
    fichier_path text,
    observation text,
    CONSTRAINT ck_document_dates CHECK (((date_effet IS NULL) OR (date_document IS NULL) OR (date_effet >= date_document)))
);


ALTER TABLE public.document_administratif OWNER TO postgres;

--
-- Name: document_administratif_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.document_administratif ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.document_administratif_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: document_evenement_carriere; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.document_evenement_carriere (
    id integer NOT NULL,
    document_id integer NOT NULL,
    evenement_carriere_id integer NOT NULL,
    observation text
);


ALTER TABLE public.document_evenement_carriere OWNER TO postgres;

--
-- Name: document_evenement_carriere_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.document_evenement_carriere ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.document_evenement_carriere_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: document_mouvement_administratif; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.document_mouvement_administratif (
    id integer NOT NULL,
    document_id integer NOT NULL,
    mouvement_administratif_id integer CONSTRAINT document_mouvement_administ_mouvement_administratif_id_not_null NOT NULL,
    observation text
);


ALTER TABLE public.document_mouvement_administratif OWNER TO postgres;

--
-- Name: document_mouvement_administratif_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.document_mouvement_administratif ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.document_mouvement_administratif_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: document_rh; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.document_rh (
    id bigint NOT NULL,
    reference character varying(50) NOT NULL,
    type character varying(30) NOT NULL,
    objet character varying(255) NOT NULL,
    contenu text,
    agent_id bigint,
    dossier_id bigint,
    statut character varying(30) DEFAULT 'BROUILLON'::character varying NOT NULL,
    auteur character varying(150),
    date_document date,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    date_modification timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT document_rh_statut_check CHECK (((statut)::text = ANY ((ARRAY['BROUILLON'::character varying, 'A_VERIFIER'::character varying, 'VALIDE'::character varying, 'SIGNE'::character varying, 'ARCHIVE'::character varying])::text[]))),
    CONSTRAINT document_rh_type_check CHECK (((type)::text = ANY ((ARRAY['DEMANDE'::character varying, 'COURRIER'::character varying, 'ACTE'::character varying])::text[])))
);


ALTER TABLE public.document_rh OWNER TO postgres;

--
-- Name: document_rh_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.document_rh_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.document_rh_id_seq OWNER TO postgres;

--
-- Name: document_rh_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.document_rh_id_seq OWNED BY public.document_rh.id;


--
-- Name: dossier_rh; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.dossier_rh (
    id bigint NOT NULL,
    reference character varying(50) NOT NULL,
    objet character varying(255) NOT NULL,
    agent_id bigint,
    statut character varying(30) DEFAULT 'OUVERT'::character varying NOT NULL,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    date_modification timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


ALTER TABLE public.dossier_rh OWNER TO postgres;

--
-- Name: dossier_rh_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.dossier_rh_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.dossier_rh_id_seq OWNER TO postgres;

--
-- Name: dossier_rh_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.dossier_rh_id_seq OWNED BY public.dossier_rh.id;


--
-- Name: echelle; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.echelle (
    id integer NOT NULL,
    cadre_id integer NOT NULL,
    code character varying(50) NOT NULL,
    libelle character varying(150) NOT NULL,
    description text,
    date_debut_validite date,
    date_fin_validite date
);


ALTER TABLE public.echelle OWNER TO postgres;

--
-- Name: echelle_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.echelle ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.echelle_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: echelon; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.echelon (
    id integer NOT NULL,
    classe_id integer NOT NULL,
    code character varying(100) NOT NULL,
    libelle character varying(200) NOT NULL,
    ordre integer NOT NULL,
    description text,
    reference_juridique text,
    date_debut_validite date,
    date_fin_validite date
);


ALTER TABLE public.echelon OWNER TO postgres;

--
-- Name: echelon_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
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
-- Name: effet_regle_rh; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.effet_regle_rh (
    id integer NOT NULL,
    regle_id integer NOT NULL,
    code character varying(100) NOT NULL,
    libelle character varying(255) NOT NULL,
    type_effet character varying(100) NOT NULL,
    valeur text,
    ordre integer NOT NULL,
    description text,
    CONSTRAINT ck_effet_ordre CHECK ((ordre > 0))
);


ALTER TABLE public.effet_regle_rh OWNER TO postgres;

--
-- Name: effet_regle_rh_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.effet_regle_rh ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.effet_regle_rh_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: employe; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.employe (
    id integer NOT NULL,
    user_id integer,
    matricule character varying(30) NOT NULL,
    nom character varying(100) NOT NULL,
    prenom character varying(100) NOT NULL,
    sexe character varying(20),
    adresse character varying(255),
    cin character varying(30),
    telephone character varying(30),
    date_naissance date,
    lieu_naissance character varying(150),
    date_embauche date,
    poste_id integer NOT NULL,
    service_id integer NOT NULL,
    type_emploi_id integer,
    categorie_id integer,
    grade_id integer,
    lieu_travail character varying(200),
    photo character varying(500)
);


ALTER TABLE public.employe OWNER TO postgres;

--
-- Name: employe_diplome; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.employe_diplome (
    id integer NOT NULL,
    employe_id integer NOT NULL,
    diplome_id integer NOT NULL,
    etablissement character varying(255),
    date_obtention date,
    numero_diplome character varying(100),
    mention character varying(100),
    principal boolean DEFAULT false NOT NULL,
    observation text
);


ALTER TABLE public.employe_diplome OWNER TO postgres;

--
-- Name: employe_diplome_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
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
-- Name: employe_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
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
-- Name: evaluation; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.evaluation (
    id integer NOT NULL,
    employe_id integer NOT NULL,
    annee integer NOT NULL,
    note numeric(5,2),
    appreciation text,
    evaluateur character varying(255),
    date_evaluation date,
    reference text,
    CONSTRAINT ck_evaluation_note CHECK (((note IS NULL) OR ((note >= (0)::numeric) AND (note <= (20)::numeric))))
);


ALTER TABLE public.evaluation OWNER TO postgres;

--
-- Name: evaluation_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.evaluation ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.evaluation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: evenement_carriere; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.evenement_carriere (
    id integer NOT NULL,
    employe_id integer NOT NULL,
    type_evenement character varying(100) NOT NULL,
    date_evenement date NOT NULL,
    date_effet date NOT NULL,
    situation_avant_id integer,
    situation_apres_id integer,
    reference_acte text,
    type_acte character varying(100),
    motif text,
    observation text,
    CONSTRAINT ck_evenement_dates CHECK ((date_effet >= date_evenement))
);


ALTER TABLE public.evenement_carriere OWNER TO postgres;

--
-- Name: evenement_carriere_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.evenement_carriere ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.evenement_carriere_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: grade; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.grade (
    id integer NOT NULL,
    code_grade character varying(50) NOT NULL,
    type_emploi_id integer
);


ALTER TABLE public.grade OWNER TO postgres;

--
-- Name: grade_carriere; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.grade_carriere (
    id integer NOT NULL,
    corps_id integer NOT NULL,
    code character varying(100) NOT NULL,
    libelle character varying(200) NOT NULL,
    description text,
    reference_juridique text,
    date_debut_validite date,
    date_fin_validite date
);


ALTER TABLE public.grade_carriere OWNER TO postgres;

--
-- Name: grade_carriere_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.grade_carriere ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.grade_carriere_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: grade_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.grade ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.grade_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: grille_indiciaire; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.grille_indiciaire (
    id integer NOT NULL,
    cadre_id integer NOT NULL,
    echelle_id integer NOT NULL,
    classe_id integer NOT NULL,
    echelon_id integer NOT NULL,
    indice integer NOT NULL,
    date_debut_validite date NOT NULL,
    date_fin_validite date,
    reference_juridique text
);


ALTER TABLE public.grille_indiciaire OWNER TO postgres;

--
-- Name: grille_indiciaire_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.grille_indiciaire ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.grille_indiciaire_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: mouvement_administratif; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.mouvement_administratif (
    id integer NOT NULL,
    employe_id integer NOT NULL,
    type_mouvement_id integer NOT NULL,
    poste_avant_id integer,
    service_avant_id integer,
    poste_apres_id integer,
    service_apres_id integer,
    date_demande date,
    date_decision date,
    date_effet date NOT NULL,
    reference_acte text,
    type_acte character varying(100),
    motif text,
    observation text,
    CONSTRAINT ck_mouvement_admin_dates CHECK (((date_decision IS NULL) OR (date_decision >= date_demande)))
);


ALTER TABLE public.mouvement_administratif OWNER TO postgres;

--
-- Name: mouvement_administratif_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.mouvement_administratif ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.mouvement_administratif_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: mouvement_anciennete; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.mouvement_anciennete (
    id integer NOT NULL,
    employe_id integer NOT NULL,
    type_mouvement character varying(100) NOT NULL,
    duree_jours integer NOT NULL,
    date_effet date NOT NULL,
    motif text,
    reference_acte text,
    observation text,
    CONSTRAINT ck_mouvement_anciennete_duree CHECK ((duree_jours <> 0))
);


ALTER TABLE public.mouvement_anciennete OWNER TO postgres;

--
-- Name: mouvement_anciennete_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.mouvement_anciennete ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.mouvement_anciennete_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: notification; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.notification (
    id integer NOT NULL,
    user_id integer NOT NULL,
    titre character varying(200) NOT NULL,
    message text NOT NULL,
    lu boolean DEFAULT false NOT NULL,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


ALTER TABLE public.notification OWNER TO postgres;

--
-- Name: notification_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.notification ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: parametre_regle_rh; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.parametre_regle_rh (
    id integer NOT NULL,
    code character varying(100) NOT NULL,
    libelle character varying(255) NOT NULL,
    type_valeur character varying(50) NOT NULL,
    unite character varying(50),
    description text,
    date_debut_validite date,
    date_fin_validite date
);


ALTER TABLE public.parametre_regle_rh OWNER TO postgres;

--
-- Name: parametre_regle_rh_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.parametre_regle_rh ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.parametre_regle_rh_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: poste; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.poste (
    id integer NOT NULL,
    intitule character varying(150) NOT NULL,
    description text,
    service_id integer NOT NULL
);


ALTER TABLE public.poste OWNER TO postgres;

--
-- Name: poste_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
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
-- Name: regle_population; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.regle_population (
    id integer NOT NULL,
    regle_id integer NOT NULL,
    type_population character varying(100) NOT NULL,
    statut_agent_id integer,
    cadre_id integer,
    echelle_id integer,
    corps_id integer,
    grade_carriere_id integer,
    observation text
);


ALTER TABLE public.regle_population OWNER TO postgres;

--
-- Name: regle_population_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.regle_population ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.regle_population_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: regle_reference_juridique; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.regle_reference_juridique (
    id integer NOT NULL,
    regle_id integer NOT NULL,
    texte_juridique_id integer NOT NULL,
    article_juridique_id integer,
    role_reference character varying(100),
    observation text
);


ALTER TABLE public.regle_reference_juridique OWNER TO postgres;

--
-- Name: regle_reference_juridique_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.regle_reference_juridique ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.regle_reference_juridique_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: regle_rh; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.regle_rh (
    id integer NOT NULL,
    type_regle_id integer NOT NULL,
    code character varying(100) NOT NULL,
    libelle character varying(255) NOT NULL,
    description text,
    population_concernee character varying(255),
    reference_juridique text,
    article character varying(100),
    date_debut_validite date,
    date_fin_validite date,
    priorite integer,
    active boolean DEFAULT true NOT NULL,
    CONSTRAINT ck_regle_priorite CHECK (((priorite IS NULL) OR (priorite >= 0)))
);


ALTER TABLE public.regle_rh OWNER TO postgres;

--
-- Name: regle_rh_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.regle_rh ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.regle_rh_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: retraite; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.retraite (
    id integer NOT NULL,
    employe_id integer NOT NULL,
    date_demande date,
    date_admission date,
    date_effet date NOT NULL,
    type_retraite character varying(100),
    age_au_depart numeric(5,2),
    reference_acte text,
    type_acte character varying(100),
    observation text,
    CONSTRAINT ck_retraite_dates CHECK (((date_admission IS NULL) OR (date_demande IS NULL) OR (date_admission >= date_demande)))
);


ALTER TABLE public.retraite OWNER TO postgres;

--
-- Name: retraite_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.retraite ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.retraite_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: service; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.service (
    id integer NOT NULL,
    code character varying(20) NOT NULL,
    nom character varying(200) NOT NULL,
    description text,
    direction_id integer
);


ALTER TABLE public.service OWNER TO postgres;

--
-- Name: service_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
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
-- Name: situation_administrative; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.situation_administrative (
    id integer NOT NULL,
    employe_id integer NOT NULL,
    code character varying(50) NOT NULL,
    libelle character varying(150) NOT NULL,
    date_debut date NOT NULL,
    date_fin date,
    reference_acte text,
    motif text,
    observation text,
    CONSTRAINT ck_situation_admin_dates CHECK (((date_fin IS NULL) OR (date_fin >= date_debut)))
);


ALTER TABLE public.situation_administrative OWNER TO postgres;

--
-- Name: situation_administrative_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.situation_administrative ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.situation_administrative_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: situation_carriere; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.situation_carriere (
    id integer NOT NULL,
    employe_id integer NOT NULL,
    date_debut date NOT NULL,
    date_fin date,
    statut_agent_id integer,
    cadre_id integer,
    echelle_id integer,
    corps_id integer,
    grade_carriere_id integer,
    classe_id integer,
    echelon_id integer,
    indice integer,
    origine character varying(100),
    reference_acte text,
    date_acte date,
    observation text,
    CONSTRAINT ck_situation_dates CHECK (((date_fin IS NULL) OR (date_fin >= date_debut)))
);


ALTER TABLE public.situation_carriere OWNER TO postgres;

--
-- Name: situation_carriere_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.situation_carriere ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.situation_carriere_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: statut_agent; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.statut_agent (
    id integer NOT NULL,
    code character varying(50) NOT NULL,
    libelle character varying(150) NOT NULL,
    description text,
    date_debut_validite date,
    date_fin_validite date
);


ALTER TABLE public.statut_agent OWNER TO postgres;

--
-- Name: statut_agent_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
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
-- Name: tache; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.tache (
    id integer NOT NULL,
    user_id integer NOT NULL,
    employe_id integer,
    titre character varying(200) NOT NULL,
    description text,
    statut character varying(30) DEFAULT 'A_FAIRE'::character varying NOT NULL,
    priorite character varying(30) DEFAULT 'NORMALE'::character varying NOT NULL,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    date_echeance timestamp without time zone,
    CONSTRAINT chk_tache_priorite CHECK (((priorite)::text = ANY ((ARRAY['FAIBLE'::character varying, 'NORMALE'::character varying, 'HAUTE'::character varying, 'URGENTE'::character varying])::text[]))),
    CONSTRAINT chk_tache_statut CHECK (((statut)::text = ANY ((ARRAY['A_FAIRE'::character varying, 'EN_COURS'::character varying, 'TERMINEE'::character varying, 'ANNULEE'::character varying])::text[])))
);


ALTER TABLE public.tache OWNER TO postgres;

--
-- Name: tache_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.tache ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tache_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: texte_juridique; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.texte_juridique (
    id integer NOT NULL,
    type_texte character varying(100) NOT NULL,
    numero character varying(100),
    titre character varying(500) NOT NULL,
    date_texte date,
    autorite character varying(255),
    objet text,
    reference_publication text,
    fichier_path text,
    url_source text,
    date_debut_validite date,
    date_fin_validite date,
    observation text
);


ALTER TABLE public.texte_juridique OWNER TO postgres;

--
-- Name: texte_juridique_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.texte_juridique ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.texte_juridique_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: type_cessation_service; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.type_cessation_service (
    id integer NOT NULL,
    code character varying(50) NOT NULL,
    libelle character varying(150) NOT NULL,
    description text,
    date_debut_validite date,
    date_fin_validite date
);


ALTER TABLE public.type_cessation_service OWNER TO postgres;

--
-- Name: type_cessation_service_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.type_cessation_service ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.type_cessation_service_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: type_document_administratif; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.type_document_administratif (
    id integer NOT NULL,
    code character varying(50) NOT NULL,
    libelle character varying(150) NOT NULL,
    description text,
    date_debut_validite date,
    date_fin_validite date
);


ALTER TABLE public.type_document_administratif OWNER TO postgres;

--
-- Name: type_document_administratif_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.type_document_administratif ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.type_document_administratif_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: type_emploi; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.type_emploi (
    id integer NOT NULL,
    nom character varying(100) NOT NULL
);


ALTER TABLE public.type_emploi OWNER TO postgres;

--
-- Name: type_emploi_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
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
-- Name: type_mouvement_administratif; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.type_mouvement_administratif (
    id integer NOT NULL,
    code character varying(50) NOT NULL,
    libelle character varying(150) NOT NULL,
    description text,
    date_debut_validite date,
    date_fin_validite date
);


ALTER TABLE public.type_mouvement_administratif OWNER TO postgres;

--
-- Name: type_mouvement_administratif_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.type_mouvement_administratif ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.type_mouvement_administratif_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: type_regle_rh; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.type_regle_rh (
    id integer NOT NULL,
    code character varying(100) NOT NULL,
    libelle character varying(200) NOT NULL,
    description text,
    date_debut_validite date,
    date_fin_validite date
);


ALTER TABLE public.type_regle_rh OWNER TO postgres;

--
-- Name: type_regle_rh_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.type_regle_rh ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.type_regle_rh_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: utilisateur; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.utilisateur (
    id integer NOT NULL,
    email character varying(150) NOT NULL,
    password_hash character varying(255),
    role character varying(50) DEFAULT 'SPERS_AGENT'::character varying NOT NULL,
    statut_compte character varying(30) DEFAULT 'ACTIF'::character varying NOT NULL
);


ALTER TABLE public.utilisateur OWNER TO postgres;

--
-- Name: utilisateur_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
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
-- Name: valeur_parametre_regle; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.valeur_parametre_regle (
    id integer NOT NULL,
    parametre_id integer NOT NULL,
    valeur character varying(255) NOT NULL,
    date_debut_validite date NOT NULL,
    date_fin_validite date,
    reference_juridique text,
    observation text,
    CONSTRAINT ck_valeur_parametre_dates CHECK (((date_fin_validite IS NULL) OR (date_fin_validite >= date_debut_validite)))
);


ALTER TABLE public.valeur_parametre_regle OWNER TO postgres;

--
-- Name: valeur_parametre_regle_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.valeur_parametre_regle ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.valeur_parametre_regle_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: document_rh id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.document_rh ALTER COLUMN id SET DEFAULT nextval('public.document_rh_id_seq'::regclass);


--
-- Name: dossier_rh id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.dossier_rh ALTER COLUMN id SET DEFAULT nextval('public.dossier_rh_id_seq'::regclass);


--
-- Data for Name: activite; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.activite (id, acteur_id, employe_id, type_action, description, date_heure) FROM stdin;
1	\N	9	CREATION_COMPTE	Demande de création de compte SYGPERS pour l'agent TEST-NOTIF-001 - compte placé en attente de validation.	2026-09-29 15:41:19.907921
\.


--
-- Data for Name: affectation; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.affectation (id, employe_id, poste_id, service_id, date_debut, date_fin, reference_acte, observation) FROM stdin;
\.


--
-- Data for Name: article_juridique; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.article_juridique (id, texte_juridique_id, numero_article, titre, contenu, observation) FROM stdin;
1	1	2	Missions du corps	Les concepteurs sont charges des taches de conception correspondant a leur specialite et non devolues a un corps de fonctionnaires cree anterieurement. Ils peuvent egalement exercer des fonctions administratives, d enseignement ou de recherche correspondant a leur specialite.	Decret n 96-746 du 27 aout 1996.
2	1	3	Hierarchie et echelonement indiciaire	La hierarchie et l echelonement indiciaire du corps des concepteurs comprennent le concepteur de classe exceptionnelle, le concepteur principal, le concepteur de 1er classe, le concepteur de 2e classe et le concepteur stagiaire.	Les grades, classes, echelons et indices sont modelises dans les tables de carriere et de grille indiciaire.
3	1	4	Limitation des recrutements	Aucun recrutement ne peut etre effectue dans le corps au dela de l effectif des agents des cadres et echelles concernes inscrits au budget de l exercice en cours.	Decret n 96-746 du 27 aout 1996.
4	1	9	Avancement d echelon	L avancement d echelon dans une meme classe des fonctionnaires du present corps est constate par arrete a deux ans d anciennete.	Texte repris du Decret n 96-746 du 27 aout 1996.
\.


--
-- Data for Name: avancement; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.avancement (id, employe_id, type_avancement, situation_avant_id, situation_apres_id, date_demande, date_proposition, date_decision, date_effet, statut, reference_acte, observation) FROM stdin;
\.


--
-- Data for Name: cadre; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.cadre (id, code, libelle, description, date_debut_validite, date_fin_validite) FROM stdin;
1	A	Cadre A	Cadre A de la fonction publique.	2003-09-03	\N
2	B	Cadre B	Cadre B de la fonction publique.	2003-09-03	\N
3	C	Cadre C	Cadre C de la fonction publique.	2003-09-03	\N
4	D	Cadre D	Cadre D de la fonction publique.	2003-09-03	\N
\.


--
-- Data for Name: categorie; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.categorie (id, code, diplome) FROM stdin;
1	I	Ã€ renseigner selon la nomenclature officielle
2	II	Ã€ renseigner selon la nomenclature officielle
3	III	Ã€ renseigner selon la nomenclature officielle
4	IV	Ã€ renseigner selon la nomenclature officielle
5	V	Ã€ renseigner selon la nomenclature officielle
6	VI	Ã€ renseigner selon la nomenclature officielle
7	VII	Ã€ renseigner selon la nomenclature officielle
8	VIII	Ã€ renseigner selon la nomenclature officielle
9	IX	Ã€ renseigner selon la nomenclature officielle
10	X	Ã€ renseigner selon la nomenclature officielle
\.


--
-- Data for Name: cessation_service; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.cessation_service (id, employe_id, type_cessation_id, date_demande, date_decision, date_effet, reference_acte, type_acte, motif, observation) FROM stdin;
\.


--
-- Data for Name: classe; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.classe (id, grade_carriere_id, code, libelle, ordre, description, reference_juridique, date_debut_validite, date_fin_validite) FROM stdin;
1	1	2E_CLASSE	2e classe	1	Deuxieme classe du grade de concepteur.	Decret n 96-746 du 27 aout 1996, article 3	1996-08-27	\N
2	1	1RE_CLASSE	1re classe	2	Premiere classe du grade de concepteur.	Decret n 96-746 du 27 aout 1996, article 3	1996-08-27	\N
3	1	PRINCIPAL	Principal	3	Classe principale du grade de concepteur.	Decret n 96-746 du 27 aout 1996, article 3	1996-08-27	\N
4	1	EXCEPTIONNELLE	Classe exceptionnelle	4	Classe exceptionnelle du grade de concepteur.	Decret n 96-746 du 27 aout 1996, article 3	1996-08-27	\N
\.


--
-- Data for Name: condition_regle_rh; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.condition_regle_rh (id, regle_id, code, libelle, type_condition, operateur, valeur, ordre, obligatoire, description) FROM stdin;
1	1	ANCIENNETE_ECHELON	Anciennete minimale dans l echelon	ANCIENNETE	>=	24	1	t	L agent doit justifier de deux ans d anciennete dans son echelon actuel.
\.


--
-- Data for Name: corps; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.corps (id, echelle_id, code, libelle, description, reference_juridique, date_debut_validite, date_fin_validite) FROM stdin;
1	1	CONCEPTEURS	Corps des concepteurs	Corps de fonctionnaires charge de taches de conception correspondant a leur specialite.	Decret n 96-746 du 27 aout 1996	1996-08-27	\N
\.


--
-- Data for Name: diplome; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.diplome (id, code, libelle, niveau, domaine, description, reference_juridique, date_debut_validite, date_fin_validite) FROM stdin;
\.


--
-- Data for Name: direction; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.direction (id, nom, description) FROM stdin;
1	Direction Générale du Budget et des Finances	Direction de démonstration représentant un niveau organisationnel supérieur.
2	Direction du Budget	Gestion et suivi des activités liées au budget.
3	Direction du Patrimoine de l'Etat	Gestion administrative du patrimoine de l'Etat.
5	Direction de la Solde et des Pensions	Gestion de la solde et des pensions des agents de l'Etat.
4	Direction de la Gestion des Effectifs des Agents de l'Etat	Gestion administrative des effectifs des agents travaillant pour l'Etat.
\.


--
-- Data for Name: document_administratif; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.document_administratif (id, employe_id, type_document_id, reference_document, objet, date_document, date_effet, statut, fichier_path, observation) FROM stdin;
\.


--
-- Data for Name: document_evenement_carriere; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.document_evenement_carriere (id, document_id, evenement_carriere_id, observation) FROM stdin;
\.


--
-- Data for Name: document_mouvement_administratif; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.document_mouvement_administratif (id, document_id, mouvement_administratif_id, observation) FROM stdin;
\.


--
-- Data for Name: document_rh; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.document_rh (id, reference, type, objet, contenu, agent_id, dossier_id, statut, auteur, date_document, date_creation, date_modification) FROM stdin;
1	DOC-2026-00001	DEMANDE	Demande de conge annuel	Je sollicite une autorisation de conge annuel.	1	\N	BROUILLON	Josue TSIVOERY	2026-09-22	2026-09-23 16:02:03.701672	2026-09-23 16:02:03.701672
2	DOC-2026-00002	DEMANDE	Demande de conge annuel modifiee	Je sollicite une autorisation de conge annuel pour une nouvelle periode.	1	\N	ARCHIVE	Josue TSIVOERY	2026-09-26	2026-09-26 14:49:07.825228	2026-09-26 15:09:29.268762
3	DOC-2026-00003	DEMANDE	Demande de congé annuel	Je souhaite obtenir un congé de 2 mois cette année	2	\N	ARCHIVE	TSIVOERY Josué	2026-09-25	2026-09-26 16:24:05.910443	2026-09-26 16:24:58.033419
\.


--
-- Data for Name: dossier_rh; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.dossier_rh (id, reference, objet, agent_id, statut, date_creation, date_modification) FROM stdin;
\.


--
-- Data for Name: echelle; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.echelle (id, cadre_id, code, libelle, description, date_debut_validite, date_fin_validite) FROM stdin;
1	1	A1	Echelle A1	Echelle A1 du cadre A de la Fonction publique malagasy.	1996-08-27	\N
\.


--
-- Data for Name: echelon; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.echelon (id, classe_id, code, libelle, ordre, description, reference_juridique, date_debut_validite, date_fin_validite) FROM stdin;
1	1	1	1er echelon	1	Premier echelon de la 2e classe.	Decret n 96-746 du 27 aout 1996, article 3	1996-08-27	\N
2	1	2	2e echelon	2	Deuxieme echelon de la 2e classe.	Decret n 96-746 du 27 aout 1996, article 3	1996-08-27	\N
3	1	3	3e echelon	3	Troisieme echelon de la 2e classe.	Decret n 96-746 du 27 aout 1996, article 3	1996-08-27	\N
4	2	1	1er echelon	1	Premier echelon de la 1re classe.	Decret n 96-746 du 27 aout 1996, article 3	1996-08-27	\N
5	2	2	2e echelon	2	Deuxieme echelon de la 1re classe.	Decret n 96-746 du 27 aout 1996, article 3	1996-08-27	\N
6	2	3	3e echelon	3	Troisieme echelon de la 1re classe.	Decret n 96-746 du 27 aout 1996, article 3	1996-08-27	\N
7	3	1	1er echelon	1	Premier echelon de la classe principale.	Decret n 96-746 du 27 aout 1996, article 3	1996-08-27	\N
8	3	2	2e echelon	2	Deuxieme echelon de la classe principale.	Decret n 96-746 du 27 aout 1996, article 3	1996-08-27	\N
9	3	3	3e echelon	3	Troisieme echelon de la classe principale.	Decret n 96-746 du 27 aout 1996, article 3	1996-08-27	\N
10	4	1	1er echelon	1	Premier echelon de la classe exceptionnelle.	Decret n 96-746 du 27 aout 1996, article 3	1996-08-27	\N
11	4	2	2e echelon	2	Deuxieme echelon de la classe exceptionnelle.	Decret n 96-746 du 27 aout 1996, article 3	1996-08-27	\N
\.


--
-- Data for Name: effet_regle_rh; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.effet_regle_rh (id, regle_id, code, libelle, type_effet, valeur, ordre, description) FROM stdin;
1	1	PASSAGE_ECHELON_SUIVANT	Passage a l echelon suivant	CHANGEMENT_ECHELON	ECHELON_SUIVANT	1	L agent passe a l echelon suivant de sa classe actuelle. Le nouvel indice est determine par la grille indiciaire correspondante.
\.


--
-- Data for Name: employe; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.employe (id, user_id, matricule, nom, prenom, sexe, adresse, cin, telephone, date_naissance, lieu_naissance, date_embauche, poste_id, service_id, type_emploi_id, categorie_id, grade_id, lieu_travail, photo) FROM stdin;
3	\N	MEF003	RASOLOARISON	Fara	F	Antananarivo	CIN-DEMO-003	0320000003	1990-01-18	Fianarantsoa	2016-02-15	27	8	1	5	2	Antananarivo	\N
4	\N	MEF004	RAKOTO	Hery	M	Antananarivo	CIN-DEMO-004	0320000004	1987-11-02	Toamasina	2013-09-12	10	3	1	4	3	Antananarivo	\N
5	\N	MEF005	RAZAFINDRAKOTO	Mialy	F	Antananarivo	CIN-DEMO-005	0320000005	1991-05-26	Mahajanga	2017-04-03	17	5	1	5	2	Antananarivo	\N
6	\N	MEF006	ANDRIAMAMPIANINA	Toky	M	Antananarivo	CIN-DEMO-006	0320000006	1983-09-14	Antananarivo	2010-01-11	1	1	1	4	3	Antananarivo	\N
7	\N	MEF007	RAVAOARISOA	Soa	F	Antananarivo	CIN-DEMO-007	0320000007	1992-12-08	Antsirabe	2019-08-19	14	4	2	6	1	Antananarivo	\N
8	\N	MEF008	RABEMANANJARA	Solofo	M	Antananarivo	CIN-DEMO-008	0320000008	1989-06-30	Fianarantsoa	2015-03-16	20	6	1	5	2	Antananarivo	\N
10	\N	TEST-NOTIF-002	RABARY	Marie	F	Antananarivo	\N	0340000001	1999-07-20	Antananarivo	2026-09-26	7	2	\N	\N	\N	Antananarivo	\N
13	\N	TEST-NOTIF-003	RABEA	Marie	F	\N	\N	\N	\N	\N	2026-09-26	7	2	\N	\N	\N	\N	\N
14	\N	MEF-DIR-001	RAKOTOMALALA	Hanta	F	\N	\N	\N	1980-06-15	\N	2008-01-02	32	2	\N	\N	\N	\N	\N
16	\N	MEF-CHEF-001	ANDRIANINA	Lova	F	\N	\N	\N	1984-04-18	\N	2012-02-06	5	2	\N	\N	\N	\N	\N
17	\N	MEF-CHEF-002	RAKOTOARISOA	Faly	M	\N	\N	\N	1982-09-11	\N	2010-07-19	22	7	\N	\N	\N	\N	\N
18	\N	MEF-CHEF-003	ANDRIANASOLO	Mamy	F	\N	\N	\N	1985-02-27	\N	2013-05-13	26	8	\N	\N	\N	\N	\N
19	\N	MEF-CHEF-004	RAZAFIARISON	Tiana	F	\N	\N	\N	1986-08-09	\N	2014-09-01	9	3	\N	\N	\N	\N	\N
20	\N	MEF-CHEF-005	RAKOTONDRINA	Voahirana	F	\N	\N	\N	1987-03-21	\N	2015-01-12	16	5	\N	\N	\N	\N	\N
22	\N	MEF-CHEF-007	RASOAMANANA	Aina	F	\N	\N	\N	1988-10-14	\N	2016-06-20	13	4	\N	\N	\N	\N	\N
23	\N	MEF-CHEF-008	RAKOTOZANDRY	Fetra	M	\N	\N	\N	1984-07-30	\N	2012-10-15	19	6	\N	\N	\N	\N	\N
15	\N	MEF-DIR-002	RAZAFINDRAIBE	Tahina	M	\N	\N	\N	1979-11-22	\N	2007-03-11	33	7	\N	\N	\N	\N	/uploads/employes/agent-15-91728ab5-9e34-47f0-8fc9-097af2d0da79.jpg
1	1	MEF001	RAZANASOAVINA	Josette	F	Antananarivo	CIN-DEMO-001	0343403434	1988-03-09	Antananarivo	2014-01-04	6	2	1	4	2	Antananarivo	/uploads/employes/agent-1-b4900763-3bb3-41e3-9a09-693fec8ef028.jpg
2	2	MEF002	RAKOTONDRABE	Andry	M	Antananarivo	CIN-DEMO-002	0320000002	1985-07-21	Antsirabe	2011-05-31	23	7	1	5	3	Antananarivo	/uploads/employes/agent-2-cc966c82-4b67-4841-ba27-a738ecc64ce9.jpg
21	3	MEF-CHEF-006	ANDRIAMBOLOLONA	Niry	M	\N	CIN-DEMO-123	0330003300	1983-12-03	\N	2011-11-05	1	1	\N	\N	\N	\N	/uploads/employes/agent-21-35cddcd0-226c-46da-953c-d0594bfd1a86.png
9	4	TEST-NOTIF-001	RANDRIA	Jean	M	Antananarivo	\N	0340000000	1998-05-15	Antananarivo	2026-09-26	7	2	\N	\N	\N	Antananarivo	\N
\.


--
-- Data for Name: employe_diplome; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.employe_diplome (id, employe_id, diplome_id, etablissement, date_obtention, numero_diplome, mention, principal, observation) FROM stdin;
\.


--
-- Data for Name: evaluation; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.evaluation (id, employe_id, annee, note, appreciation, evaluateur, date_evaluation, reference) FROM stdin;
\.


--
-- Data for Name: evenement_carriere; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.evenement_carriere (id, employe_id, type_evenement, date_evenement, date_effet, situation_avant_id, situation_apres_id, reference_acte, type_acte, motif, observation) FROM stdin;
1	1	AVANCEMENT_ECHELON	2022-01-01	2022-01-01	1	2	Donnee fictive de demonstration	DEMONSTRATION	Avancement d echelon apres anciennete requise.	Evenement fictif utilise pour tester le moteur d analyse de carriere.
2	1	AVANCEMENT_ECHELON	2024-01-01	2024-01-01	2	3	Donnee fictive de demonstration	DEMONSTRATION	Avancement d echelon apres anciennete requise.	Evenement fictif utilise pour tester le moteur d analyse de carriere.
\.


--
-- Data for Name: grade; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.grade (id, code_grade, type_emploi_id) FROM stdin;
1	GRADE-01	1
2	GRADE-02	1
3	GRADE-03	1
4	GRADE-04	1
5	GRADE-05	1
\.


--
-- Data for Name: grade_carriere; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.grade_carriere (id, corps_id, code, libelle, description, reference_juridique, date_debut_validite, date_fin_validite) FROM stdin;
1	1	CONCEPTEUR	Concepteur	Grade du corps des concepteurs structure selon les classes prevues par le statut particulier.	Decret n 96-746 du 27 aout 1996, article 3	1996-08-27	\N
\.


--
-- Data for Name: grille_indiciaire; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.grille_indiciaire (id, cadre_id, echelle_id, classe_id, echelon_id, indice, date_debut_validite, date_fin_validite, reference_juridique) FROM stdin;
1	1	1	1	1	1035	1996-08-27	\N	Decret n 96-746 du 27 aout 1996, article 3
2	1	1	1	2	1125	1996-08-27	\N	Decret n 96-746 du 27 aout 1996, article 3
3	1	1	1	3	1225	1996-08-27	\N	Decret n 96-746 du 27 aout 1996, article 3
4	1	1	2	4	1325	1996-08-27	\N	Decret n 96-746 du 27 aout 1996, article 3
5	1	1	2	5	1455	1996-08-27	\N	Decret n 96-746 du 27 aout 1996, article 3
6	1	1	2	6	1585	1996-08-27	\N	Decret n 96-746 du 27 aout 1996, article 3
7	1	1	3	7	1725	1996-08-27	\N	Decret n 96-746 du 27 aout 1996, article 3
8	1	1	3	8	1880	1996-08-27	\N	Decret n 96-746 du 27 aout 1996, article 3
9	1	1	3	9	2045	1996-08-27	\N	Decret n 96-746 du 27 aout 1996, article 3
10	1	1	4	10	2225	1996-08-27	\N	Decret n 96-746 du 27 aout 1996, article 3
11	1	1	4	11	2325	1996-08-27	\N	Decret n 96-746 du 27 aout 1996, article 3
\.


--
-- Data for Name: mouvement_administratif; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.mouvement_administratif (id, employe_id, type_mouvement_id, poste_avant_id, service_avant_id, poste_apres_id, service_apres_id, date_demande, date_decision, date_effet, reference_acte, type_acte, motif, observation) FROM stdin;
\.


--
-- Data for Name: mouvement_anciennete; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.mouvement_anciennete (id, employe_id, type_mouvement, duree_jours, date_effet, motif, reference_acte, observation) FROM stdin;
\.


--
-- Data for Name: notification; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.notification (id, user_id, titre, message, lu, date_creation) FROM stdin;
2	1	Bienvenue	Bienvenue dans l'Assistant RH du MEF.	t	2026-09-17 13:56:40.546624
3	1	Nouvel agent ajouté	Jean RANDRIA a été ajouté au système.	t	2026-09-26 22:21:03.783401
5	1	Nouvel agent ajouté	Marie RABEA a été ajouté au système.	t	2026-09-26 22:57:38.44419
4	1	Nouvel agent ajouté	Marie RABARY a été ajouté au système.	t	2026-09-26 22:27:18.509664
1	1	Nouvelle tƒche	Une nouvelle tƒche vous a ‚t‚ attribu‚e.	t	2026-09-17 13:56:40.546624
\.


--
-- Data for Name: parametre_regle_rh; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.parametre_regle_rh (id, code, libelle, type_valeur, unite, description, date_debut_validite, date_fin_validite) FROM stdin;
\.


--
-- Data for Name: poste; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.poste (id, intitule, description, service_id) FROM stdin;
1	Chef de service	Responsable de la coordination et du suivi du service.	1
2	Chef de bureau	Responsable d'un bureau administratif.	1
5	Chef de service	Responsable de la gestion administrative des effectifs.	2
6	Gestionnaire de dossiers	Gestion et suivi des dossiers administratifs des agents.	2
15	Agent administratif	Assure le traitement administratif des dossiers.	4
21	Agent administratif	Traitement administratif des dossiers.	6
24	Gestionnaire de dossiers	Gestion et suivi des dossiers de solde.	7
26	Chef de service	Responsable du traitement des dossiers de pensions.	8
27	Agent de liquidation	Traitement administratif des dossiers de liquidation.	8
28	Gestionnaire de dossiers	Gestion et suivi des dossiers de pensions.	8
3	Secrétaire	Assure les activités administratives et le secrétariat.	1
4	Agent administratif	Assure le traitement des opérations administratives.	1
7	Agent administratif	Traitement des opérations administratives liées aux effectifs.	2
8	Secrétaire	Assure les tâches de secrétariat du service.	2
9	Chef de service	Responsable des études et travaux du service.	3
10	Juriste	Participe aux études et analyses juridiques.	3
11	Chargé d'études	Réalise des études et analyses administratives.	3
12	Secrétaire	Assure les activités de secrétariat.	3
13	Chef de service	Responsable des activités du service.	4
14	Chargé d'études	Participe aux études relatives à la protection des agents.	4
16	Chef de cellule	Coordonne les activités de la cellule.	5
17	Chargé d'études	Réalise les études et analyses nécessaires au suivi.	5
18	Agent de suivi	Participe au suivi des activités et indicateurs.	5
19	Chef de service	Responsable des activités du service.	6
20	Gestionnaire de dossiers	Gestion et suivi des dossiers de prévoyance.	6
22	Chef de service	Responsable des opérations relatives à la solde.	7
23	Agent de solde	Traitement et suivi des opérations relatives à la solde.	7
25	Secrétaire	Assure les activités de secrétariat.	7
29	Secrétaire	Assure les activités de secrétariat.	8
31	informaticien	Employer qui se charge du numérisation, automatisation de tache, création d'appli, sécurité des réseau, etc..	7
32	Directeur	Responsable de la direction.	2
33	Directeur	Responsable de la direction.	7
\.


--
-- Data for Name: regle_population; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.regle_population (id, regle_id, type_population, statut_agent_id, cadre_id, echelle_id, corps_id, grade_carriere_id, observation) FROM stdin;
1	1	CORPS	1	1	1	1	1	Fonctionnaires du corps des concepteurs relevant du cadre A et de l echelle A1.
\.


--
-- Data for Name: regle_reference_juridique; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.regle_reference_juridique (id, regle_id, texte_juridique_id, article_juridique_id, role_reference, observation) FROM stdin;
1	1	1	4	FONDEMENT	Fondement juridique de la regle d avancement d echelon a deux ans d anciennete.
\.


--
-- Data for Name: regle_rh; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.regle_rh (id, type_regle_id, code, libelle, description, population_concernee, reference_juridique, article, date_debut_validite, date_fin_validite, priorite, active) FROM stdin;
1	1	AVANCEMENT_ECHELON_CONCEPTEUR_2ANS	Avancement d echelon des concepteurs	Avancement d echelon dans une meme classe apres deux ans d anciennete.	Fonctionnaires du corps des concepteurs	Decret n 96-746 du 27 aout 1996	Article 9	1996-08-27	\N	1	t
\.


--
-- Data for Name: retraite; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.retraite (id, employe_id, date_demande, date_admission, date_effet, type_retraite, age_au_depart, reference_acte, type_acte, observation) FROM stdin;
\.


--
-- Data for Name: service; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.service (id, code, nom, description, direction_id) FROM stdin;
2	SGEAE	SERVICE DE LA GESTION DES EFFECTIFS DES AGENTS DE L'ETAT	Gestion administrative des effectifs des agents de l'Etat.	4
8	SL	SERVICE DE LA LIQUIDATION DES PENSIONS	Traitement et liquidation des dossiers de pensions.	5
1	SCSD	SERVICE DE LA COORDINATION DES SERVICES DECONCENTRES	Coordination et suivi des services déconcentrés.	\N
3	SLE	SERVICE DE LA LEGISLATION ET DES ETUDES	Etudes et travaux relatifs aux questions législatives et administratives.	\N
5	CESE	CELLULE D'ETUDES, DE SUIVI ET D'EVALUATION	Etudes, suivi et évaluation des activités.	\N
6	SCPAE	SERVICE CENTRAL DE LA PREVOYANCE DES AGENTS DE L'ETAT	Gestion et suivi des questions de prévoyance des agents de l'Etat.	\N
7	SCS	SERVICE CENTRAL DE LA SOLDE	Gestion et traitement des opérations relatives à la solde.	5
4	SPPAE	SERVICE DE LA POLITIQUE DE PROTECTION DES AGENTS DE L'ETAT	Suivi des questions relatives à la protection des agents de l'Etat.	\N
\.


--
-- Data for Name: situation_administrative; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.situation_administrative (id, employe_id, code, libelle, date_debut, date_fin, reference_acte, motif, observation) FROM stdin;
\.


--
-- Data for Name: situation_carriere; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.situation_carriere (id, employe_id, date_debut, date_fin, statut_agent_id, cadre_id, echelle_id, corps_id, grade_carriere_id, classe_id, echelon_id, indice, origine, reference_acte, date_acte, observation) FROM stdin;
1	1	2020-01-01	2022-01-01	1	1	1	1	1	1	1	1035	DEMONSTRATION	Donnee fictive de demonstration	2020-01-01	Situation fictive utilisee pour tester le moteur d analyse de carriere.
2	1	2022-01-01	2024-01-01	1	1	1	1	1	1	2	1125	DEMONSTRATION	Donnee fictive de demonstration	2022-01-01	Situation fictive utilisee pour tester le moteur d analyse de carriere.
3	1	2024-01-01	\N	1	1	1	1	1	1	3	1225	DEMONSTRATION	Donnee fictive de demonstration	2024-01-01	Situation fictive utilisee pour tester le moteur d analyse de carriere.
\.


--
-- Data for Name: statut_agent; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.statut_agent (id, code, libelle, description, date_debut_validite, date_fin_validite) FROM stdin;
1	FONCTIONNAIRE	Fonctionnaire	Agent soumis au Statut Général des Fonctionnaires.	2003-09-03	\N
2	AGENT_NON_ENCADRE	Agent non encadré de l'État	Agent relevant du régime des agents non encadrés de l'État.	1994-11-17	\N
\.


--
-- Data for Name: tache; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.tache (id, user_id, employe_id, titre, description, statut, priorite, date_creation, date_echeance) FROM stdin;
1	1	1	VÃ©rifier le dossier de l'agent	VÃ©rifier les informations administratives du dossier.	A_FAIRE	HAUTE	2026-09-17 13:56:40.546624	2026-09-20 13:56:40.546624
2	1	2	ContrÃ´ler les informations de carriÃ¨re	VÃ©rifier les informations relatives Ã  la carriÃ¨re de l'agent.	EN_COURS	NORMALE	2026-09-17 13:56:40.546624	2026-09-24 13:56:40.546624
3	1	\N	PrÃ©parer un Ã©tat des effectifs	PrÃ©parer un Ã©tat rÃ©capitulatif des effectifs par service.	A_FAIRE	NORMALE	2026-09-17 13:56:40.546624	2026-09-27 13:56:40.546624
\.


--
-- Data for Name: texte_juridique; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.texte_juridique (id, type_texte, numero, titre, date_texte, autorite, objet, reference_publication, fichier_path, url_source, date_debut_validite, date_fin_validite, observation) FROM stdin;
1	DECRET	96-746	Decret portant creation d un corps de concepteurs et fixant le statut particulier de ce corps de fonctionnaires	1996-08-27	Premier Ministre, Chef du Gouvernement	Creation du corps des concepteurs et fixation de son statut particulier.	Decret n 96-746 du 27 aout 1996	\N	https://www.mef.gov.mg/dgcf/textes-pdf/FONCTION%20PUBLIQUE/TEXTE%20STATUT/unpan004632%281%29.pdf	1996-08-27	\N	Source officielle publiee par le Ministere de l Economie et des Finances.
\.


--
-- Data for Name: type_cessation_service; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.type_cessation_service (id, code, libelle, description, date_debut_validite, date_fin_validite) FROM stdin;
\.


--
-- Data for Name: type_document_administratif; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.type_document_administratif (id, code, libelle, description, date_debut_validite, date_fin_validite) FROM stdin;
\.


--
-- Data for Name: type_emploi; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.type_emploi (id, nom) FROM stdin;
1	Fonctionnaire
2	Agent contractuel
3	Agent temporaire
4	Personnel d'appui
\.


--
-- Data for Name: type_mouvement_administratif; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.type_mouvement_administratif (id, code, libelle, description, date_debut_validite, date_fin_validite) FROM stdin;
\.


--
-- Data for Name: type_regle_rh; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.type_regle_rh (id, code, libelle, description, date_debut_validite, date_fin_validite) FROM stdin;
1	AVANCEMENT	Avancement	Regles relatives a la progression de la carriere.	\N	\N
2	RECLASSEMENT	Reclassement	Regles relatives au changement de position de carriere.	\N	\N
3	INTEGRATION	Integration	Regles relatives a l integration dans un statut ou un corps.	\N	\N
4	REMUNERATION	Remuneration	Regles relatives aux indices et elements de remuneration.	\N	\N
5	POSITION_ADMINISTRATIVE	Position administrative	Regles relatives a la situation administrative de l agent.	\N	\N
\.


--
-- Data for Name: utilisateur; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.utilisateur (id, email, password_hash, role, statut_compte) FROM stdin;
1	rh.demo@mef.local	$2a$10$QEncp1s47f332/QHZBc5CudKioswQViPGfcJFKfkduEXEhrPpM3G.	SPERS_AGENT	ACTIF
2	test.mef@mef.local	$2a$10$W2T3E3x3gGQatdzu584J1OaG15mjHubjPzsWHpcwBfIle6Oq5bdv.	SPERS_AGENT	ACTIF
3	blabla@gmail.com	$2a$10$hBWUfomDz134VxE1qSDr/.gEY9EHBaBxmZ8W1zEDQoNkfzT8QBvI6	SPERS_AGENT	ACTIF
4	test.notification@mef.local	$2a$10$0fkpd8AUTXpmx99ze6ErVeqAdfBPFAHV5oefn3nvEGI8UUiocMBjC	SPERS_AGENT	EN_ATTENTE
\.


--
-- Data for Name: valeur_parametre_regle; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.valeur_parametre_regle (id, parametre_id, valeur, date_debut_validite, date_fin_validite, reference_juridique, observation) FROM stdin;
\.


--
-- Name: activite_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.activite_id_seq', 1, true);


--
-- Name: affectation_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.affectation_id_seq', 1, false);


--
-- Name: article_juridique_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.article_juridique_id_seq', 4, true);


--
-- Name: avancement_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.avancement_id_seq', 1, false);


--
-- Name: cadre_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.cadre_id_seq', 4, true);


--
-- Name: categorie_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.categorie_id_seq', 10, true);


--
-- Name: cessation_service_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.cessation_service_id_seq', 1, false);


--
-- Name: classe_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.classe_id_seq', 4, true);


--
-- Name: condition_regle_rh_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.condition_regle_rh_id_seq', 1, true);


--
-- Name: corps_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.corps_id_seq', 1, true);


--
-- Name: diplome_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.diplome_id_seq', 1, false);


--
-- Name: direction_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.direction_id_seq', 7, true);


--
-- Name: document_administratif_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.document_administratif_id_seq', 1, false);


--
-- Name: document_evenement_carriere_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.document_evenement_carriere_id_seq', 1, false);


--
-- Name: document_mouvement_administratif_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.document_mouvement_administratif_id_seq', 1, false);


--
-- Name: document_rh_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.document_rh_id_seq', 3, true);


--
-- Name: dossier_rh_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.dossier_rh_id_seq', 1, false);


--
-- Name: echelle_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.echelle_id_seq', 1, true);


--
-- Name: echelon_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.echelon_id_seq', 11, true);


--
-- Name: effet_regle_rh_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.effet_regle_rh_id_seq', 1, true);


--
-- Name: employe_diplome_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.employe_diplome_id_seq', 1, false);


--
-- Name: employe_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.employe_id_seq', 23, true);


--
-- Name: evaluation_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.evaluation_id_seq', 1, false);


--
-- Name: evenement_carriere_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.evenement_carriere_id_seq', 2, true);


--
-- Name: grade_carriere_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.grade_carriere_id_seq', 1, true);


--
-- Name: grade_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.grade_id_seq', 7, true);


--
-- Name: grille_indiciaire_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.grille_indiciaire_id_seq', 11, true);


--
-- Name: mouvement_administratif_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.mouvement_administratif_id_seq', 1, false);


--
-- Name: mouvement_anciennete_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.mouvement_anciennete_id_seq', 1, false);


--
-- Name: notification_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.notification_id_seq', 5, true);


--
-- Name: parametre_regle_rh_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.parametre_regle_rh_id_seq', 1, false);


--
-- Name: poste_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.poste_id_seq', 33, true);


--
-- Name: regle_population_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.regle_population_id_seq', 1, true);


--
-- Name: regle_reference_juridique_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.regle_reference_juridique_id_seq', 1, true);


--
-- Name: regle_rh_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.regle_rh_id_seq', 3, true);


--
-- Name: retraite_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.retraite_id_seq', 1, false);


--
-- Name: service_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.service_id_seq', 10, true);


--
-- Name: situation_administrative_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.situation_administrative_id_seq', 1, false);


--
-- Name: situation_carriere_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.situation_carriere_id_seq', 3, true);


--
-- Name: statut_agent_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.statut_agent_id_seq', 4, true);


--
-- Name: tache_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.tache_id_seq', 3, true);


--
-- Name: texte_juridique_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.texte_juridique_id_seq', 1, true);


--
-- Name: type_cessation_service_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.type_cessation_service_id_seq', 1, false);


--
-- Name: type_document_administratif_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.type_document_administratif_id_seq', 1, false);


--
-- Name: type_emploi_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.type_emploi_id_seq', 4, true);


--
-- Name: type_mouvement_administratif_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.type_mouvement_administratif_id_seq', 1, false);


--
-- Name: type_regle_rh_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.type_regle_rh_id_seq', 5, true);


--
-- Name: utilisateur_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.utilisateur_id_seq', 4, true);


--
-- Name: valeur_parametre_regle_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.valeur_parametre_regle_id_seq', 1, false);


--
-- Name: activite activite_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.activite
    ADD CONSTRAINT activite_pkey PRIMARY KEY (id);


--
-- Name: affectation affectation_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.affectation
    ADD CONSTRAINT affectation_pkey PRIMARY KEY (id);


--
-- Name: article_juridique article_juridique_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.article_juridique
    ADD CONSTRAINT article_juridique_pkey PRIMARY KEY (id);


--
-- Name: avancement avancement_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.avancement
    ADD CONSTRAINT avancement_pkey PRIMARY KEY (id);


--
-- Name: cadre cadre_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.cadre
    ADD CONSTRAINT cadre_code_key UNIQUE (code);


--
-- Name: cadre cadre_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.cadre
    ADD CONSTRAINT cadre_pkey PRIMARY KEY (id);


--
-- Name: categorie categorie_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.categorie
    ADD CONSTRAINT categorie_code_key UNIQUE (code);


--
-- Name: categorie categorie_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.categorie
    ADD CONSTRAINT categorie_pkey PRIMARY KEY (id);


--
-- Name: cessation_service cessation_service_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.cessation_service
    ADD CONSTRAINT cessation_service_pkey PRIMARY KEY (id);


--
-- Name: classe classe_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.classe
    ADD CONSTRAINT classe_pkey PRIMARY KEY (id);


--
-- Name: condition_regle_rh condition_regle_rh_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.condition_regle_rh
    ADD CONSTRAINT condition_regle_rh_pkey PRIMARY KEY (id);


--
-- Name: corps corps_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.corps
    ADD CONSTRAINT corps_pkey PRIMARY KEY (id);


--
-- Name: diplome diplome_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.diplome
    ADD CONSTRAINT diplome_code_key UNIQUE (code);


--
-- Name: diplome diplome_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.diplome
    ADD CONSTRAINT diplome_pkey PRIMARY KEY (id);


--
-- Name: direction direction_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.direction
    ADD CONSTRAINT direction_pkey PRIMARY KEY (id);


--
-- Name: document_administratif document_administratif_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.document_administratif
    ADD CONSTRAINT document_administratif_pkey PRIMARY KEY (id);


--
-- Name: document_evenement_carriere document_evenement_carriere_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.document_evenement_carriere
    ADD CONSTRAINT document_evenement_carriere_pkey PRIMARY KEY (id);


--
-- Name: document_mouvement_administratif document_mouvement_administratif_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.document_mouvement_administratif
    ADD CONSTRAINT document_mouvement_administratif_pkey PRIMARY KEY (id);


--
-- Name: document_rh document_rh_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.document_rh
    ADD CONSTRAINT document_rh_pkey PRIMARY KEY (id);


--
-- Name: document_rh document_rh_reference_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.document_rh
    ADD CONSTRAINT document_rh_reference_key UNIQUE (reference);


--
-- Name: dossier_rh dossier_rh_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.dossier_rh
    ADD CONSTRAINT dossier_rh_pkey PRIMARY KEY (id);


--
-- Name: dossier_rh dossier_rh_reference_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.dossier_rh
    ADD CONSTRAINT dossier_rh_reference_key UNIQUE (reference);


--
-- Name: echelle echelle_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.echelle
    ADD CONSTRAINT echelle_pkey PRIMARY KEY (id);


--
-- Name: echelon echelon_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.echelon
    ADD CONSTRAINT echelon_pkey PRIMARY KEY (id);


--
-- Name: effet_regle_rh effet_regle_rh_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.effet_regle_rh
    ADD CONSTRAINT effet_regle_rh_pkey PRIMARY KEY (id);


--
-- Name: employe employe_cin_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employe
    ADD CONSTRAINT employe_cin_key UNIQUE (cin);


--
-- Name: employe_diplome employe_diplome_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employe_diplome
    ADD CONSTRAINT employe_diplome_pkey PRIMARY KEY (id);


--
-- Name: employe employe_matricule_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employe
    ADD CONSTRAINT employe_matricule_key UNIQUE (matricule);


--
-- Name: employe employe_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employe
    ADD CONSTRAINT employe_pkey PRIMARY KEY (id);


--
-- Name: employe employe_user_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employe
    ADD CONSTRAINT employe_user_id_key UNIQUE (user_id);


--
-- Name: evaluation evaluation_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.evaluation
    ADD CONSTRAINT evaluation_pkey PRIMARY KEY (id);


--
-- Name: evenement_carriere evenement_carriere_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.evenement_carriere
    ADD CONSTRAINT evenement_carriere_pkey PRIMARY KEY (id);


--
-- Name: grade_carriere grade_carriere_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.grade_carriere
    ADD CONSTRAINT grade_carriere_pkey PRIMARY KEY (id);


--
-- Name: grade grade_code_grade_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.grade
    ADD CONSTRAINT grade_code_grade_key UNIQUE (code_grade);


--
-- Name: grade grade_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.grade
    ADD CONSTRAINT grade_pkey PRIMARY KEY (id);


--
-- Name: grille_indiciaire grille_indiciaire_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.grille_indiciaire
    ADD CONSTRAINT grille_indiciaire_pkey PRIMARY KEY (id);


--
-- Name: mouvement_administratif mouvement_administratif_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.mouvement_administratif
    ADD CONSTRAINT mouvement_administratif_pkey PRIMARY KEY (id);


--
-- Name: mouvement_anciennete mouvement_anciennete_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.mouvement_anciennete
    ADD CONSTRAINT mouvement_anciennete_pkey PRIMARY KEY (id);


--
-- Name: notification notification_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);


--
-- Name: parametre_regle_rh parametre_regle_rh_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.parametre_regle_rh
    ADD CONSTRAINT parametre_regle_rh_code_key UNIQUE (code);


--
-- Name: parametre_regle_rh parametre_regle_rh_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.parametre_regle_rh
    ADD CONSTRAINT parametre_regle_rh_pkey PRIMARY KEY (id);


--
-- Name: poste poste_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.poste
    ADD CONSTRAINT poste_pkey PRIMARY KEY (id);


--
-- Name: regle_population regle_population_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.regle_population
    ADD CONSTRAINT regle_population_pkey PRIMARY KEY (id);


--
-- Name: regle_reference_juridique regle_reference_juridique_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.regle_reference_juridique
    ADD CONSTRAINT regle_reference_juridique_pkey PRIMARY KEY (id);


--
-- Name: regle_rh regle_rh_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.regle_rh
    ADD CONSTRAINT regle_rh_code_key UNIQUE (code);


--
-- Name: regle_rh regle_rh_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.regle_rh
    ADD CONSTRAINT regle_rh_pkey PRIMARY KEY (id);


--
-- Name: retraite retraite_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.retraite
    ADD CONSTRAINT retraite_pkey PRIMARY KEY (id);


--
-- Name: service service_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.service
    ADD CONSTRAINT service_code_key UNIQUE (code);


--
-- Name: service service_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.service
    ADD CONSTRAINT service_pkey PRIMARY KEY (id);


--
-- Name: situation_administrative situation_administrative_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.situation_administrative
    ADD CONSTRAINT situation_administrative_pkey PRIMARY KEY (id);


--
-- Name: situation_carriere situation_carriere_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.situation_carriere
    ADD CONSTRAINT situation_carriere_pkey PRIMARY KEY (id);


--
-- Name: statut_agent statut_agent_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.statut_agent
    ADD CONSTRAINT statut_agent_code_key UNIQUE (code);


--
-- Name: statut_agent statut_agent_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.statut_agent
    ADD CONSTRAINT statut_agent_pkey PRIMARY KEY (id);


--
-- Name: tache tache_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.tache
    ADD CONSTRAINT tache_pkey PRIMARY KEY (id);


--
-- Name: texte_juridique texte_juridique_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.texte_juridique
    ADD CONSTRAINT texte_juridique_pkey PRIMARY KEY (id);


--
-- Name: type_cessation_service type_cessation_service_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.type_cessation_service
    ADD CONSTRAINT type_cessation_service_code_key UNIQUE (code);


--
-- Name: type_cessation_service type_cessation_service_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.type_cessation_service
    ADD CONSTRAINT type_cessation_service_pkey PRIMARY KEY (id);


--
-- Name: type_document_administratif type_document_administratif_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.type_document_administratif
    ADD CONSTRAINT type_document_administratif_code_key UNIQUE (code);


--
-- Name: type_document_administratif type_document_administratif_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.type_document_administratif
    ADD CONSTRAINT type_document_administratif_pkey PRIMARY KEY (id);


--
-- Name: type_emploi type_emploi_nom_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.type_emploi
    ADD CONSTRAINT type_emploi_nom_key UNIQUE (nom);


--
-- Name: type_emploi type_emploi_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.type_emploi
    ADD CONSTRAINT type_emploi_pkey PRIMARY KEY (id);


--
-- Name: type_mouvement_administratif type_mouvement_administratif_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.type_mouvement_administratif
    ADD CONSTRAINT type_mouvement_administratif_code_key UNIQUE (code);


--
-- Name: type_mouvement_administratif type_mouvement_administratif_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.type_mouvement_administratif
    ADD CONSTRAINT type_mouvement_administratif_pkey PRIMARY KEY (id);


--
-- Name: type_regle_rh type_regle_rh_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.type_regle_rh
    ADD CONSTRAINT type_regle_rh_code_key UNIQUE (code);


--
-- Name: type_regle_rh type_regle_rh_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.type_regle_rh
    ADD CONSTRAINT type_regle_rh_pkey PRIMARY KEY (id);


--
-- Name: article_juridique uq_article_texte_numero; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.article_juridique
    ADD CONSTRAINT uq_article_texte_numero UNIQUE (texte_juridique_id, numero_article);


--
-- Name: classe uq_classe_grade_code; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.classe
    ADD CONSTRAINT uq_classe_grade_code UNIQUE (grade_carriere_id, code);


--
-- Name: classe uq_classe_grade_ordre; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.classe
    ADD CONSTRAINT uq_classe_grade_ordre UNIQUE (grade_carriere_id, ordre);


--
-- Name: condition_regle_rh uq_condition_regle_code; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.condition_regle_rh
    ADD CONSTRAINT uq_condition_regle_code UNIQUE (regle_id, code);


--
-- Name: condition_regle_rh uq_condition_regle_ordre; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.condition_regle_rh
    ADD CONSTRAINT uq_condition_regle_ordre UNIQUE (regle_id, ordre);


--
-- Name: corps uq_corps_echelle_code; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.corps
    ADD CONSTRAINT uq_corps_echelle_code UNIQUE (echelle_id, code);


--
-- Name: document_evenement_carriere uq_document_evenement; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.document_evenement_carriere
    ADD CONSTRAINT uq_document_evenement UNIQUE (document_id, evenement_carriere_id);


--
-- Name: document_mouvement_administratif uq_document_mouvement; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.document_mouvement_administratif
    ADD CONSTRAINT uq_document_mouvement UNIQUE (document_id, mouvement_administratif_id);


--
-- Name: echelle uq_echelle_cadre_code; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.echelle
    ADD CONSTRAINT uq_echelle_cadre_code UNIQUE (cadre_id, code);


--
-- Name: echelon uq_echelon_classe_code; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.echelon
    ADD CONSTRAINT uq_echelon_classe_code UNIQUE (classe_id, code);


--
-- Name: echelon uq_echelon_classe_ordre; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.echelon
    ADD CONSTRAINT uq_echelon_classe_ordre UNIQUE (classe_id, ordre);


--
-- Name: effet_regle_rh uq_effet_regle_code; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.effet_regle_rh
    ADD CONSTRAINT uq_effet_regle_code UNIQUE (regle_id, code);


--
-- Name: effet_regle_rh uq_effet_regle_ordre; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.effet_regle_rh
    ADD CONSTRAINT uq_effet_regle_ordre UNIQUE (regle_id, ordre);


--
-- Name: employe_diplome uq_employe_diplome; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employe_diplome
    ADD CONSTRAINT uq_employe_diplome UNIQUE (employe_id, diplome_id, date_obtention);


--
-- Name: evaluation uq_evaluation_employe_annee; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.evaluation
    ADD CONSTRAINT uq_evaluation_employe_annee UNIQUE (employe_id, annee);


--
-- Name: grade_carriere uq_grade_carriere_corps_code; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.grade_carriere
    ADD CONSTRAINT uq_grade_carriere_corps_code UNIQUE (corps_id, code);


--
-- Name: grille_indiciaire uq_grille_echelon_date; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.grille_indiciaire
    ADD CONSTRAINT uq_grille_echelon_date UNIQUE (echelon_id, date_debut_validite);


--
-- Name: regle_reference_juridique uq_regle_reference; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.regle_reference_juridique
    ADD CONSTRAINT uq_regle_reference UNIQUE (regle_id, texte_juridique_id, article_juridique_id);


--
-- Name: utilisateur utilisateur_email_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.utilisateur
    ADD CONSTRAINT utilisateur_email_key UNIQUE (email);


--
-- Name: utilisateur utilisateur_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.utilisateur
    ADD CONSTRAINT utilisateur_pkey PRIMARY KEY (id);


--
-- Name: valeur_parametre_regle valeur_parametre_regle_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.valeur_parametre_regle
    ADD CONSTRAINT valeur_parametre_regle_pkey PRIMARY KEY (id);


--
-- Name: idx_document_rh_agent; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_document_rh_agent ON public.document_rh USING btree (agent_id);


--
-- Name: idx_document_rh_dossier; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_document_rh_dossier ON public.document_rh USING btree (dossier_id);


--
-- Name: idx_document_rh_statut; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_document_rh_statut ON public.document_rh USING btree (statut);


--
-- Name: idx_document_rh_type; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_document_rh_type ON public.document_rh USING btree (type);


--
-- Name: idx_employe_nom; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_employe_nom ON public.employe USING btree (nom);


--
-- Name: idx_employe_poste; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_employe_poste ON public.employe USING btree (poste_id);


--
-- Name: idx_employe_prenom; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_employe_prenom ON public.employe USING btree (prenom);


--
-- Name: idx_employe_service; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_employe_service ON public.employe USING btree (service_id);


--
-- Name: idx_notification_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_notification_user ON public.notification USING btree (user_id);


--
-- Name: idx_poste_service; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_poste_service ON public.poste USING btree (service_id);


--
-- Name: idx_service_direction; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_service_direction ON public.service USING btree (direction_id);


--
-- Name: idx_tache_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_tache_user ON public.tache USING btree (user_id);


--
-- Name: document_rh document_rh_agent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.document_rh
    ADD CONSTRAINT document_rh_agent_id_fkey FOREIGN KEY (agent_id) REFERENCES public.employe(id);


--
-- Name: document_rh document_rh_dossier_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.document_rh
    ADD CONSTRAINT document_rh_dossier_id_fkey FOREIGN KEY (dossier_id) REFERENCES public.dossier_rh(id);


--
-- Name: dossier_rh dossier_rh_agent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.dossier_rh
    ADD CONSTRAINT dossier_rh_agent_id_fkey FOREIGN KEY (agent_id) REFERENCES public.employe(id);


--
-- Name: activite fk_activite_acteur; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.activite
    ADD CONSTRAINT fk_activite_acteur FOREIGN KEY (acteur_id) REFERENCES public.utilisateur(id);


--
-- Name: activite fk_activite_employe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.activite
    ADD CONSTRAINT fk_activite_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: affectation fk_affectation_employe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.affectation
    ADD CONSTRAINT fk_affectation_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: affectation fk_affectation_poste; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.affectation
    ADD CONSTRAINT fk_affectation_poste FOREIGN KEY (poste_id) REFERENCES public.poste(id);


--
-- Name: affectation fk_affectation_service; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.affectation
    ADD CONSTRAINT fk_affectation_service FOREIGN KEY (service_id) REFERENCES public.service(id);


--
-- Name: article_juridique fk_article_texte; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.article_juridique
    ADD CONSTRAINT fk_article_texte FOREIGN KEY (texte_juridique_id) REFERENCES public.texte_juridique(id);


--
-- Name: avancement fk_avancement_employe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.avancement
    ADD CONSTRAINT fk_avancement_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: avancement fk_avancement_situation_apres; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.avancement
    ADD CONSTRAINT fk_avancement_situation_apres FOREIGN KEY (situation_apres_id) REFERENCES public.situation_carriere(id);


--
-- Name: avancement fk_avancement_situation_avant; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.avancement
    ADD CONSTRAINT fk_avancement_situation_avant FOREIGN KEY (situation_avant_id) REFERENCES public.situation_carriere(id);


--
-- Name: cessation_service fk_cessation_employe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.cessation_service
    ADD CONSTRAINT fk_cessation_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: cessation_service fk_cessation_type; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.cessation_service
    ADD CONSTRAINT fk_cessation_type FOREIGN KEY (type_cessation_id) REFERENCES public.type_cessation_service(id);


--
-- Name: classe fk_classe_grade_carriere; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.classe
    ADD CONSTRAINT fk_classe_grade_carriere FOREIGN KEY (grade_carriere_id) REFERENCES public.grade_carriere(id);


--
-- Name: condition_regle_rh fk_condition_regle; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.condition_regle_rh
    ADD CONSTRAINT fk_condition_regle FOREIGN KEY (regle_id) REFERENCES public.regle_rh(id);


--
-- Name: corps fk_corps_echelle; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.corps
    ADD CONSTRAINT fk_corps_echelle FOREIGN KEY (echelle_id) REFERENCES public.echelle(id);


--
-- Name: document_administratif fk_document_employe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.document_administratif
    ADD CONSTRAINT fk_document_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: document_evenement_carriere fk_document_evenement_carriere; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.document_evenement_carriere
    ADD CONSTRAINT fk_document_evenement_carriere FOREIGN KEY (evenement_carriere_id) REFERENCES public.evenement_carriere(id);


--
-- Name: document_evenement_carriere fk_document_evenement_document; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.document_evenement_carriere
    ADD CONSTRAINT fk_document_evenement_document FOREIGN KEY (document_id) REFERENCES public.document_administratif(id);


--
-- Name: document_mouvement_administratif fk_document_mouvement_admin; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.document_mouvement_administratif
    ADD CONSTRAINT fk_document_mouvement_admin FOREIGN KEY (mouvement_administratif_id) REFERENCES public.mouvement_administratif(id);


--
-- Name: document_mouvement_administratif fk_document_mouvement_document; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.document_mouvement_administratif
    ADD CONSTRAINT fk_document_mouvement_document FOREIGN KEY (document_id) REFERENCES public.document_administratif(id);


--
-- Name: document_administratif fk_document_type; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.document_administratif
    ADD CONSTRAINT fk_document_type FOREIGN KEY (type_document_id) REFERENCES public.type_document_administratif(id);


--
-- Name: echelle fk_echelle_cadre; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.echelle
    ADD CONSTRAINT fk_echelle_cadre FOREIGN KEY (cadre_id) REFERENCES public.cadre(id);


--
-- Name: echelon fk_echelon_classe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.echelon
    ADD CONSTRAINT fk_echelon_classe FOREIGN KEY (classe_id) REFERENCES public.classe(id);


--
-- Name: effet_regle_rh fk_effet_regle; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.effet_regle_rh
    ADD CONSTRAINT fk_effet_regle FOREIGN KEY (regle_id) REFERENCES public.regle_rh(id);


--
-- Name: employe fk_employe_categorie; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employe
    ADD CONSTRAINT fk_employe_categorie FOREIGN KEY (categorie_id) REFERENCES public.categorie(id);


--
-- Name: employe_diplome fk_employe_diplome_diplome; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employe_diplome
    ADD CONSTRAINT fk_employe_diplome_diplome FOREIGN KEY (diplome_id) REFERENCES public.diplome(id);


--
-- Name: employe_diplome fk_employe_diplome_employe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employe_diplome
    ADD CONSTRAINT fk_employe_diplome_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: employe fk_employe_grade; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employe
    ADD CONSTRAINT fk_employe_grade FOREIGN KEY (grade_id) REFERENCES public.grade(id);


--
-- Name: employe fk_employe_poste; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employe
    ADD CONSTRAINT fk_employe_poste FOREIGN KEY (poste_id) REFERENCES public.poste(id);


--
-- Name: employe fk_employe_service; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employe
    ADD CONSTRAINT fk_employe_service FOREIGN KEY (service_id) REFERENCES public.service(id);


--
-- Name: employe fk_employe_type_emploi; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employe
    ADD CONSTRAINT fk_employe_type_emploi FOREIGN KEY (type_emploi_id) REFERENCES public.type_emploi(id);


--
-- Name: employe fk_employe_utilisateur; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employe
    ADD CONSTRAINT fk_employe_utilisateur FOREIGN KEY (user_id) REFERENCES public.utilisateur(id);


--
-- Name: evaluation fk_evaluation_employe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.evaluation
    ADD CONSTRAINT fk_evaluation_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: evenement_carriere fk_evenement_employe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.evenement_carriere
    ADD CONSTRAINT fk_evenement_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: evenement_carriere fk_evenement_situation_apres; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.evenement_carriere
    ADD CONSTRAINT fk_evenement_situation_apres FOREIGN KEY (situation_apres_id) REFERENCES public.situation_carriere(id);


--
-- Name: evenement_carriere fk_evenement_situation_avant; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.evenement_carriere
    ADD CONSTRAINT fk_evenement_situation_avant FOREIGN KEY (situation_avant_id) REFERENCES public.situation_carriere(id);


--
-- Name: grade_carriere fk_grade_carriere_corps; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.grade_carriere
    ADD CONSTRAINT fk_grade_carriere_corps FOREIGN KEY (corps_id) REFERENCES public.corps(id);


--
-- Name: grade fk_grade_type_emploi; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.grade
    ADD CONSTRAINT fk_grade_type_emploi FOREIGN KEY (type_emploi_id) REFERENCES public.type_emploi(id);


--
-- Name: grille_indiciaire fk_grille_cadre; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.grille_indiciaire
    ADD CONSTRAINT fk_grille_cadre FOREIGN KEY (cadre_id) REFERENCES public.cadre(id);


--
-- Name: grille_indiciaire fk_grille_classe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.grille_indiciaire
    ADD CONSTRAINT fk_grille_classe FOREIGN KEY (classe_id) REFERENCES public.classe(id);


--
-- Name: grille_indiciaire fk_grille_echelle; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.grille_indiciaire
    ADD CONSTRAINT fk_grille_echelle FOREIGN KEY (echelle_id) REFERENCES public.echelle(id);


--
-- Name: grille_indiciaire fk_grille_echelon; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.grille_indiciaire
    ADD CONSTRAINT fk_grille_echelon FOREIGN KEY (echelon_id) REFERENCES public.echelon(id);


--
-- Name: mouvement_administratif fk_mouvement_admin_employe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.mouvement_administratif
    ADD CONSTRAINT fk_mouvement_admin_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: mouvement_administratif fk_mouvement_admin_poste_apres; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.mouvement_administratif
    ADD CONSTRAINT fk_mouvement_admin_poste_apres FOREIGN KEY (poste_apres_id) REFERENCES public.poste(id);


--
-- Name: mouvement_administratif fk_mouvement_admin_poste_avant; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.mouvement_administratif
    ADD CONSTRAINT fk_mouvement_admin_poste_avant FOREIGN KEY (poste_avant_id) REFERENCES public.poste(id);


--
-- Name: mouvement_administratif fk_mouvement_admin_service_apres; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.mouvement_administratif
    ADD CONSTRAINT fk_mouvement_admin_service_apres FOREIGN KEY (service_apres_id) REFERENCES public.service(id);


--
-- Name: mouvement_administratif fk_mouvement_admin_service_avant; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.mouvement_administratif
    ADD CONSTRAINT fk_mouvement_admin_service_avant FOREIGN KEY (service_avant_id) REFERENCES public.service(id);


--
-- Name: mouvement_administratif fk_mouvement_admin_type; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.mouvement_administratif
    ADD CONSTRAINT fk_mouvement_admin_type FOREIGN KEY (type_mouvement_id) REFERENCES public.type_mouvement_administratif(id);


--
-- Name: mouvement_anciennete fk_mouvement_anciennete_employe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.mouvement_anciennete
    ADD CONSTRAINT fk_mouvement_anciennete_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: notification fk_notification_utilisateur; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT fk_notification_utilisateur FOREIGN KEY (user_id) REFERENCES public.utilisateur(id);


--
-- Name: poste fk_poste_service; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.poste
    ADD CONSTRAINT fk_poste_service FOREIGN KEY (service_id) REFERENCES public.service(id);


--
-- Name: regle_population fk_regle_population_cadre; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.regle_population
    ADD CONSTRAINT fk_regle_population_cadre FOREIGN KEY (cadre_id) REFERENCES public.cadre(id);


--
-- Name: regle_population fk_regle_population_corps; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.regle_population
    ADD CONSTRAINT fk_regle_population_corps FOREIGN KEY (corps_id) REFERENCES public.corps(id);


--
-- Name: regle_population fk_regle_population_echelle; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.regle_population
    ADD CONSTRAINT fk_regle_population_echelle FOREIGN KEY (echelle_id) REFERENCES public.echelle(id);


--
-- Name: regle_population fk_regle_population_grade; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.regle_population
    ADD CONSTRAINT fk_regle_population_grade FOREIGN KEY (grade_carriere_id) REFERENCES public.grade_carriere(id);


--
-- Name: regle_population fk_regle_population_regle; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.regle_population
    ADD CONSTRAINT fk_regle_population_regle FOREIGN KEY (regle_id) REFERENCES public.regle_rh(id);


--
-- Name: regle_population fk_regle_population_statut; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.regle_population
    ADD CONSTRAINT fk_regle_population_statut FOREIGN KEY (statut_agent_id) REFERENCES public.statut_agent(id);


--
-- Name: regle_reference_juridique fk_regle_reference_article; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.regle_reference_juridique
    ADD CONSTRAINT fk_regle_reference_article FOREIGN KEY (article_juridique_id) REFERENCES public.article_juridique(id);


--
-- Name: regle_reference_juridique fk_regle_reference_regle; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.regle_reference_juridique
    ADD CONSTRAINT fk_regle_reference_regle FOREIGN KEY (regle_id) REFERENCES public.regle_rh(id);


--
-- Name: regle_reference_juridique fk_regle_reference_texte; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.regle_reference_juridique
    ADD CONSTRAINT fk_regle_reference_texte FOREIGN KEY (texte_juridique_id) REFERENCES public.texte_juridique(id);


--
-- Name: regle_rh fk_regle_type; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.regle_rh
    ADD CONSTRAINT fk_regle_type FOREIGN KEY (type_regle_id) REFERENCES public.type_regle_rh(id);


--
-- Name: retraite fk_retraite_employe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.retraite
    ADD CONSTRAINT fk_retraite_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: service fk_service_direction; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.service
    ADD CONSTRAINT fk_service_direction FOREIGN KEY (direction_id) REFERENCES public.direction(id);


--
-- Name: situation_administrative fk_situation_admin_employe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.situation_administrative
    ADD CONSTRAINT fk_situation_admin_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: situation_carriere fk_situation_cadre; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.situation_carriere
    ADD CONSTRAINT fk_situation_cadre FOREIGN KEY (cadre_id) REFERENCES public.cadre(id);


--
-- Name: situation_carriere fk_situation_classe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.situation_carriere
    ADD CONSTRAINT fk_situation_classe FOREIGN KEY (classe_id) REFERENCES public.classe(id);


--
-- Name: situation_carriere fk_situation_corps; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.situation_carriere
    ADD CONSTRAINT fk_situation_corps FOREIGN KEY (corps_id) REFERENCES public.corps(id);


--
-- Name: situation_carriere fk_situation_echelle; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.situation_carriere
    ADD CONSTRAINT fk_situation_echelle FOREIGN KEY (echelle_id) REFERENCES public.echelle(id);


--
-- Name: situation_carriere fk_situation_echelon; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.situation_carriere
    ADD CONSTRAINT fk_situation_echelon FOREIGN KEY (echelon_id) REFERENCES public.echelon(id);


--
-- Name: situation_carriere fk_situation_employe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.situation_carriere
    ADD CONSTRAINT fk_situation_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: situation_carriere fk_situation_grade; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.situation_carriere
    ADD CONSTRAINT fk_situation_grade FOREIGN KEY (grade_carriere_id) REFERENCES public.grade_carriere(id);


--
-- Name: situation_carriere fk_situation_statut; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.situation_carriere
    ADD CONSTRAINT fk_situation_statut FOREIGN KEY (statut_agent_id) REFERENCES public.statut_agent(id);


--
-- Name: tache fk_tache_employe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.tache
    ADD CONSTRAINT fk_tache_employe FOREIGN KEY (employe_id) REFERENCES public.employe(id);


--
-- Name: tache fk_tache_utilisateur; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.tache
    ADD CONSTRAINT fk_tache_utilisateur FOREIGN KEY (user_id) REFERENCES public.utilisateur(id);


--
-- Name: valeur_parametre_regle fk_valeur_parametre; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.valeur_parametre_regle
    ADD CONSTRAINT fk_valeur_parametre FOREIGN KEY (parametre_id) REFERENCES public.parametre_regle_rh(id);


--
-- PostgreSQL database dump complete
--

\unrestrict KVcBOzPzv2eZZEdfJi3uyeeenh5v8KbYjyEWHIOhw3YYcM3EjjoY2lJsA6OGVzs

