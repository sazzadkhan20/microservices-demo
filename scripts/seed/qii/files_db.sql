--
-- PostgreSQL database dump
--

\restrict bH1dqHMN51PgLkU9h8eLi6FOhKnDU4bOCgdHu0x73e20hZL9Uta1pvjTPFw4GT9

-- Dumped from database version 16.15 (Debian 16.15-1.pgdg13+2)
-- Dumped by pg_dump version 16.15 (Debian 16.15-1.pgdg13+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: file_object; Type: TABLE DATA; Schema: public; Owner: -
--

SET SESSION AUTHORIZATION DEFAULT;

ALTER TABLE public.file_object DISABLE TRIGGER ALL;

COPY public.file_object (id, original_name, content_type, size_bytes, storage_key, uploaded_by, created_at, scan_status, classification, employee_id, scan_detail, scanned_at, released_at) FROM stdin;
1	smoke.pdf	application/pdf	45	qii-hrms-documents/released/b07b3b0a-dab6-45e7-92b6-fe982b7b5cf5	1	2026-10-29 12:40:22.340476+00	RELEASED	GENERAL	\N	heuristic-clean	2026-11-08 10:08:57.167159+00	2026-11-08 10:08:57.218758+00
2	e2e-1793278094009.pdf	application/pdf	45	qii-hrms-documents/released/8cf350ff-6d99-4418-ba38-5832e2391ae8	1	2026-10-29 12:48:18.17566+00	RELEASED	GENERAL	\N	heuristic-clean	2026-11-08 10:08:57.254209+00	2026-11-08 10:08:57.269144+00
3	e2e-1793278197932.pdf	application/pdf	45	qii-hrms-documents/released/ea1499d6-df8e-4531-8868-491533e4086d	1	2026-10-29 12:50:01.756853+00	RELEASED	GENERAL	\N	heuristic-clean	2026-11-08 10:08:57.296389+00	2026-11-08 10:08:57.312672+00
4	e2e-1793278249413.pdf	application/pdf	45	qii-hrms-documents/released/22a3cea8-ac9b-490a-a2f7-f67a9f9629d3	1	2026-10-29 12:50:53.346935+00	RELEASED	GENERAL	\N	heuristic-clean	2026-11-08 10:08:57.340979+00	2026-11-08 10:08:57.358463+00
5	smoke.pdf	application/pdf	25	qii-hrms-documents/37929731-f6db-47c8-9a2b-88feec745446	1	2026-10-29 13:17:21.657639+00	REJECTED	GENERAL	\N	PDF structure invalid (missing %%EOF)	2026-11-08 10:08:57.384796+00	\N
6	e2e-1793280134122.pdf	application/pdf	45	qii-hrms-documents/c8f36181-7c42-4027-b955-6c409ecdea16	1	2026-10-29 13:22:18.532229+00	REJECTED	GENERAL	\N	MinIO get failed: The specified key does not exist.	2026-11-08 10:34:25.362105+00	\N
7	deep-1793280139377.pdf	application/pdf	13	qii-hrms-documents/9f5b7636-d041-427e-a344-f11e64d5cad8	1	2026-10-29 13:22:20.752524+00	REJECTED	GENERAL	\N	PDF structure invalid (missing %%EOF)	2026-11-08 10:34:25.441233+00	\N
8	deep-1793280167436.pdf	application/pdf	13	qii-hrms-documents/5adca6a0-5417-4686-abe2-2b33d26a7bbb	1	2026-10-29 13:22:49.781863+00	REJECTED	GENERAL	\N	PDF structure invalid (missing %%EOF)	2026-11-08 10:34:25.50142+00	\N
9	e2e-1793280179077.pdf	application/pdf	45	qii-hrms-documents/released/8121e13e-09e4-4ce3-8044-9f1c02b5a869	1	2026-10-29 13:23:01.304355+00	RELEASED	GENERAL	\N	heuristic-clean	2026-11-08 10:34:25.564277+00	2026-11-08 10:34:25.692237+00
10	deep-1793280181701.pdf	application/pdf	13	qii-hrms-documents/68f2fe0a-a1d5-41b2-9d6c-d78fc08c93ca	1	2026-10-29 13:23:03.134688+00	REJECTED	GENERAL	\N	PDF structure invalid (missing %%EOF)	2026-11-08 10:34:25.786007+00	\N
11	e2e-1793290467215.pdf	application/pdf	45	qii-hrms-documents/released/73f26515-0db3-4f45-88c2-b62afed7542b	1	2026-10-29 16:14:32.337329+00	RELEASED	GENERAL	\N	heuristic-clean	2026-11-08 10:34:25.831108+00	2026-11-08 10:34:25.912808+00
12	Qii HRMS v1 Software Requirements Specification.pdf	application/pdf	862177	qii-hrms-documents/released/19085606-bc64-477c-8298-fe14652ca540	2	2026-10-30 06:34:04.136394+00	RELEASED	GENERAL	\N	heuristic-clean	2026-11-08 10:34:27.295245+00	2026-11-08 10:34:29.550475+00
13	qii-pixel.png	image/png	67	qii-hrms-documents/released/e46c657a-9411-4780-a83c-6fad976152ca	1	2026-10-31 07:45:31.536038+00	RELEASED	GENERAL	\N	heuristic-clean	2026-11-08 10:34:29.637758+00	2026-11-08 10:34:29.692562+00
14	Qii HRMS v1 Software Requirements Specification (1).pdf	application/pdf	862177	qii-hrms-documents/released/471ade64-4059-42f5-a78d-bd5338a0f86b	1	2026-10-31 07:52:28.051826+00	RELEASED	GENERAL	\N	heuristic-clean	2026-11-08 10:34:29.816998+00	2026-11-08 10:34:29.879793+00
15	convocation_application_confiramtion.pdf	application/pdf	168649	qii-hrms-documents/released/df0c670c-3d42-402f-92e7-dde15ef698db	1	2026-11-08 10:59:29.034016+00	RELEASED	GENERAL	18	heuristic-clean	2026-11-08 10:59:30.75957+00	2026-11-08 10:59:30.885319+00
16	Issues.docx	application/vnd.openxmlformats-officedocument.wordprocessingml.document	23733	qii-hrms-documents/released/e8f65435-a13e-4e00-8f8a-963fa5f05985	1	2026-11-08 19:10:04.914902+00	RELEASED	GENERAL	11	heuristic-clean	2026-11-08 19:10:06.200499+00	2026-11-08 19:10:06.275862+00
\.


ALTER TABLE public.file_object ENABLE TRIGGER ALL;

--
-- Data for Name: files_audit_log; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.files_audit_log DISABLE TRIGGER ALL;

COPY public.files_audit_log (id, at, actor_employee_id, action, entity, entity_id, before_json, after_json, ip) FROM stdin;
1	2026-11-08 10:08:57.221037+00	\N	SCAN_RELEASE	FileObject	1	"SCANNING"	"RELEASED"	\N
2	2026-11-08 10:08:57.270258+00	\N	SCAN_RELEASE	FileObject	2	"SCANNING"	"RELEASED"	\N
3	2026-11-08 10:08:57.313348+00	\N	SCAN_RELEASE	FileObject	3	"SCANNING"	"RELEASED"	\N
4	2026-11-08 10:08:57.358989+00	\N	SCAN_RELEASE	FileObject	4	"SCANNING"	"RELEASED"	\N
5	2026-11-08 10:08:57.384796+00	\N	SCAN_REJECT	FileObject	5	"SCANNING"	"REJECTED"	\N
6	2026-11-08 10:08:57.645358+00	\N	SCAN_RELEASE	FileObject	6	"SCANNING"	"RELEASED"	\N
7	2026-11-08 10:34:25.364879+00	\N	SCAN_REJECT	FileObject	6	"SCANNING"	"REJECTED"	\N
8	2026-11-08 10:34:25.44241+00	\N	SCAN_REJECT	FileObject	7	"SCANNING"	"REJECTED"	\N
9	2026-11-08 10:34:25.502506+00	\N	SCAN_REJECT	FileObject	8	"SCANNING"	"REJECTED"	\N
10	2026-11-08 10:34:25.694031+00	\N	SCAN_RELEASE	FileObject	9	"SCANNING"	"RELEASED"	\N
11	2026-11-08 10:34:25.787248+00	\N	SCAN_REJECT	FileObject	10	"SCANNING"	"REJECTED"	\N
12	2026-11-08 10:34:25.913313+00	\N	SCAN_RELEASE	FileObject	11	"SCANNING"	"RELEASED"	\N
13	2026-11-08 10:34:29.586337+00	\N	SCAN_RELEASE	FileObject	12	"SCANNING"	"RELEASED"	\N
14	2026-11-08 10:34:29.693621+00	\N	SCAN_RELEASE	FileObject	13	"SCANNING"	"RELEASED"	\N
15	2026-11-08 10:34:29.880381+00	\N	SCAN_RELEASE	FileObject	14	"SCANNING"	"RELEASED"	\N
16	2026-11-08 10:59:29.451828+00	18	UPLOAD	FileObject	15	\N	{"sizeBytes": 168649, "scanStatus": "QUARANTINED", "contentType": "application/pdf", "originalName": "convocation_application_confiramtion.pdf", "classification": "GENERAL"}	\N
17	2026-11-08 10:59:30.893852+00	18	SCAN_RELEASE	FileObject	15	"SCANNING"	"RELEASED"	\N
18	2026-11-08 10:59:37.899683+00	1	DOWNLOAD_URL	FileObject	15	\N	{"expiresAt": "2026-11-08T11:01:37Z", "scanStatus": "RELEASED", "classification": "GENERAL"}	\N
19	2026-11-08 10:59:37.998891+00	1	DOWNLOAD	FileObject	15	\N	{"via": "short-lived-token", "scanStatus": "RELEASED", "storageKey": "t-qii-creative/released/df0c670c-3d42-402f-92e7-dde15ef698db"}	\N
20	2026-11-08 19:10:05.228208+00	11	UPLOAD	FileObject	16	\N	{"sizeBytes": 23733, "scanStatus": "QUARANTINED", "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "originalName": "Issues.docx", "classification": "GENERAL"}	\N
21	2026-11-08 19:10:06.276406+00	11	SCAN_RELEASE	FileObject	16	"SCANNING"	"RELEASED"	\N
22	2026-11-08 19:10:26.779712+00	1	DOWNLOAD_URL	FileObject	16	\N	{"expiresAt": "2026-11-08T19:12:26Z", "scanStatus": "RELEASED", "classification": "GENERAL"}	\N
23	2026-11-08 19:10:26.856055+00	1	DOWNLOAD	FileObject	16	\N	{"via": "short-lived-token", "scanStatus": "RELEASED", "storageKey": "t-qii-creative/released/e8f65435-a13e-4e00-8f8a-963fa5f05985"}	\N
24	2026-11-08 20:08:04.118403+00	8	DOWNLOAD_URL	FileObject	16	\N	{"expiresAt": "2026-11-08T20:10:04Z", "scanStatus": "RELEASED", "classification": "GENERAL"}	\N
25	2026-11-08 20:08:04.466921+00	8	DOWNLOAD	FileObject	16	\N	{"via": "short-lived-token", "scanStatus": "RELEASED", "storageKey": "t-qii-creative/released/e8f65435-a13e-4e00-8f8a-963fa5f05985"}	\N
26	2026-11-09 07:11:56.87118+00	1	UPLOAD	FileObject	17	\N	{"sizeBytes": 45, "scanStatus": "QUARANTINED", "contentType": "application/pdf", "originalName": "probe.pdf", "classification": "GENERAL"}	\N
27	2026-11-09 07:11:57.654909+00	1	SCAN_RELEASE	FileObject	17	"SCANNING"	"RELEASED"	\N
28	2026-11-09 07:11:59.442119+00	1	DOWNLOAD_URL	FileObject	16	\N	{"expiresAt": "2026-11-09T07:13:59Z", "scanStatus": "RELEASED", "classification": "GENERAL"}	\N
29	2026-11-09 07:11:59.58254+00	1	DELETE	FileObject	17	{"scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/40e2f32a-cb1d-4323-877f-a86107424b39"}	\N	\N
30	2026-11-09 07:12:11.202296+00	1	DOWNLOAD_URL	FileObject	16	\N	{"expiresAt": "2026-11-09T07:14:11Z", "scanStatus": "RELEASED", "classification": "GENERAL"}	\N
31	2026-11-09 07:12:11.362543+00	1	DOWNLOAD	FileObject	16	\N	{"via": "short-lived-token", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/e8f65435-a13e-4e00-8f8a-963fa5f05985"}	\N
32	2026-11-09 07:13:42.530433+00	2	DOWNLOAD_URL	FileObject	16	\N	{"expiresAt": "2026-11-09T07:15:42Z", "scanStatus": "RELEASED", "classification": "GENERAL"}	\N
33	2026-11-09 07:13:42.6998+00	2	DOWNLOAD	FileObject	16	\N	{"via": "short-lived-token", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/e8f65435-a13e-4e00-8f8a-963fa5f05985"}	\N
\.


ALTER TABLE public.files_audit_log ENABLE TRIGGER ALL;

--
-- Name: file_object_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.file_object_id_seq', 17, true);


--
-- Name: files_audit_log_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.files_audit_log_id_seq', 33, true);


--
-- PostgreSQL database dump complete
--

\unrestrict bH1dqHMN51PgLkU9h8eLi6FOhKnDU4bOCgdHu0x73e20hZL9Uta1pvjTPFw4GT9

