--
-- PostgreSQL database dump
--

\restrict YdqOC4thvtFg7ULxX3ep1yXVoP6ycC2LYhNhkdfANVMveh9JFZagt5bbk1htyRP

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
-- Data for Name: report_audit_log; Type: TABLE DATA; Schema: public; Owner: -
--

SET SESSION AUTHORIZATION DEFAULT;

ALTER TABLE public.report_audit_log DISABLE TRIGGER ALL;

COPY public.report_audit_log (id, at, actor_employee_id, action, entity, entity_id, before_json, after_json, ip) FROM stdin;
1	2026-10-30 12:12:16.772029+00	1	EXPORT	Headcount	\N	\N	{"to": "2026-10-30", "from": "2026-04-01"}	\N
2	2026-10-30 12:12:35.054118+00	1	EXPORT	Headcount	\N	\N	{"to": "2026-10-30", "from": "2026-04-01"}	\N
3	2026-10-31 07:42:08.832374+00	1	EXPORT	Headcount	\N	\N	{"to": "2026-10-31", "from": "2026-04-01"}	\N
4	2026-11-09 09:10:03.31041+00	1	CREATE	ReportExport	44	\N	{"period": "2026-11", "sizeBytes": 798, "reportType": "ATTENDANCE_MONTHLY"}	\N
5	2026-11-09 09:10:05.875379+00	1	CREATE	ReportExport	45	\N	{"period": "2026-11", "sizeBytes": 798, "reportType": "ATTENDANCE_MONTHLY"}	\N
6	2026-11-09 09:10:11.564072+00	1	CREATE	ReportExport	46	\N	{"period": "2026-11", "sizeBytes": 798, "reportType": "ATTENDANCE_MONTHLY"}	\N
7	2026-11-09 09:10:19.245136+00	1	CREATE	ReportExport	47	\N	{"period": "2026-09", "sizeBytes": 790, "reportType": "ATTENDANCE_MONTHLY"}	\N
8	2026-11-09 09:10:25.343198+00	1	CREATE	ReportExport	48	\N	{"period": "2026-05", "sizeBytes": 815, "reportType": "ATTENDANCE_MONTHLY"}	\N
9	2026-11-09 09:10:32.367525+00	1	CREATE	ReportExport	49	\N	{"period": "2026-10", "sizeBytes": 853, "reportType": "ATTENDANCE_MONTHLY"}	\N
\.


ALTER TABLE public.report_audit_log ENABLE TRIGGER ALL;

--
-- Data for Name: report_export; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.report_export DISABLE TRIGGER ALL;

COPY public.report_export (id, report_type, period_label, employee_id, file_name, content_type, size_bytes, storage_key, requested_by, created_at, status, error_message, completed_at) FROM stdin;
1	ATTENDANCE_MONTHLY	2026-10	\N	attendance-2026-10.csv	text/csv	818	\N	1	2026-10-29 13:16:35.452583+00	READY	\N	\N
2	ATTENDANCE_MONTHLY	2026-10	\N	attendance-2026-10.csv	text/csv	818	\N	1	2026-10-29 13:16:49.410581+00	READY	\N	\N
3	ATTENDANCE_MONTHLY	2026-10	\N	attendance-2026-10.csv	text/csv	818	\N	1	2026-10-29 13:18:24.226608+00	READY	\N	\N
4	ATTENDANCE_MONTHLY	2026-10	\N	attendance-2026-10.csv	text/csv	818	\N	1	2026-10-29 13:19:11.788651+00	READY	\N	\N
5	ATTENDANCE_MONTHLY	2026-10	\N	attendance-2026-10.csv	text/csv	818	\N	1	2026-10-29 13:22:11.950671+00	READY	\N	\N
6	ATTENDANCE_MONTHLY	2026-10	9	attendance-2026-10.csv	text/csv	844	\N	1	2026-10-29 13:22:18.990183+00	READY	\N	\N
7	ATTENDANCE_MONTHLY	2027-01	9	attendance-2027-01.csv	text/csv	815	\N	1	2026-10-29 13:22:20.925177+00	READY	\N	\N
8	ATTENDANCE_MONTHLY	2027-01	9	attendance-2027-01.csv	text/csv	815	\N	1	2026-10-29 13:22:49.906064+00	READY	\N	\N
9	ATTENDANCE_MONTHLY	2026-10	\N	attendance-2026-10.csv	text/csv	818	\N	1	2026-10-29 13:22:58.859945+00	READY	\N	\N
10	ATTENDANCE_MONTHLY	2026-10	9	attendance-2026-10.csv	text/csv	844	\N	1	2026-10-29 13:23:01.482439+00	READY	\N	\N
11	ATTENDANCE_MONTHLY	2027-01	9	attendance-2027-01.csv	text/csv	815	\N	1	2026-10-29 13:23:03.318358+00	READY	\N	\N
12	ATTENDANCE_MONTHLY	2026-10	\N	attendance-2026-10.csv	text/csv	818	\N	1	2026-10-29 14:02:19.749524+00	READY	\N	\N
13	ATTENDANCE_MONTHLY	2026-10	\N	attendance-2026-10.csv	text/csv	818	\N	1	2026-10-29 16:14:25.129552+00	READY	\N	\N
14	ATTENDANCE_MONTHLY	2026-10	9	attendance-2026-10.csv	text/csv	844	\N	1	2026-10-29 16:14:32.699927+00	READY	\N	\N
15	ATTENDANCE_MONTHLY	2026-10	\N	attendance-2026-10.csv	text/csv	853	\N	9	2026-10-29 18:04:35.583805+00	READY	\N	\N
16	ATTENDANCE_MONTHLY	2026-09	6	attendance-2026-09.csv	text/csv	790	\N	1	2026-10-30 12:12:19.243598+00	READY	\N	\N
17	ATTENDANCE_MONTHLY	2026-09	6	attendance-2026-09.csv	text/csv	790	\N	1	2026-10-30 12:12:35.3311+00	READY	\N	\N
18	ATTENDANCE_MONTHLY	2026-10	9	attendance-2026-10.csv	text/csv	844	\N	9	2026-10-30 12:30:41.569076+00	READY	\N	\N
19	ATTENDANCE_MONTHLY	2026-10	1	attendance-2026-10.csv	text/csv	853	\N	1	2026-10-31 07:47:48.756012+00	READY	\N	\N
20	ATTENDANCE_MONTHLY	2026-10	1	attendance-2026-10.csv	text/csv	853	\N	1	2026-10-31 07:48:17.327286+00	READY	\N	\N
21	ATTENDANCE_MONTHLY	2026-10	14	attendance-2026-10.csv	text/csv	863	\N	14	2026-10-31 07:48:51.966704+00	READY	\N	\N
22	ATTENDANCE_MONTHLY	2026-10	14	attendance-2026-10.csv	text/csv	863	\N	14	2026-10-31 07:49:14.753281+00	READY	\N	\N
23	ATTENDANCE_MONTHLY	2026-09	14	attendance-2026-09.csv	text/csv	820	\N	14	2026-10-31 07:49:42.333092+00	READY	\N	\N
24	ATTENDANCE_MONTHLY	2026-10	14	attendance-2026-10.csv	text/csv	863	\N	14	2026-10-31 07:49:46.603864+00	READY	\N	\N
25	ATTENDANCE_MONTHLY	2026-11	14	attendance-2026-11.csv	text/csv	820	\N	14	2026-10-31 07:49:50.405954+00	READY	\N	\N
26	ATTENDANCE_MONTHLY	2026-10	14	attendance-2026-10.csv	text/csv	863	\N	14	2026-10-31 07:49:54.793317+00	READY	\N	\N
27	ATTENDANCE_MONTHLY	2026-10	14	attendance-2026-10.csv	text/csv	863	\N	14	2026-10-31 07:50:22.148773+00	READY	\N	\N
28	ATTENDANCE_MONTHLY	2026-10	1	attendance-2026-10.csv	text/csv	853	\N	1	2026-10-31 07:51:11.942539+00	READY	\N	\N
29	ATTENDANCE_MONTHLY	2026-10	1	attendance-2026-10.csv	text/csv	853	\N	1	2026-10-31 07:51:38.815285+00	READY	\N	\N
30	ATTENDANCE_MONTHLY	2026-10	1	attendance-2026-10.csv	text/csv	853	\N	1	2026-10-31 07:52:56.96732+00	READY	\N	\N
31	ATTENDANCE_MONTHLY	2026-10	1	attendance-2026-10.csv	text/csv	853	\N	1	2026-10-31 07:53:26.624314+00	READY	\N	\N
32	ATTENDANCE_MONTHLY	2026-10	14	attendance-2026-10.csv	text/csv	863	\N	14	2026-10-31 07:53:50.211943+00	READY	\N	\N
33	ATTENDANCE_MONTHLY	2026-10	12	attendance-2026-10.csv	text/csv	887	\N	12	2026-10-31 07:55:32.487771+00	READY	\N	\N
34	ATTENDANCE_MONTHLY	2026-10	12	attendance-2026-10.csv	text/csv	887	\N	12	2026-10-31 07:56:35.299739+00	READY	\N	\N
35	ATTENDANCE_MONTHLY	2026-10	2	attendance-2026-10.csv	text/csv	844	\N	1	2026-10-31 08:00:15.361649+00	READY	\N	\N
36	ATTENDANCE_MONTHLY	2026-10	12	attendance-2026-10.csv	text/csv	887	\N	12	2026-10-31 08:02:07.640707+00	READY	\N	\N
37	ATTENDANCE_MONTHLY	2026-10	1	attendance-2026-10.csv	text/csv	853	\N	1	2026-10-31 08:02:21.622451+00	READY	\N	\N
38	ATTENDANCE_MONTHLY	2026-10	8	attendance-2026-10.csv	text/csv	856	\N	8	2026-10-31 08:02:44.260508+00	READY	\N	\N
39	ATTENDANCE_MONTHLY	2026-10	11	attendance-2026-10.csv	text/csv	881	\N	11	2026-10-31 10:00:49.662505+00	READY	\N	\N
40	ATTENDANCE_MONTHLY	2026-10	11	attendance-2026-10.csv	text/csv	881	\N	11	2026-10-31 10:00:59.793787+00	READY	\N	\N
41	ATTENDANCE_MONTHLY	2026-09	11	attendance-2026-09.csv	text/csv	820	\N	11	2026-10-31 10:01:02.185432+00	READY	\N	\N
42	ATTENDANCE_MONTHLY	2026-11	\N	attendance-2026-11.csv	text/csv	790	\N	1	2026-11-03 06:38:02.429513+00	READY	\N	\N
43	ATTENDANCE_MONTHLY	2026-11	\N	attendance-2026-11.csv	text/csv	790	\N	1	2026-11-03 06:38:40.611551+00	READY	\N	\N
44	ATTENDANCE_MONTHLY	2026-11	1	attendance-2026-11.csv	text/csv	798	\N	1	2026-11-09 09:10:03.095522+00	READY	\N	2026-11-09 09:10:03.095522+00
45	ATTENDANCE_MONTHLY	2026-11	1	attendance-2026-11.csv	text/csv	798	\N	1	2026-11-09 09:10:05.85549+00	READY	\N	2026-11-09 09:10:05.85549+00
46	ATTENDANCE_MONTHLY	2026-11	1	attendance-2026-11.csv	text/csv	798	\N	1	2026-11-09 09:10:11.550478+00	READY	\N	2026-11-09 09:10:11.550478+00
47	ATTENDANCE_MONTHLY	2026-09	1	attendance-2026-09.csv	text/csv	790	\N	1	2026-11-09 09:10:19.235809+00	READY	\N	2026-11-09 09:10:19.235809+00
48	ATTENDANCE_MONTHLY	2026-05	1	attendance-2026-05.csv	text/csv	815	\N	1	2026-11-09 09:10:25.335219+00	READY	\N	2026-11-09 09:10:25.335219+00
49	ATTENDANCE_MONTHLY	2026-10	1	attendance-2026-10.csv	text/csv	853	\N	1	2026-11-09 09:10:32.359366+00	READY	\N	2026-11-09 09:10:32.359366+00
\.


ALTER TABLE public.report_export ENABLE TRIGGER ALL;

--
-- Name: report_audit_log_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.report_audit_log_id_seq', 9, true);


--
-- Name: report_export_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.report_export_id_seq', 49, true);


--
-- PostgreSQL database dump complete
--

\unrestrict YdqOC4thvtFg7ULxX3ep1yXVoP6ycC2LYhNhkdfANVMveh9JFZagt5bbk1htyRP

