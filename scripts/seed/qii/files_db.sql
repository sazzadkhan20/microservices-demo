--
-- PostgreSQL database dump
--

\restrict pehrCkoRJVCfAseSCUl7VTOY9xcRixyzOLFbuuwVS4WaTtOnRQzbYOrpyjKXxcn

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
18	convocation_application_confiramtion.pdf	application/pdf	168649	qii-hrms-documents/released/f2847066-7116-4167-b1d1-d5525738569d	1	2026-11-09 09:14:07.573475+00	RELEASED	GENERAL	12	heuristic-clean	2026-11-09 09:14:09.282005+00	2026-11-09 09:14:09.437838+00
19	convocation_application_confiramtion.pdf	application/pdf	168649	qii-hrms-documents/released/9d0b0f3e-212f-4745-8be9-6d3797ea7002	1	2026-11-09 12:11:00.036966+00	RELEASED	GENERAL	4	heuristic-clean	2026-11-09 12:11:01.199688+00	2026-11-09 12:11:01.319539+00
20	tiny.png	image/png	70	qii-hrms-documents/released/9b6c3d9d-3fee-4010-8b28-f205ecda393c	1	2026-11-09 17:32:19.803439+00	RELEASED	PHOTO	1	heuristic-clean	2026-11-09 17:32:20.157936+00	2026-11-09 17:32:20.190937+00
21	tiny.pdf	application/pdf	23	qii-hrms-documents/released/830bf79c-c2de-439c-a902-9ea50255d85c	1	2026-11-09 17:32:20.053438+00	RELEASED	GENERAL	1	heuristic-clean	2026-11-09 17:32:21.17981+00	2026-11-09 17:32:21.208771+00
22	639110669_10237551542625682_6113427110361513735_n.jpg	image/jpeg	312598	qii-hrms-documents/released/7e905346-6b5d-41f1-ab3b-081914f26430	1	2026-11-09 17:36:34.088795+00	RELEASED	PHOTO	1	heuristic-clean	2026-11-09 17:36:35.230744+00	2026-11-09 17:36:35.305907+00
23	Aivelon transparent monochrome watermark.png	image/png	412682	qii-hrms-documents/released/a2a58ce4-0441-45eb-bc89-5892f85b46d0	1	2026-11-09 17:36:41.521033+00	RELEASED	PHOTO	1	heuristic-clean	2026-11-09 17:36:42.370113+00	2026-11-09 17:36:42.436808+00
24	646370928_10237780265983623_4975361515082676989_n.jpg	image/jpeg	422864	qii-hrms-documents/released/47d23bab-4259-4e8e-aa3e-de622924808d	1	2026-11-09 17:36:45.880377+00	RELEASED	PHOTO	1	heuristic-clean	2026-11-09 17:36:46.448605+00	2026-11-09 17:36:46.487239+00
25	646370928_10237780265983623_4975361515082676989_n.jpg	image/jpeg	422864	qii-hrms-documents/released/f5a5c497-d985-4636-93fd-fb834ce6b7c3	1	2026-11-09 17:46:06.402368+00	RELEASED	PHOTO	1	heuristic-clean	2026-11-09 17:46:07.13304+00	2026-11-09 17:46:07.201946+00
26	654208159_10238069135285175_1898413046031583718_n.jpg	image/jpeg	256833	qii-hrms-documents/released/18123080-4079-4be5-b308-18101029c603	1	2026-11-09 17:46:12.886544+00	RELEASED	PHOTO	1	heuristic-clean	2026-11-09 17:46:13.293817+00	2026-11-09 17:46:13.340255+00
27	639110669_10237551542625682_6113427110361513735_n.jpg	image/jpeg	312598	qii-hrms-documents/released/684cea3d-6745-4e94-a432-a7adde714bfe	1	2026-11-09 17:46:17.788446+00	RELEASED	PHOTO	1	heuristic-clean	2026-11-09 17:46:18.403659+00	2026-11-09 17:46:18.440895+00
28	f8ce0f61-6aac-476b-ae2f-8648e3d43a6f.jpg	image/jpeg	180803	qii-hrms-documents/released/5df7e0ab-96ba-4d5b-a68a-af51f9201fc9	1	2026-11-09 18:22:16.969687+00	RELEASED	PHOTO	1	heuristic-clean	2026-11-09 18:22:18.062531+00	2026-11-09 18:22:18.12664+00
30	tiny.png	image/png	70	qii-hrms-documents/released/62e67aea-3903-47b3-a984-98a5ec7058f4	1	2026-11-09 18:46:44.333467+00	RELEASED	PHOTO	1	heuristic-clean	2026-11-09 18:46:44.586697+00	2026-11-09 18:46:44.628603+00
29	f8ce0f61-6aac-476b-ae2f-8648e3d43a6f.jpg	image/jpeg	180803	qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2	1	2026-11-09 18:22:24.986271+00	RELEASED	PHOTO	22	heuristic-clean	2026-11-09 18:22:25.212878+00	2026-11-09 18:22:25.27307+00
31	Issues.docx	application/vnd.openxmlformats-officedocument.wordprocessingml.document	23733	qii-hrms-documents/released/7893ed8f-b6fc-415d-b065-b95e1accac59	1	2026-11-09 19:31:50.997538+00	RELEASED	GENERAL	15	heuristic-clean	2026-11-09 19:31:51.28332+00	2026-11-09 19:31:51.357355+00
32	Asst_Software_Engineer_Project_Assignment.pdf	application/pdf	6876984	qii-hrms-documents/released/062c7226-1543-45ed-a852-f964b859ffd7	1	2026-11-09 19:36:20.135053+00	RELEASED	GENERAL	15	heuristic-clean	2026-11-09 19:36:20.590716+00	2026-11-09 19:36:20.715952+00
33	HRMS_Microservices_Developer_Guide.pdf	application/pdf	467158	qii-hrms-documents/released/43884f1f-2345-4e61-8f60-ddf25d3ea917	1	2026-11-09 19:36:44.121251+00	RELEASED	GENERAL	15	heuristic-clean	2026-11-09 19:36:44.202105+00	2026-11-09 19:36:44.246179+00
34	convocation_application_confiramtion.pdf	application/pdf	168649	qii-hrms-documents/released/7683ed12-fcba-4879-bddb-c59720c2fd5d	1	2026-11-09 19:39:39.604746+00	RELEASED	GENERAL	15	heuristic-clean	2026-11-09 19:39:39.727909+00	2026-11-09 19:39:39.780215+00
35	Qii HRMS v1 Software Requirements Specification.pdf	application/pdf	862177	qii-hrms-documents/released/4be167a6-556c-4ba7-95c5-fdcbc6078c8e	1	2026-11-09 19:40:07.60947+00	RELEASED	GENERAL	15	heuristic-clean	2026-11-09 19:40:07.668769+00	2026-11-09 19:40:07.719773+00
36	646370928_10237780265983623_4975361515082676989_n.jpg	image/jpeg	422864	qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2	1	2026-11-09 20:42:25.217163+00	RELEASED	PHOTO	4	heuristic-clean	2026-11-09 20:42:25.677764+00	2026-11-09 20:42:25.751063+00
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
34	2026-11-09 09:14:07.675282+00	12	UPLOAD	FileObject	18	\N	{"sizeBytes": 168649, "scanStatus": "QUARANTINED", "contentType": "application/pdf", "originalName": "convocation_application_confiramtion.pdf", "classification": "GENERAL"}	\N
35	2026-11-09 09:14:09.437838+00	12	SCAN_RELEASE	FileObject	18	"SCANNING"	"RELEASED"	\N
36	2026-11-09 09:14:25.02417+00	1	DOWNLOAD_URL	FileObject	18	\N	{"expiresAt": "2026-11-09T09:16:25Z", "scanStatus": "RELEASED", "classification": "GENERAL"}	\N
37	2026-11-09 09:14:25.120682+00	1	DOWNLOAD	FileObject	18	\N	{"via": "short-lived-token", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/f2847066-7116-4167-b1d1-d5525738569d"}	\N
38	2026-11-09 12:11:00.192378+00	4	UPLOAD	FileObject	19	\N	{"sizeBytes": 168649, "scanStatus": "QUARANTINED", "contentType": "application/pdf", "originalName": "convocation_application_confiramtion.pdf", "classification": "GENERAL"}	\N
39	2026-11-09 12:11:01.320489+00	4	SCAN_RELEASE	FileObject	19	"SCANNING"	"RELEASED"	\N
40	2026-11-09 17:32:19.844257+00	1	UPLOAD	FileObject	20	\N	{"sizeBytes": 70, "scanStatus": "QUARANTINED", "contentType": "image/png", "originalName": "tiny.png", "classification": "PHOTO"}	\N
41	2026-11-09 17:32:20.056979+00	1	UPLOAD	FileObject	21	\N	{"sizeBytes": 23, "scanStatus": "QUARANTINED", "contentType": "application/pdf", "originalName": "tiny.pdf", "classification": "GENERAL"}	\N
42	2026-11-09 17:32:20.19146+00	1	SCAN_RELEASE	FileObject	20	"SCANNING"	"RELEASED"	\N
43	2026-11-09 17:32:21.209463+00	1	SCAN_RELEASE	FileObject	21	"SCANNING"	"RELEASED"	\N
44	2026-11-09 17:36:34.24392+00	1	UPLOAD	FileObject	22	\N	{"sizeBytes": 312598, "scanStatus": "QUARANTINED", "contentType": "image/jpeg", "originalName": "639110669_10237551542625682_6113427110361513735_n.jpg", "classification": "PHOTO"}	\N
45	2026-11-09 17:36:35.306922+00	1	SCAN_RELEASE	FileObject	22	"SCANNING"	"RELEASED"	\N
46	2026-11-09 17:36:41.524682+00	1	UPLOAD	FileObject	23	\N	{"sizeBytes": 412682, "scanStatus": "QUARANTINED", "contentType": "image/png", "originalName": "Aivelon transparent monochrome watermark.png", "classification": "PHOTO"}	\N
47	2026-11-09 17:36:42.437814+00	1	SCAN_RELEASE	FileObject	23	"SCANNING"	"RELEASED"	\N
48	2026-11-09 17:36:45.88517+00	1	UPLOAD	FileObject	24	\N	{"sizeBytes": 422864, "scanStatus": "QUARANTINED", "contentType": "image/jpeg", "originalName": "646370928_10237780265983623_4975361515082676989_n.jpg", "classification": "PHOTO"}	\N
49	2026-11-09 17:36:46.487239+00	1	SCAN_RELEASE	FileObject	24	"SCANNING"	"RELEASED"	\N
50	2026-11-09 17:46:06.484105+00	1	UPLOAD	FileObject	25	\N	{"sizeBytes": 422864, "scanStatus": "QUARANTINED", "contentType": "image/jpeg", "originalName": "646370928_10237780265983623_4975361515082676989_n.jpg", "classification": "PHOTO"}	\N
51	2026-11-09 17:46:07.202521+00	1	SCAN_RELEASE	FileObject	25	"SCANNING"	"RELEASED"	\N
52	2026-11-09 17:46:12.904069+00	1	UPLOAD	FileObject	26	\N	{"sizeBytes": 256833, "scanStatus": "QUARANTINED", "contentType": "image/jpeg", "originalName": "654208159_10238069135285175_1898413046031583718_n.jpg", "classification": "PHOTO"}	\N
53	2026-11-09 17:46:13.342052+00	1	SCAN_RELEASE	FileObject	26	"SCANNING"	"RELEASED"	\N
54	2026-11-09 17:46:17.791348+00	1	UPLOAD	FileObject	27	\N	{"sizeBytes": 312598, "scanStatus": "QUARANTINED", "contentType": "image/jpeg", "originalName": "639110669_10237551542625682_6113427110361513735_n.jpg", "classification": "PHOTO"}	\N
55	2026-11-09 17:46:18.441412+00	1	SCAN_RELEASE	FileObject	27	"SCANNING"	"RELEASED"	\N
56	2026-11-09 18:22:17.046078+00	1	UPLOAD	FileObject	28	\N	{"sizeBytes": 180803, "scanStatus": "QUARANTINED", "contentType": "image/jpeg", "originalName": "f8ce0f61-6aac-476b-ae2f-8648e3d43a6f.jpg", "classification": "PHOTO"}	\N
57	2026-11-09 18:22:18.127538+00	1	SCAN_RELEASE	FileObject	28	"SCANNING"	"RELEASED"	\N
58	2026-11-09 18:22:24.993141+00	1	UPLOAD	FileObject	29	\N	{"sizeBytes": 180803, "scanStatus": "QUARANTINED", "contentType": "image/jpeg", "originalName": "f8ce0f61-6aac-476b-ae2f-8648e3d43a6f.jpg", "classification": "PHOTO"}	\N
59	2026-11-09 18:22:25.273576+00	1	SCAN_RELEASE	FileObject	29	"SCANNING"	"RELEASED"	\N
60	2026-11-09 18:46:44.371164+00	1	UPLOAD	FileObject	30	\N	{"sizeBytes": 70, "scanStatus": "QUARANTINED", "contentType": "image/png", "originalName": "tiny.png", "classification": "PHOTO"}	\N
61	2026-11-09 18:46:44.629151+00	1	SCAN_RELEASE	FileObject	30	"SCANNING"	"RELEASED"	\N
62	2026-11-09 18:46:54.505907+00	1	DOWNLOAD	FileObject	30	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/62e67aea-3903-47b3-a984-98a5ec7058f4"}	\N
63	2026-11-09 18:48:28.477881+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
64	2026-11-09 18:48:28.694707+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
65	2026-11-09 18:48:28.769609+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
66	2026-11-09 18:48:28.860748+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
67	2026-11-09 19:09:34.933299+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
68	2026-11-09 19:09:35.817314+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
69	2026-11-09 19:09:35.933492+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
70	2026-11-09 19:09:36.029891+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
71	2026-11-09 19:11:28.946453+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
72	2026-11-09 19:11:29.222281+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
73	2026-11-09 19:11:29.366069+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
74	2026-11-09 19:11:29.463264+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
75	2026-11-09 19:12:30.596941+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
76	2026-11-09 19:12:30.683727+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
77	2026-11-09 19:12:30.751304+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
78	2026-11-09 19:12:30.810798+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
79	2026-11-09 19:12:35.02749+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
80	2026-11-09 19:20:11.510736+00	24	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
81	2026-11-09 19:20:12.065414+00	24	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
82	2026-11-09 19:20:14.351481+00	24	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
83	2026-11-09 19:20:14.489426+00	24	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
84	2026-11-09 19:20:27.96676+00	24	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
85	2026-11-09 19:20:28.034108+00	24	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
86	2026-11-09 19:20:50.030695+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
87	2026-11-09 19:20:50.107803+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
88	2026-11-09 19:20:50.171926+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
89	2026-11-09 19:20:50.234156+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
90	2026-11-09 19:21:00.243072+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
91	2026-11-09 19:21:00.321137+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
92	2026-11-09 19:22:36.631853+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
93	2026-11-09 19:22:36.693326+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
94	2026-11-09 19:22:55.438748+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
95	2026-11-09 19:22:55.493864+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
96	2026-11-09 19:22:55.555634+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
97	2026-11-09 19:22:55.61103+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
98	2026-11-09 19:23:01.46688+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
99	2026-11-09 19:23:01.560343+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
100	2026-11-09 19:23:01.619387+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
101	2026-11-09 19:23:01.678726+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
102	2026-11-09 19:23:03.279901+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
103	2026-11-09 19:23:03.364386+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
104	2026-11-09 19:23:09.907848+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
105	2026-11-09 19:23:09.973777+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
106	2026-11-09 19:23:55.15464+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
107	2026-11-09 19:23:55.209949+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
108	2026-11-09 19:23:55.259089+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
109	2026-11-09 19:23:55.335422+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
110	2026-11-09 19:24:11.381172+00	23	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
111	2026-11-09 19:24:11.524856+00	23	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
112	2026-11-09 19:25:13.034695+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
113	2026-11-09 19:25:13.089865+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
114	2026-11-09 19:25:13.135306+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
115	2026-11-09 19:25:13.180068+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
116	2026-11-09 19:25:16.602628+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
117	2026-11-09 19:25:16.677656+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
118	2026-11-09 19:25:54.899137+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
119	2026-11-09 19:25:54.951938+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
120	2026-11-09 19:31:51.025075+00	15	UPLOAD	FileObject	31	\N	{"sizeBytes": 23733, "scanStatus": "QUARANTINED", "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "originalName": "Issues.docx", "classification": "GENERAL"}	\N
121	2026-11-09 19:31:51.357973+00	15	SCAN_RELEASE	FileObject	31	"SCANNING"	"RELEASED"	\N
159	2026-11-10 08:28:49.930848+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
122	2026-11-09 19:36:20.206521+00	15	UPLOAD	FileObject	32	\N	{"sizeBytes": 6876984, "scanStatus": "QUARANTINED", "contentType": "application/pdf", "originalName": "Asst_Software_Engineer_Project_Assignment.pdf", "classification": "GENERAL"}	\N
123	2026-11-09 19:36:20.716604+00	15	SCAN_RELEASE	FileObject	32	"SCANNING"	"RELEASED"	\N
124	2026-11-09 19:36:44.126557+00	15	UPLOAD	FileObject	33	\N	{"sizeBytes": 467158, "scanStatus": "QUARANTINED", "contentType": "application/pdf", "originalName": "HRMS_Microservices_Developer_Guide.pdf", "classification": "GENERAL"}	\N
125	2026-11-09 19:36:44.247029+00	15	SCAN_RELEASE	FileObject	33	"SCANNING"	"RELEASED"	\N
126	2026-11-09 19:39:39.624316+00	15	UPLOAD	FileObject	34	\N	{"sizeBytes": 168649, "scanStatus": "QUARANTINED", "contentType": "application/pdf", "originalName": "convocation_application_confiramtion.pdf", "classification": "GENERAL"}	\N
127	2026-11-09 19:39:39.780752+00	15	SCAN_RELEASE	FileObject	34	"SCANNING"	"RELEASED"	\N
128	2026-11-09 19:40:07.612234+00	15	UPLOAD	FileObject	35	\N	{"sizeBytes": 862177, "scanStatus": "QUARANTINED", "contentType": "application/pdf", "originalName": "Qii HRMS v1 Software Requirements Specification.pdf", "classification": "GENERAL"}	\N
129	2026-11-09 19:40:07.719773+00	15	SCAN_RELEASE	FileObject	35	"SCANNING"	"RELEASED"	\N
130	2026-11-09 20:13:45.752319+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
131	2026-11-09 20:13:46.51932+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
132	2026-11-09 20:13:46.603045+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
133	2026-11-09 20:13:46.67353+00	1	DOWNLOAD	FileObject	29	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2"}	\N
134	2026-11-09 20:42:25.259338+00	4	UPLOAD	FileObject	36	\N	{"sizeBytes": 422864, "scanStatus": "QUARANTINED", "contentType": "image/jpeg", "originalName": "646370928_10237780265983623_4975361515082676989_n.jpg", "classification": "PHOTO"}	\N
135	2026-11-09 20:42:25.751063+00	4	SCAN_RELEASE	FileObject	36	"SCANNING"	"RELEASED"	\N
136	2026-11-09 20:42:28.627929+00	1	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
137	2026-11-09 20:42:30.549928+00	1	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
138	2026-11-09 20:42:30.636259+00	1	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
139	2026-11-10 00:46:38.375614+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
140	2026-11-10 00:46:38.744494+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
141	2026-11-10 00:48:21.000967+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
142	2026-11-10 00:48:21.333705+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
143	2026-11-10 00:48:28.339433+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
144	2026-11-10 00:48:28.476797+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
145	2026-11-10 01:00:17.083772+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
146	2026-11-10 01:00:17.428087+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
147	2026-11-10 01:01:54.078999+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
148	2026-11-10 01:01:54.208171+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
149	2026-11-10 01:08:19.368641+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
150	2026-11-10 01:08:19.590514+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
151	2026-11-10 07:46:34.668447+00	1	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
152	2026-11-10 07:46:34.920475+00	1	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
153	2026-11-10 07:46:34.979433+00	1	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
154	2026-11-10 07:46:35.031883+00	1	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
155	2026-11-10 07:46:36.400146+00	1	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
156	2026-11-10 07:46:36.465789+00	1	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
157	2026-11-10 07:47:10.068592+00	1	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
158	2026-11-10 07:47:10.139817+00	1	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
160	2026-11-10 08:28:50.265945+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
161	2026-11-10 08:29:30.767264+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
162	2026-11-10 08:29:30.87835+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
163	2026-11-10 08:37:07.625167+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
164	2026-11-10 08:37:07.719843+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
165	2026-11-10 08:43:52.988008+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
166	2026-11-10 08:43:53.215763+00	4	DOWNLOAD	FileObject	36	\N	{"via": "session", "scanStatus": "RELEASED", "storageKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	\N
\.


ALTER TABLE public.files_audit_log ENABLE TRIGGER ALL;

--
-- Name: file_object_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.file_object_id_seq', 36, true);


--
-- Name: files_audit_log_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.files_audit_log_id_seq', 166, true);


--
-- PostgreSQL database dump complete
--

\unrestrict pehrCkoRJVCfAseSCUl7VTOY9xcRixyzOLFbuuwVS4WaTtOnRQzbYOrpyjKXxcn

