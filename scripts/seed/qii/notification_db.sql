--
-- PostgreSQL database dump
--

\restrict mohdut2SefFjT8K5DOepqoPLaUlqrciirRuVVYH3ztxeDQ3rDQg8nJ062exgUPg

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
-- Data for Name: notification_audit_log; Type: TABLE DATA; Schema: public; Owner: -
--

SET SESSION AUTHORIZATION DEFAULT;

ALTER TABLE public.notification_audit_log DISABLE TRIGGER ALL;

COPY public.notification_audit_log (id, at, actor_employee_id, action, entity, entity_id, before_json, after_json, ip) FROM stdin;
1	2026-11-08 19:00:42.896851+00	8	CREATE	Notification	146	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
\.


ALTER TABLE public.notification_audit_log ENABLE TRIGGER ALL;

--
-- Data for Name: notifications; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.notifications DISABLE TRIGGER ALL;

COPY public.notifications (id, employee_id, title, body, category, read_flag, dedupe_key, created_at, link_url, delivery_attempts, last_delivery_at, delivery_status) FROM stdin;
5	9	Leave PENDING_APPROVAL	Leave application #17 is now PENDING_APPROVAL	LEAVE	f	leave-17-PENDING_APPROVAL	2026-10-29 12:48:16.137531+00	\N	1	\N	DELIVERED
8	9	Leave APPROVED	Leave application #18 is now APPROVED	LEAVE	f	leave-18-APPROVED	2026-10-29 12:48:32.478856+00	\N	1	\N	DELIVERED
7	9	Leave PENDING_APPROVAL	Leave application #18 is now PENDING_APPROVAL	LEAVE	f	leave-18-PENDING_APPROVAL	2026-10-29 12:48:32.150532+00	\N	1	\N	DELIVERED
11	9	Leave APPROVED	Leave application #21 is now APPROVED	LEAVE	f	leave-21-APPROVED	2026-10-29 12:50:51.175364+00	\N	1	\N	DELIVERED
10	9	Leave PENDING_APPROVAL	Leave application #21 is now PENDING_APPROVAL	LEAVE	f	leave-21-PENDING_APPROVAL	2026-10-29 12:50:51.080079+00	\N	1	\N	DELIVERED
4	9	Leave decided	Approved	LEAVE	f	leave-smoke-1	2026-10-29 12:40:46.136107+00	\N	1	\N	DELIVERED
1	1	Pwned	hi	\N	t	\N	2026-10-29 10:57:27.610315+00	\N	1	\N	DELIVERED
2	9	Pwned2	nope	SYSTEM	f	\N	2026-10-29 11:15:32.653051+00	\N	1	\N	DELIVERED
6	1	E2E ping 1793278094009	System dummy notification	SYSTEM	t	\N	2026-10-29 12:48:18.562059+00	\N	1	\N	DELIVERED
9	1	E2E ping 1793278197932	System dummy notification	SYSTEM	t	\N	2026-10-29 12:50:01.900623+00	\N	1	\N	DELIVERED
12	1	E2E ping 1793278249413	System dummy notification	SYSTEM	t	\N	2026-10-29 12:50:53.484696+00	\N	1	\N	DELIVERED
13	9	Leave PENDING_APPROVAL	Leave application #27 is now PENDING_APPROVAL	LEAVE	f	leave-27-PENDING_APPROVAL	2026-10-29 13:22:16.864285+00	\N	1	\N	DELIVERED
14	9	Leave APPROVED	Leave application #27 is now APPROVED	LEAVE	f	leave-27-APPROVED	2026-10-29 13:22:17.026036+00	\N	1	\N	DELIVERED
15	1	E2E ping 1793280134122	System dummy notification	SYSTEM	t	\N	2026-10-29 13:22:18.781192+00	\N	1	\N	DELIVERED
16	9	Deep ping 1793280139377	logic suite	SYSTEM	f	\N	2026-10-29 13:22:20.8415+00	\N	1	\N	DELIVERED
17	9	Leave PENDING_APPROVAL	Leave application #29 is now PENDING_APPROVAL	LEAVE	f	leave-29-PENDING_APPROVAL	2026-10-29 13:22:48.796117+00	\N	1	\N	DELIVERED
18	9	Leave rejected	Leave application #30 is now REJECTED	LEAVE	f	leave-30-REJECTED	2026-10-29 13:22:49.035954+00	\N	1	\N	DELIVERED
19	9	Leave APPROVED	Leave application #29 is now APPROVED	LEAVE	f	leave-29-APPROVED	2026-10-29 13:22:49.343402+00	\N	1	\N	DELIVERED
20	9	Deep ping 1793280167436	logic suite	SYSTEM	f	\N	2026-10-29 13:22:49.840971+00	\N	1	\N	DELIVERED
21	9	Leave PENDING_APPROVAL	Leave application #33 is now PENDING_APPROVAL	LEAVE	f	leave-33-PENDING_APPROVAL	2026-10-29 13:23:00.081924+00	\N	1	\N	DELIVERED
22	9	Leave APPROVED	Leave application #33 is now APPROVED	LEAVE	f	leave-33-APPROVED	2026-10-29 13:23:00.121177+00	\N	1	\N	DELIVERED
23	1	E2E ping 1793280179077	System dummy notification	SYSTEM	t	\N	2026-10-29 13:23:01.381886+00	\N	1	\N	DELIVERED
24	9	Leave PENDING_APPROVAL	Leave application #34 is now PENDING_APPROVAL	LEAVE	f	leave-34-PENDING_APPROVAL	2026-10-29 13:23:02.611263+00	\N	1	\N	DELIVERED
25	9	Leave rejected	Leave application #35 is now REJECTED	LEAVE	f	leave-35-REJECTED	2026-10-29 13:23:02.70217+00	\N	1	\N	DELIVERED
26	9	Leave APPROVED	Leave application #34 is now APPROVED	LEAVE	f	leave-34-APPROVED	2026-10-29 13:23:02.749402+00	\N	1	\N	DELIVERED
27	9	Deep ping 1793280181701	logic suite	SYSTEM	f	\N	2026-10-29 13:23:03.216525+00	\N	1	\N	DELIVERED
28	9	Leave PENDING_APPROVAL	Leave application #38 is now PENDING_APPROVAL	LEAVE	f	leave-38-PENDING_APPROVAL	2026-10-29 16:14:30.05425+00	\N	1	\N	DELIVERED
29	9	Leave APPROVED	Leave application #38 is now APPROVED	LEAVE	f	leave-38-APPROVED	2026-10-29 16:14:30.225757+00	\N	1	\N	DELIVERED
30	1	E2E ping 1793290467215	System dummy notification	SYSTEM	t	\N	2026-10-29 16:14:32.54658+00	\N	1	\N	DELIVERED
33	6	Leave PENDING_APPROVAL	Leave application #5 is now PENDING_APPROVAL	LEAVE	f	leave-5-PENDING_APPROVAL	2026-10-30 09:48:14.524371+00	\N	1	\N	DELIVERED
34	6	Leave APPROVED	Leave application #5 is now APPROVED	LEAVE	f	leave-5-APPROVED	2026-10-30 09:48:14.824181+00	\N	1	\N	DELIVERED
35	6	Leave PENDING_APPROVAL	Leave application #6 is now PENDING_APPROVAL	LEAVE	f	leave-6-PENDING_APPROVAL	2026-10-30 09:49:03.50062+00	\N	1	\N	DELIVERED
36	6	Leave APPROVED	Leave application #6 is now APPROVED	LEAVE	f	leave-6-APPROVED	2026-10-30 09:49:03.662617+00	\N	1	\N	DELIVERED
37	2	Missing work log	No work log for 2026-10-29. Please add entries for that day.	WORKLOG	f	worklog_missing-2-2026-10-29	2026-10-30 10:14:26.604038+00	\N	1	\N	DELIVERED
38	3	Team member missing work log	Nadia Chowdhury has no work log for 2026-10-29	WORKLOG	f	worklog_missing_mgr-2-2026-10-29	2026-10-30 10:14:26.764911+00	\N	1	\N	DELIVERED
39	3	Missing work log	No work log for 2026-10-29. Please add entries for that day.	WORKLOG	f	worklog_missing-3-2026-10-29	2026-10-30 10:14:26.81037+00	\N	1	\N	DELIVERED
41	4	Missing work log	No work log for 2026-10-29. Please add entries for that day.	WORKLOG	f	worklog_missing-4-2026-10-29	2026-10-30 10:14:26.948569+00	\N	1	\N	DELIVERED
43	5	Missing work log	No work log for 2026-10-29. Please add entries for that day.	WORKLOG	f	worklog_missing-5-2026-10-29	2026-10-30 10:14:27.11548+00	\N	1	\N	DELIVERED
45	12	Missing work log	No work log for 2026-10-29. Please add entries for that day.	WORKLOG	f	worklog_missing-12-2026-10-29	2026-10-30 10:14:27.209648+00	\N	1	\N	DELIVERED
46	10	Team member missing work log	Farhana Begum has no work log for 2026-10-29	WORKLOG	f	worklog_missing_mgr-12-2026-10-29	2026-10-30 10:14:27.258562+00	\N	1	\N	DELIVERED
48	2	Missing work log	No work log for 2026-10-28. Please add entries for that day.	WORKLOG	f	worklog_missing-2-2026-10-28	2026-10-30 10:14:27.447407+00	\N	1	\N	DELIVERED
49	3	Team member missing work log	Nadia Chowdhury has no work log for 2026-10-28	WORKLOG	f	worklog_missing_mgr-2-2026-10-28	2026-10-30 10:14:27.486366+00	\N	1	\N	DELIVERED
47	1	Missing work log	No work log for 2026-10-29. Please add entries for that day.	WORKLOG	t	worklog_missing-1-2026-10-29	2026-10-30 10:14:27.392977+00	\N	1	\N	DELIVERED
44	1	Team member missing work log	Marcus Blake has no work log for 2026-10-29	WORKLOG	t	worklog_missing_mgr-5-2026-10-29	2026-10-30 10:14:27.153959+00	\N	1	\N	DELIVERED
40	1	Team member missing work log	Rafiul Islam has no work log for 2026-10-29	WORKLOG	t	worklog_missing_mgr-3-2026-10-29	2026-10-30 10:14:26.872891+00	\N	1	\N	DELIVERED
42	1	Team member missing work log	Lina Ahmed has no work log for 2026-10-29	WORKLOG	t	worklog_missing_mgr-4-2026-10-29	2026-10-30 10:14:26.993461+00	\N	1	\N	DELIVERED
3	1	Smoke	Hello from self	SYSTEM	t	\N	2026-10-29 12:39:52.473686+00	\N	1	\N	DELIVERED
32	14	Leave APPROVED	Leave application #4 is now APPROVED	LEAVE	t	leave-4-APPROVED	2026-10-30 09:31:56.89416+00	\N	1	\N	DELIVERED
50	3	Missing work log	No work log for 2026-10-28. Please add entries for that day.	WORKLOG	f	worklog_missing-3-2026-10-28	2026-10-30 10:14:27.521885+00	\N	1	\N	DELIVERED
52	4	Missing work log	No work log for 2026-10-28. Please add entries for that day.	WORKLOG	f	worklog_missing-4-2026-10-28	2026-10-30 10:14:27.603732+00	\N	1	\N	DELIVERED
54	5	Missing work log	No work log for 2026-10-28. Please add entries for that day.	WORKLOG	f	worklog_missing-5-2026-10-28	2026-10-30 10:14:27.660997+00	\N	1	\N	DELIVERED
56	12	Missing work log	No work log for 2026-10-28. Please add entries for that day.	WORKLOG	f	worklog_missing-12-2026-10-28	2026-10-30 10:14:27.738344+00	\N	1	\N	DELIVERED
57	10	Team member missing work log	Farhana Begum has no work log for 2026-10-28	WORKLOG	f	worklog_missing_mgr-12-2026-10-28	2026-10-30 10:14:27.757762+00	\N	1	\N	DELIVERED
59	3	Team member missing work log	Nadia Chowdhury has no work log for 2026-10-27	WORKLOG	f	worklog_missing_mgr-2-2026-10-27	2026-10-30 10:14:27.889839+00	\N	1	\N	DELIVERED
60	3	Missing work log	No work log for 2026-10-27. Please add entries for that day.	WORKLOG	f	worklog_missing-3-2026-10-27	2026-10-30 10:14:27.917378+00	\N	1	\N	DELIVERED
62	4	Missing work log	No work log for 2026-10-27. Please add entries for that day.	WORKLOG	f	worklog_missing-4-2026-10-27	2026-10-30 10:14:27.989346+00	\N	1	\N	DELIVERED
64	5	Missing work log	No work log for 2026-10-27. Please add entries for that day.	WORKLOG	f	worklog_missing-5-2026-10-27	2026-10-30 10:14:28.033972+00	\N	1	\N	DELIVERED
66	12	Missing work log	No work log for 2026-10-27. Please add entries for that day.	WORKLOG	f	worklog_missing-12-2026-10-27	2026-10-30 10:14:28.073667+00	\N	1	\N	DELIVERED
67	10	Team member missing work log	Farhana Begum has no work log for 2026-10-27	WORKLOG	f	worklog_missing_mgr-12-2026-10-27	2026-10-30 10:14:28.094834+00	\N	1	\N	DELIVERED
68	6	Missing work log	No work log for 2026-10-27. Please add entries for that day.	WORKLOG	f	worklog_missing-6-2026-10-27	2026-10-30 10:14:28.123432+00	\N	1	\N	DELIVERED
69	4	Team member missing work log	Tania Sultana has no work log for 2026-10-27	WORKLOG	f	worklog_missing_mgr-6-2026-10-27	2026-10-30 10:14:28.140106+00	\N	1	\N	DELIVERED
70	7	Missing work log	No work log for 2026-10-27. Please add entries for that day.	WORKLOG	f	worklog_missing-7-2026-10-27	2026-10-30 10:14:28.16178+00	\N	1	\N	DELIVERED
71	4	Team member missing work log	Imran Hossain has no work log for 2026-10-27	WORKLOG	f	worklog_missing_mgr-7-2026-10-27	2026-10-30 10:14:28.187557+00	\N	1	\N	DELIVERED
72	10	Missing work log	No work log for 2026-10-27. Please add entries for that day.	WORKLOG	f	worklog_missing-10-2026-10-27	2026-10-30 10:14:28.215625+00	\N	1	\N	DELIVERED
73	4	Team member missing work log	Priya Das has no work log for 2026-10-27	WORKLOG	f	worklog_missing_mgr-10-2026-10-27	2026-10-30 10:14:28.245776+00	\N	1	\N	DELIVERED
75	5	Team member missing work log	Sophie Nguyen has no work log for 2026-10-27	WORKLOG	f	worklog_missing_mgr-8-2026-10-27	2026-10-30 10:14:28.290772+00	\N	1	\N	DELIVERED
77	5	Team member missing work log	Owen Fraser has no work log for 2026-10-27	WORKLOG	f	worklog_missing_mgr-11-2026-10-27	2026-10-30 10:14:28.326575+00	\N	1	\N	DELIVERED
78	9	Missing work log	No work log for 2026-10-27. Please add entries for that day.	WORKLOG	f	worklog_missing-9-2026-10-27	2026-10-30 10:14:28.367566+00	\N	1	\N	DELIVERED
80	12	Leave PENDING_APPROVAL	Leave application #3 is now PENDING_APPROVAL	LEAVE	f	leave-3-PENDING_APPROVAL	2026-10-30 10:29:47.485934+00	\N	1	\N	DELIVERED
65	1	Team member missing work log	Marcus Blake has no work log for 2026-10-27	WORKLOG	t	worklog_missing_mgr-5-2026-10-27	2026-10-30 10:14:28.051363+00	\N	1	\N	DELIVERED
63	1	Team member missing work log	Lina Ahmed has no work log for 2026-10-27	WORKLOG	t	worklog_missing_mgr-4-2026-10-27	2026-10-30 10:14:28.008407+00	\N	1	\N	DELIVERED
61	1	Team member missing work log	Rafiul Islam has no work log for 2026-10-27	WORKLOG	t	worklog_missing_mgr-3-2026-10-27	2026-10-30 10:14:27.935589+00	\N	1	\N	DELIVERED
55	1	Team member missing work log	Marcus Blake has no work log for 2026-10-28	WORKLOG	t	worklog_missing_mgr-5-2026-10-28	2026-10-30 10:14:27.703117+00	\N	1	\N	DELIVERED
51	1	Team member missing work log	Rafiul Islam has no work log for 2026-10-28	WORKLOG	t	worklog_missing_mgr-3-2026-10-28	2026-10-30 10:14:27.562345+00	\N	1	\N	DELIVERED
53	1	Team member missing work log	Lina Ahmed has no work log for 2026-10-28	WORKLOG	t	worklog_missing_mgr-4-2026-10-28	2026-10-30 10:14:27.627411+00	\N	1	\N	DELIVERED
81	6	Leave rejected	Leave application #7 is now REJECTED	LEAVE	f	leave-7-REJECTED	2026-10-30 11:05:24.628996+00	\N	1	\N	DELIVERED
82	6	Leave PENDING_APPROVAL	Leave application #8 is now PENDING_APPROVAL	LEAVE	f	leave-8-PENDING_APPROVAL	2026-10-30 11:05:24.842215+00	\N	1	\N	DELIVERED
83	6	Leave APPROVED	Leave application #8 is now APPROVED	LEAVE	f	leave-8-APPROVED	2026-10-30 11:05:24.91843+00	\N	1	\N	DELIVERED
84	6	Leave rejected	Leave application #13 is now REJECTED	LEAVE	f	leave-13-REJECTED	2026-10-30 11:07:51.50852+00	\N	1	\N	DELIVERED
85	6	Leave PENDING_APPROVAL	Leave application #14 is now PENDING_APPROVAL	LEAVE	f	leave-14-PENDING_APPROVAL	2026-10-30 11:07:51.695458+00	\N	1	\N	DELIVERED
86	6	Leave APPROVED	Leave application #14 is now APPROVED	LEAVE	f	leave-14-APPROVED	2026-10-30 11:07:51.787105+00	\N	1	\N	DELIVERED
87	6	Leave PENDING_CANCEL	Leave application #14 is now PENDING_CANCEL	LEAVE	f	leave-14-PENDING_CANCEL	2026-10-30 11:07:51.917896+00	\N	1	\N	DELIVERED
88	6	Leave CANCELLED	Leave application #14 is now CANCELLED	LEAVE	f	leave-14-CANCELLED	2026-10-30 11:07:51.966276+00	\N	1	\N	DELIVERED
89	6	Leave rejected	Leave application #19 is now REJECTED	LEAVE	f	leave-19-REJECTED	2026-10-30 11:08:15.544414+00	\N	1	\N	DELIVERED
90	6	Leave PENDING_APPROVAL	Leave application #20 is now PENDING_APPROVAL	LEAVE	f	leave-20-PENDING_APPROVAL	2026-10-30 11:08:15.981112+00	\N	1	\N	DELIVERED
91	6	Leave APPROVED	Leave application #20 is now APPROVED	LEAVE	f	leave-20-APPROVED	2026-10-30 11:08:16.12936+00	\N	1	\N	DELIVERED
92	6	Leave PENDING_CANCEL	Leave application #20 is now PENDING_CANCEL	LEAVE	f	leave-20-PENDING_CANCEL	2026-10-30 11:08:16.313432+00	\N	1	\N	DELIVERED
76	11	Missing work log	No work log for 2026-10-27. Please add entries for that day.	WORKLOG	t	worklog_missing-11-2026-10-27	2026-10-30 10:14:28.309923+00	\N	1	\N	DELIVERED
79	2	Team member missing work log	Karim Ali has no work log for 2026-10-27	WORKLOG	t	worklog_missing_mgr-9-2026-10-27	2026-10-30 10:14:28.400082+00	\N	1	\N	DELIVERED
58	2	Missing work log	No work log for 2026-10-27. Please add entries for that day.	WORKLOG	t	worklog_missing-2-2026-10-27	2026-10-30 10:14:27.870583+00	\N	1	\N	DELIVERED
93	6	Leave CANCELLED	Leave application #20 is now CANCELLED	LEAVE	f	leave-20-CANCELLED	2026-10-30 11:08:16.437252+00	\N	1	\N	DELIVERED
95	1	Idle approval	Waiting	LEAVE	t	idle-test-link-1793364001	2026-10-30 12:40:02.140514+00	/approvals	1	\N	DELIVERED
96	6	Leave PENDING_APPROVAL	Leave application #16 is now PENDING_APPROVAL	LEAVE	f	leave-16-PENDING_APPROVAL	2026-10-30 12:45:17.399796+00	/leave	1	\N	DELIVERED
126	6	Leave APPROVED	Leave application #30 is now APPROVED	LEAVE	f	leave-30-APPROVED	2026-11-03 11:56:42.900731+00	/leave	1	\N	DELIVERED
127	4	Leave cancellation approval needed	Tania Sultana requested cancellation of leave #30	LEAVE	f	leave-cancel-30-4	2026-11-03 11:56:43.08123+00	/approvals	1	\N	DELIVERED
94	1	Leave approved	Your leave is approved	LEAVE	t	test-link-1793364001	2026-10-30 12:40:01.854603+00	/leave	1	\N	DELIVERED
31	14	Leave PENDING_APPROVAL	Leave application #4 is now PENDING_APPROVAL	LEAVE	t	leave-4-PENDING_APPROVAL	2026-10-30 09:30:59.636035+00	\N	1	\N	DELIVERED
74	8	Missing work log	No work log for 2026-10-27. Please add entries for that day.	WORKLOG	t	worklog_missing-8-2026-10-27	2026-10-30 10:14:28.270958+00	\N	1	\N	DELIVERED
97	5	Leave approval needed	Sophie Nguyen submitted leave application #23	LEAVE	f	leave-submitted-23-5	2026-11-03 06:32:13.612162+00	/approvals	1	\N	DELIVERED
98	8	Leave APPROVED	Leave application #23 is now APPROVED	LEAVE	t	leave-23-APPROVED	2026-11-03 06:32:37.061198+00	/leave	1	\N	DELIVERED
100	8	Leave PENDING_APPROVAL	Leave application #24 is now PENDING_APPROVAL	LEAVE	t	leave-24-PENDING_APPROVAL	2026-11-03 09:24:59.247631+00	/leave	1	\N	DELIVERED
101	8	Leave APPROVED	Leave application #24 is now APPROVED	LEAVE	t	leave-24-APPROVED	2026-11-03 09:25:32.363563+00	/leave	1	\N	DELIVERED
99	5	Leave approval needed	Sophie Nguyen submitted leave application #24	LEAVE	t	leave-submitted-24-5	2026-11-03 09:24:45.240175+00	/approvals	1	\N	DELIVERED
103	8	Leave cancellation approved	Leave application #24 is now CANCELLED	LEAVE	t	leave-24-CANCELLED	2026-11-03 10:06:34.009077+00	/leave	1	\N	DELIVERED
104	6	Leave cancellation still pending approval	Leave application #8 is now PENDING_CANCEL	LEAVE	f	leave-8-PENDING_CANCEL	2026-11-03 11:53:54.360887+00	/leave	1	\N	DELIVERED
105	6	Leave cancellation approved	Leave application #8 is now CANCELLED	LEAVE	f	leave-8-CANCELLED	2026-11-03 11:53:54.96937+00	/leave	1	\N	DELIVERED
106	6	Leave APPROVED	Leave application #1 is now APPROVED	LEAVE	f	leave-1-APPROVED	2026-11-03 11:56:02.702578+00	/leave	1	\N	DELIVERED
107	4	Leave approval needed	Tania Sultana submitted leave application #25	LEAVE	f	leave-submitted-25-4	2026-11-03 11:56:03.210765+00	/approvals	1	\N	DELIVERED
108	6	Leave APPROVED	Leave application #25 is now APPROVED	LEAVE	f	leave-25-APPROVED	2026-11-03 11:56:03.927324+00	/leave	1	\N	DELIVERED
109	4	Leave cancellation approval needed	Tania Sultana requested cancellation of leave #25	LEAVE	f	leave-cancel-25-4	2026-11-03 11:56:04.152995+00	/approvals	1	\N	DELIVERED
110	6	Leave cancellation approved	Leave application #25 is now CANCELLED	LEAVE	f	leave-25-CANCELLED	2026-11-03 11:56:05.469317+00	/leave	1	\N	DELIVERED
111	4	Leave approval needed	Tania Sultana submitted leave application #26	LEAVE	f	leave-submitted-26-4	2026-11-03 11:56:05.70723+00	/approvals	1	\N	DELIVERED
112	6	Leave still pending approval	Leave application #26 is now PENDING_APPROVAL	LEAVE	f	leave-26-PENDING_APPROVAL	2026-11-03 11:56:06.215542+00	/leave	1	\N	DELIVERED
113	6	Leave APPROVED	Leave application #26 is now APPROVED	LEAVE	f	leave-26-APPROVED	2026-11-03 11:56:06.738883+00	/leave	1	\N	DELIVERED
114	4	Leave cancellation approval needed	Tania Sultana requested cancellation of leave #26	LEAVE	f	leave-cancel-26-4	2026-11-03 11:56:06.94827+00	/approvals	1	\N	DELIVERED
115	6	Leave cancellation still pending approval	Leave application #26 is now PENDING_CANCEL	LEAVE	f	leave-26-PENDING_CANCEL	2026-11-03 11:56:07.667682+00	/leave	1	\N	DELIVERED
116	6	Leave cancellation approved	Leave application #26 is now CANCELLED	LEAVE	f	leave-26-CANCELLED	2026-11-03 11:56:07.887245+00	/leave	1	\N	DELIVERED
117	4	Leave approval needed	Tania Sultana submitted leave application #27	LEAVE	f	leave-submitted-27-4	2026-11-03 11:56:08.104063+00	/approvals	1	\N	DELIVERED
118	4	Leave approval needed	Tania Sultana submitted leave application #28	LEAVE	f	leave-submitted-28-4	2026-11-03 11:56:08.524542+00	/approvals	1	\N	DELIVERED
119	6	Leave rejected	Leave application #28 is now REJECTED	LEAVE	f	leave-28-REJECTED	2026-11-03 11:56:08.738081+00	/leave	1	\N	DELIVERED
120	6	Leave APPROVED	Leave application #16 is now APPROVED	LEAVE	f	leave-16-APPROVED	2026-11-03 11:56:09.376083+00	/leave	1	\N	DELIVERED
121	4	Leave approval needed	Tania Sultana submitted leave application #29	LEAVE	f	leave-submitted-29-4	2026-11-03 11:56:39.793308+00	/approvals	1	\N	DELIVERED
122	4	Leave cancellation approval needed	Tania Sultana requested cancellation of leave #29	LEAVE	f	leave-cancel-29-4	2026-11-03 11:56:40.56534+00	/approvals	1	\N	DELIVERED
123	6	Leave cancellation approved	Leave application #29 is now CANCELLED	LEAVE	f	leave-29-CANCELLED	2026-11-03 11:56:41.619521+00	/leave	1	\N	DELIVERED
124	4	Leave approval needed	Tania Sultana submitted leave application #30	LEAVE	f	leave-submitted-30-4	2026-11-03 11:56:41.913197+00	/approvals	1	\N	DELIVERED
125	6	Leave still pending approval	Leave application #30 is now PENDING_APPROVAL	LEAVE	f	leave-30-PENDING_APPROVAL	2026-11-03 11:56:42.419243+00	/leave	1	\N	DELIVERED
128	6	Leave cancellation still pending approval	Leave application #30 is now PENDING_CANCEL	LEAVE	f	leave-30-PENDING_CANCEL	2026-11-03 11:56:43.484589+00	/leave	1	\N	DELIVERED
129	6	Leave cancellation approved	Leave application #30 is now CANCELLED	LEAVE	f	leave-30-CANCELLED	2026-11-03 11:56:43.746254+00	/leave	1	\N	DELIVERED
130	4	Leave approval needed	Tania Sultana submitted leave application #31	LEAVE	f	leave-submitted-31-4	2026-11-03 11:56:44.277936+00	/approvals	1	\N	DELIVERED
131	6	Leave rejected	Leave application #31 is now REJECTED	LEAVE	f	leave-31-REJECTED	2026-11-03 11:56:44.512479+00	/leave	1	\N	DELIVERED
132	4	Leave approval needed	Tania Sultana submitted leave application #32	LEAVE	f	leave-submitted-32-4	2026-11-03 11:57:16.353154+00	/approvals	1	\N	DELIVERED
133	6	Leave APPROVED	Leave application #32 is now APPROVED	LEAVE	f	leave-32-APPROVED	2026-11-03 11:57:16.989196+00	/leave	1	\N	DELIVERED
134	4	Leave cancellation approval needed	Tania Sultana requested cancellation of leave #32	LEAVE	f	leave-cancel-32-4	2026-11-03 11:57:17.173418+00	/approvals	1	\N	DELIVERED
135	6	Leave cancellation approved	Leave application #32 is now CANCELLED	LEAVE	f	leave-32-CANCELLED	2026-11-03 11:57:18.268049+00	/leave	1	\N	DELIVERED
136	4	Leave approval needed	Tania Sultana submitted leave application #33	LEAVE	f	leave-submitted-33-4	2026-11-03 11:57:18.486415+00	/approvals	1	\N	DELIVERED
137	4	Leave cancellation approval needed	Tania Sultana requested cancellation of leave #33	LEAVE	f	leave-cancel-33-4	2026-11-03 11:57:19.548975+00	/approvals	1	\N	DELIVERED
138	6	Leave cancellation still pending approval	Leave application #33 is now PENDING_CANCEL	LEAVE	f	leave-33-PENDING_CANCEL	2026-11-03 11:57:20.001073+00	/leave	1	\N	DELIVERED
139	6	Leave cancellation approved	Leave application #33 is now CANCELLED	LEAVE	f	leave-33-CANCELLED	2026-11-03 11:57:20.219569+00	/leave	1	\N	DELIVERED
140	4	Leave approval needed	Tania Sultana submitted leave application #34	LEAVE	f	leave-submitted-34-4	2026-11-03 11:57:20.511185+00	/approvals	1	\N	DELIVERED
141	4	Leave approval needed	Tania Sultana submitted leave application #35	LEAVE	f	leave-submitted-35-4	2026-11-03 11:57:20.882174+00	/approvals	1	\N	DELIVERED
102	8	Leave cancellation still pending approval	Leave application #24 is now PENDING_CANCEL	LEAVE	t	leave-24-PENDING_CANCEL	2026-11-03 10:05:56.352858+00	/leave	1	\N	DELIVERED
143	6	Payslip ready	Your payslip for 2026-06 has been finalized — net BDT 62045.4500.	PAYROLL	f	payslip-final-55	2026-11-04 09:34:56.561087+00	/payroll	1	\N	DELIVERED
144	10	Payslip ready	Your payslip for 2026-10 has been finalized — net BDT 45000.0000.	PAYROLL	t	payslip-final-34	2026-11-04 09:35:46.539308+00	/payroll	1	\N	DELIVERED
142	12	Leave APPROVED	Leave application #3 is now APPROVED	LEAVE	t	leave-3-APPROVED	2026-11-04 07:10:45.407771+00	/leave	1	\N	DELIVERED
145	7	Payslip ready	Your payslip for 2026-10 has been finalized — net BDT 60000.0000.	PAYROLL	f	payslip-final-32	2026-11-04 11:51:38.879999+00	/payroll	1	\N	DELIVERED
146	8	Payslip ready	Your payslip for 2026-10 has been finalized — net AUD 71800.0000.	PAYROLL	f	payslip-final-33	2026-11-08 19:00:42.758251+00	/payroll	1	2026-11-08 19:00:42.716406+00	DELIVERED
\.


ALTER TABLE public.notifications ENABLE TRIGGER ALL;

--
-- Name: notification_audit_log_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.notification_audit_log_id_seq', 1, true);


--
-- Name: notifications_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.notifications_id_seq', 146, true);


--
-- PostgreSQL database dump complete
--

\unrestrict mohdut2SefFjT8K5DOepqoPLaUlqrciirRuVVYH3ztxeDQ3rDQg8nJ062exgUPg

