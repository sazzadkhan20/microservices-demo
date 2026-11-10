--
-- PostgreSQL database dump
--

\restrict a7iN2w5zxUzchB7FGj6BazIIYscl8jODfXCooZfUyhDi0LYxd20rSIJgggwnoHm

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
2	2026-11-09 09:15:11.392028+00	2	CREATE	Notification	147	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
3	2026-11-09 13:34:34.544204+00	10	CREATE	Notification	148	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
4	2026-11-09 13:34:49.91859+00	10	MARK_READ	Notification	148	false	true	\N
5	2026-11-09 13:35:15.358559+00	10	MARK_READ	Notification	72	false	true	\N
6	2026-11-10 01:00:24.108777+00	4	MARK_READ	Notification	141	false	true	\N
7	2026-11-10 01:05:15.919274+00	16	CREATE	Notification	149	\N	{"title": "Missing work log QA", "category": "WORKLOG"}	\N
8	2026-11-10 01:07:40.763895+00	16	CREATE	Notification	150	\N	{"title": "Missing work log", "category": "WORKLOG"}	\N
9	2026-11-10 01:07:40.824474+00	1	CREATE	Notification	151	\N	{"title": "Team member missing work log", "category": "WORKLOG"}	\N
10	2026-11-10 01:07:42.189218+00	8	CREATE	Notification	152	\N	{"title": "Missing work log", "category": "WORKLOG"}	\N
11	2026-11-10 01:07:42.235702+00	5	CREATE	Notification	153	\N	{"title": "Team member missing work log", "category": "WORKLOG"}	\N
12	2026-11-10 01:07:42.30245+00	1	CREATE	Notification	154	\N	{"title": "Missing work log", "category": "WORKLOG"}	\N
13	2026-11-10 01:07:42.361074+00	6	CREATE	Notification	155	\N	{"title": "Missing work log", "category": "WORKLOG"}	\N
14	2026-11-10 01:07:42.418652+00	4	CREATE	Notification	156	\N	{"title": "Team member missing work log", "category": "WORKLOG"}	\N
15	2026-11-10 01:07:42.567797+00	8	CREATE	Notification	157	\N	{"title": "Missing work log", "category": "WORKLOG"}	\N
16	2026-11-10 01:07:42.603372+00	5	CREATE	Notification	158	\N	{"title": "Team member missing work log", "category": "WORKLOG"}	\N
17	2026-11-10 01:08:09.942355+00	6	MARK_READ	Notification	155	false	true	\N
18	2026-11-10 01:08:21.113003+00	4	MARK_READ	Notification	156	false	true	\N
19	2026-11-10 07:20:19.032447+00	4	CREATE	Notification	159	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
20	2026-11-10 07:20:19.21439+00	6	CREATE	Notification	160	\N	{"title": "Leave APPROVED", "category": "LEAVE"}	\N
21	2026-11-10 07:20:19.285316+00	4	CREATE	Notification	161	\N	{"title": "Leave cancellation approval needed", "category": "LEAVE"}	\N
22	2026-11-10 07:20:19.341739+00	6	CREATE	Notification	162	\N	{"title": "Leave cancellation approved", "category": "LEAVE"}	\N
23	2026-11-10 07:20:36.571842+00	4	CREATE	Notification	163	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
24	2026-11-10 07:23:15.538964+00	4	CREATE	Notification	164	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
25	2026-11-10 07:23:15.768109+00	4	CREATE	Notification	165	\N	{"title": "Leave cancellation approval needed", "category": "LEAVE"}	\N
26	2026-11-10 07:23:15.871722+00	6	CREATE	Notification	166	\N	{"title": "Leave cancellation approved", "category": "LEAVE"}	\N
27	2026-11-10 07:23:16.005953+00	4	CREATE	Notification	167	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
28	2026-11-10 07:23:16.058841+00	6	CREATE	Notification	168	\N	{"title": "Leave rejected", "category": "LEAVE"}	\N
29	2026-11-10 07:23:16.278629+00	4	CREATE	Notification	169	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
30	2026-11-10 07:24:01.17416+00	4	CREATE	Notification	170	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
31	2026-11-10 07:24:01.552107+00	6	CREATE	Notification	171	\N	{"title": "Leave APPROVED", "category": "LEAVE"}	\N
32	2026-11-10 07:24:01.688302+00	4	CREATE	Notification	172	\N	{"title": "Leave cancellation approval needed", "category": "LEAVE"}	\N
33	2026-11-10 07:24:01.8331+00	6	CREATE	Notification	173	\N	{"title": "Leave cancellation approved", "category": "LEAVE"}	\N
34	2026-11-10 07:24:02.080554+00	4	CREATE	Notification	174	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
35	2026-11-10 07:24:02.210337+00	6	CREATE	Notification	175	\N	{"title": "Leave rejected", "category": "LEAVE"}	\N
36	2026-11-10 07:24:02.842112+00	4	CREATE	Notification	176	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
37	2026-11-10 07:24:48.797651+00	4	CREATE	Notification	177	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
38	2026-11-10 07:24:48.891626+00	6	CREATE	Notification	178	\N	{"title": "Leave APPROVED", "category": "LEAVE"}	\N
39	2026-11-10 07:24:48.95106+00	4	CREATE	Notification	179	\N	{"title": "Leave cancellation approval needed", "category": "LEAVE"}	\N
40	2026-11-10 07:24:49.0058+00	6	CREATE	Notification	180	\N	{"title": "Leave cancellation approved", "category": "LEAVE"}	\N
41	2026-11-10 07:24:49.125011+00	4	CREATE	Notification	181	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
42	2026-11-10 07:24:49.195162+00	6	CREATE	Notification	182	\N	{"title": "Leave rejected", "category": "LEAVE"}	\N
43	2026-11-10 07:24:49.416394+00	4	CREATE	Notification	183	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
44	2026-11-10 08:22:12.546362+00	4	CREATE	Notification	184	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
45	2026-11-10 08:22:12.799186+00	6	CREATE	Notification	185	\N	{"title": "Leave APPROVED", "category": "LEAVE"}	\N
46	2026-11-10 08:22:12.879387+00	4	CREATE	Notification	186	\N	{"title": "Leave cancellation approval needed", "category": "LEAVE"}	\N
47	2026-11-10 08:22:12.984495+00	6	CREATE	Notification	187	\N	{"title": "Leave cancellation approved", "category": "LEAVE"}	\N
48	2026-11-10 08:22:13.15727+00	4	CREATE	Notification	188	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
49	2026-11-10 08:22:13.219629+00	6	CREATE	Notification	189	\N	{"title": "Leave rejected", "category": "LEAVE"}	\N
50	2026-11-10 08:22:13.459608+00	4	CREATE	Notification	190	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
51	2026-11-10 08:29:58.858712+00	1	CREATE	Notification	191	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
52	2026-11-10 08:36:58.579164+00	4	CREATE	Notification	192	\N	{"title": "Leave APPROVED", "category": "LEAVE"}	\N
53	2026-11-10 08:37:16.229644+00	4	MARK_READ	Notification	192	false	true	\N
54	2026-11-10 08:40:00.083053+00	1	CREATE	Notification	193	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
55	2026-11-10 08:40:02.28861+00	4	CREATE	Notification	194	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
56	2026-11-10 08:40:02.403066+00	6	CREATE	Notification	195	\N	{"title": "Leave APPROVED", "category": "LEAVE"}	\N
57	2026-11-10 08:40:02.472966+00	4	CREATE	Notification	196	\N	{"title": "Leave cancellation approval needed", "category": "LEAVE"}	\N
58	2026-11-10 08:40:02.564857+00	6	CREATE	Notification	197	\N	{"title": "Leave cancellation approved", "category": "LEAVE"}	\N
60	2026-11-10 08:40:02.798619+00	6	CREATE	Notification	199	\N	{"title": "Leave rejected", "category": "LEAVE"}	\N
61	2026-11-10 08:40:03.094696+00	4	CREATE	Notification	200	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
62	2026-11-10 08:42:07.270793+00	1	CREATE	Notification	201	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
63	2026-11-10 08:44:24.270514+00	1	CREATE	Notification	202	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
65	2026-11-10 08:45:26.020329+00	1	CREATE	Notification	204	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
59	2026-11-10 08:40:02.716264+00	4	CREATE	Notification	198	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
64	2026-11-10 08:44:32.857494+00	1	CREATE	Notification	203	\N	{"title": "Leave approval needed", "category": "LEAVE"}	\N
66	2026-11-10 10:58:41.804519+00	31	CREATE	Notification	205	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
67	2026-11-10 10:58:41.876942+00	1	CREATE	Notification	206	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
68	2026-11-10 10:58:41.905944+00	12	CREATE	Notification	207	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
69	2026-11-10 10:58:41.937735+00	7	CREATE	Notification	208	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
70	2026-11-10 10:58:41.97634+00	9	CREATE	Notification	209	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
71	2026-11-10 10:58:42.017087+00	4	CREATE	Notification	210	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
72	2026-11-10 10:58:42.046018+00	5	CREATE	Notification	211	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
73	2026-11-10 10:58:42.07523+00	2	CREATE	Notification	212	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
74	2026-11-10 10:58:42.111991+00	11	CREATE	Notification	213	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
75	2026-11-10 10:58:42.145144+00	10	CREATE	Notification	214	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
76	2026-11-10 10:58:42.189468+00	3	CREATE	Notification	215	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
77	2026-11-10 10:58:42.21634+00	30	CREATE	Notification	216	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
78	2026-11-10 10:58:42.251406+00	8	CREATE	Notification	217	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
79	2026-11-10 10:58:42.276227+00	6	CREATE	Notification	218	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
80	2026-11-10 10:58:42.304651+00	29	CREATE	Notification	219	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
81	2026-11-10 10:58:44.748197+00	31	CREATE	Notification	220	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
82	2026-11-10 10:58:44.777399+00	1	CREATE	Notification	221	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
83	2026-11-10 10:58:44.80215+00	12	CREATE	Notification	222	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
84	2026-11-10 10:58:44.847433+00	7	CREATE	Notification	223	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
85	2026-11-10 10:58:44.874537+00	9	CREATE	Notification	224	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
86	2026-11-10 10:58:44.915232+00	4	CREATE	Notification	225	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
87	2026-11-10 10:58:44.938281+00	5	CREATE	Notification	226	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
88	2026-11-10 10:58:44.971522+00	2	CREATE	Notification	227	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
89	2026-11-10 10:58:45.000831+00	11	CREATE	Notification	228	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
90	2026-11-10 10:58:45.019776+00	10	CREATE	Notification	229	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
91	2026-11-10 10:58:45.03822+00	3	CREATE	Notification	230	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
92	2026-11-10 10:58:45.058135+00	30	CREATE	Notification	231	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
93	2026-11-10 10:58:45.074731+00	8	CREATE	Notification	232	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
94	2026-11-10 10:58:45.101188+00	6	CREATE	Notification	233	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
95	2026-11-10 10:58:45.126299+00	29	CREATE	Notification	234	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
96	2026-11-10 10:58:48.154471+00	14	CREATE	Notification	235	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
97	2026-11-10 10:58:48.181313+00	31	CREATE	Notification	236	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
98	2026-11-10 10:58:48.212737+00	1	CREATE	Notification	237	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
99	2026-11-10 10:58:48.24191+00	12	CREATE	Notification	238	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
100	2026-11-10 10:58:48.286359+00	7	CREATE	Notification	239	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
101	2026-11-10 10:58:48.327189+00	9	CREATE	Notification	240	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
102	2026-11-10 10:58:48.354002+00	4	CREATE	Notification	241	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
103	2026-11-10 10:58:48.387545+00	5	CREATE	Notification	242	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
104	2026-11-10 10:58:48.414901+00	2	CREATE	Notification	243	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
105	2026-11-10 10:58:48.438656+00	11	CREATE	Notification	244	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
106	2026-11-10 10:58:48.461923+00	10	CREATE	Notification	245	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
107	2026-11-10 10:58:48.488688+00	3	CREATE	Notification	246	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
108	2026-11-10 10:58:48.511805+00	30	CREATE	Notification	247	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
109	2026-11-10 10:58:48.535109+00	13	CREATE	Notification	248	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
110	2026-11-10 10:58:48.551911+00	8	CREATE	Notification	249	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
111	2026-11-10 10:58:48.570144+00	6	CREATE	Notification	250	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
112	2026-11-10 10:58:48.594996+00	29	CREATE	Notification	251	\N	{"title": "Payslip ready", "category": "PAYROLL"}	\N
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
72	10	Missing work log	No work log for 2026-10-27. Please add entries for that day.	WORKLOG	t	worklog_missing-10-2026-10-27	2026-10-30 10:14:28.215625+00	\N	1	\N	DELIVERED
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
102	8	Leave cancellation still pending approval	Leave application #24 is now PENDING_CANCEL	LEAVE	t	leave-24-PENDING_CANCEL	2026-11-03 10:05:56.352858+00	/leave	1	\N	DELIVERED
143	6	Payslip ready	Your payslip for 2026-06 has been finalized — net BDT 62045.4500.	PAYROLL	f	payslip-final-55	2026-11-04 09:34:56.561087+00	/payroll	1	\N	DELIVERED
144	10	Payslip ready	Your payslip for 2026-10 has been finalized — net BDT 45000.0000.	PAYROLL	t	payslip-final-34	2026-11-04 09:35:46.539308+00	/payroll	1	\N	DELIVERED
142	12	Leave APPROVED	Leave application #3 is now APPROVED	LEAVE	t	leave-3-APPROVED	2026-11-04 07:10:45.407771+00	/leave	1	\N	DELIVERED
145	7	Payslip ready	Your payslip for 2026-10 has been finalized — net BDT 60000.0000.	PAYROLL	f	payslip-final-32	2026-11-04 11:51:38.879999+00	/payroll	1	\N	DELIVERED
146	8	Payslip ready	Your payslip for 2026-10 has been finalized — net AUD 71800.0000.	PAYROLL	f	payslip-final-33	2026-11-08 19:00:42.758251+00	/payroll	1	2026-11-08 19:00:42.716406+00	DELIVERED
147	2	Payslip ready	Your payslip for 2026-10 has been finalized — net BDT 94800.0000.	PAYROLL	f	payslip-final-29	2026-11-09 09:15:11.222356+00	/payroll	1	2026-11-09 09:15:11.181536+00	DELIVERED
148	10	Payslip ready	Your payslip for 2026-09 has been finalized — net BDT 12272.7300.	PAYROLL	t	payslip-final-27	2026-11-09 13:34:34.488649+00	/payroll	1	2026-11-09 13:34:34.454033+00	DELIVERED
141	4	Leave approval needed	Tania Sultana submitted leave application #35	LEAVE	t	leave-submitted-35-4	2026-11-03 11:57:20.882174+00	/approvals	1	\N	DELIVERED
149	16	Missing work log QA	No work log for 2026-11-10	WORKLOG	f	worklog_missing-qa-16-2026-11-10	2026-11-10 01:05:15.87263+00	/work-log	1	2026-11-10 01:05:15.861339+00	DELIVERED
150	16	Missing work log	No work log for 2026-11-10. Please add entries for that day.	WORKLOG	f	worklog_missing-16-2026-11-10	2026-11-10 01:07:40.73718+00	/work-log	1	2026-11-10 01:07:40.731902+00	DELIVERED
151	1	Team member missing work log	With Manager Test has no work log for 2026-11-10	WORKLOG	f	worklog_missing_mgr-16-2026-11-10	2026-11-10 01:07:40.819555+00	/team/work-logs	1	2026-11-10 01:07:40.818952+00	DELIVERED
152	8	Missing work log	No work log for 2026-11-10. Please add entries for that day.	WORKLOG	f	worklog_missing-8-2026-11-10	2026-11-10 01:07:42.186165+00	/work-log	1	2026-11-10 01:07:42.186165+00	DELIVERED
153	5	Team member missing work log	Sophie Nguyen has no work log for 2026-11-10	WORKLOG	f	worklog_missing_mgr-8-2026-11-10	2026-11-10 01:07:42.23302+00	/team/work-logs	1	2026-11-10 01:07:42.23302+00	DELIVERED
154	1	Missing work log	No work log for 2026-11-10. Please add entries for that day.	WORKLOG	f	worklog_missing-1-2026-11-10	2026-11-10 01:07:42.300581+00	/work-log	1	2026-11-10 01:07:42.300026+00	DELIVERED
157	8	Missing work log	No work log for 2026-11-09. Please add entries for that day.	WORKLOG	f	worklog_missing-8-2026-11-09	2026-11-10 01:07:42.565411+00	/work-log	1	2026-11-10 01:07:42.564859+00	DELIVERED
158	5	Team member missing work log	Sophie Nguyen has no work log for 2026-11-09	WORKLOG	f	worklog_missing_mgr-8-2026-11-09	2026-11-10 01:07:42.600977+00	/team/work-logs	1	2026-11-10 01:07:42.600977+00	DELIVERED
155	6	Missing work log	No work log for 2026-11-10. Please add entries for that day.	WORKLOG	t	worklog_missing-6-2026-11-10	2026-11-10 01:07:42.358011+00	/work-log	1	2026-11-10 01:07:42.356487+00	DELIVERED
156	4	Team member missing work log	Tania Sultana has no work log for 2026-11-10	WORKLOG	t	worklog_missing_mgr-6-2026-11-10	2026-11-10 01:07:42.409901+00	/team/work-logs	1	2026-11-10 01:07:42.409901+00	DELIVERED
159	4	Leave approval needed	Tania Sultana submitted leave application #36	LEAVE	f	leave-submitted-36-4	2026-11-10 07:20:18.924474+00	/approvals	1	2026-11-10 07:20:18.856315+00	DELIVERED
160	6	Leave APPROVED	Leave application #36 is now APPROVED	LEAVE	f	leave-36-APPROVED	2026-11-10 07:20:19.212216+00	/leave	1	2026-11-10 07:20:19.211606+00	DELIVERED
161	4	Leave cancellation approval needed	Tania Sultana requested cancellation of leave #36	LEAVE	f	leave-cancel-36-4	2026-11-10 07:20:19.282902+00	/approvals	1	2026-11-10 07:20:19.282352+00	DELIVERED
162	6	Leave cancellation approved	Leave application #36 is now CANCELLED	LEAVE	f	leave-36-CANCELLED	2026-11-10 07:20:19.338977+00	/leave	1	2026-11-10 07:20:19.338977+00	DELIVERED
163	4	Leave approval needed	Tania Sultana submitted leave application #37	LEAVE	f	leave-submitted-37-4	2026-11-10 07:20:36.56481+00	/approvals	1	2026-11-10 07:20:36.56481+00	DELIVERED
164	4	Leave approval needed	Tania Sultana submitted leave application #38	LEAVE	f	leave-submitted-38-4	2026-11-10 07:23:15.526509+00	/approvals	1	2026-11-10 07:23:15.523589+00	DELIVERED
165	4	Leave cancellation approval needed	Tania Sultana requested cancellation of leave #38	LEAVE	f	leave-cancel-38-4	2026-11-10 07:23:15.764871+00	/approvals	1	2026-11-10 07:23:15.764871+00	DELIVERED
166	6	Leave cancellation approved	Leave application #38 is now CANCELLED	LEAVE	f	leave-38-CANCELLED	2026-11-10 07:23:15.869648+00	/leave	1	2026-11-10 07:23:15.869648+00	DELIVERED
167	4	Leave approval needed	Tania Sultana submitted leave application #39	LEAVE	f	leave-submitted-39-4	2026-11-10 07:23:16.003877+00	/approvals	1	2026-11-10 07:23:16.003877+00	DELIVERED
168	6	Leave rejected	Leave application #39 is now REJECTED	LEAVE	f	leave-39-REJECTED	2026-11-10 07:23:16.054685+00	/leave	1	2026-11-10 07:23:16.054685+00	DELIVERED
169	4	Leave approval needed	Tania Sultana submitted leave application #40	LEAVE	f	leave-submitted-40-4	2026-11-10 07:23:16.27683+00	/approvals	1	2026-11-10 07:23:16.27683+00	DELIVERED
170	4	Leave approval needed	Tania Sultana submitted leave application #41	LEAVE	f	leave-submitted-41-4	2026-11-10 07:24:01.164597+00	/approvals	1	2026-11-10 07:24:01.164046+00	DELIVERED
171	6	Leave APPROVED	Leave application #41 is now APPROVED	LEAVE	f	leave-41-APPROVED	2026-11-10 07:24:01.546203+00	/leave	1	2026-11-10 07:24:01.546203+00	DELIVERED
172	4	Leave cancellation approval needed	Tania Sultana requested cancellation of leave #41	LEAVE	f	leave-cancel-41-4	2026-11-10 07:24:01.684027+00	/approvals	1	2026-11-10 07:24:01.684027+00	DELIVERED
173	6	Leave cancellation approved	Leave application #41 is now CANCELLED	LEAVE	f	leave-41-CANCELLED	2026-11-10 07:24:01.827339+00	/leave	1	2026-11-10 07:24:01.827339+00	DELIVERED
174	4	Leave approval needed	Tania Sultana submitted leave application #42	LEAVE	f	leave-submitted-42-4	2026-11-10 07:24:02.073758+00	/approvals	1	2026-11-10 07:24:02.073758+00	DELIVERED
175	6	Leave rejected	Leave application #42 is now REJECTED	LEAVE	f	leave-42-REJECTED	2026-11-10 07:24:02.206881+00	/leave	1	2026-11-10 07:24:02.206248+00	DELIVERED
176	4	Leave approval needed	Tania Sultana submitted leave application #43	LEAVE	f	leave-submitted-43-4	2026-11-10 07:24:02.826723+00	/approvals	1	2026-11-10 07:24:02.826723+00	DELIVERED
177	4	Leave approval needed	Tania Sultana submitted leave application #44	LEAVE	f	leave-submitted-44-4	2026-11-10 07:24:48.792646+00	/approvals	1	2026-11-10 07:24:48.792646+00	DELIVERED
178	6	Leave APPROVED	Leave application #44 is now APPROVED	LEAVE	f	leave-44-APPROVED	2026-11-10 07:24:48.889974+00	/leave	1	2026-11-10 07:24:48.889974+00	DELIVERED
179	4	Leave cancellation approval needed	Tania Sultana requested cancellation of leave #44	LEAVE	f	leave-cancel-44-4	2026-11-10 07:24:48.948689+00	/approvals	1	2026-11-10 07:24:48.948689+00	DELIVERED
180	6	Leave cancellation approved	Leave application #44 is now CANCELLED	LEAVE	f	leave-44-CANCELLED	2026-11-10 07:24:49.003555+00	/leave	1	2026-11-10 07:24:49.003555+00	DELIVERED
181	4	Leave approval needed	Tania Sultana submitted leave application #45	LEAVE	f	leave-submitted-45-4	2026-11-10 07:24:49.123754+00	/approvals	1	2026-11-10 07:24:49.123754+00	DELIVERED
182	6	Leave rejected	Leave application #45 is now REJECTED	LEAVE	f	leave-45-REJECTED	2026-11-10 07:24:49.193504+00	/leave	1	2026-11-10 07:24:49.193504+00	DELIVERED
183	4	Leave approval needed	Tania Sultana submitted leave application #46	LEAVE	f	leave-submitted-46-4	2026-11-10 07:24:49.414674+00	/approvals	1	2026-11-10 07:24:49.414674+00	DELIVERED
184	4	Leave approval needed	Tania Sultana submitted leave application #47	LEAVE	f	leave-submitted-47-4	2026-11-10 08:22:12.489504+00	/approvals	1	2026-11-10 08:22:12.471209+00	DELIVERED
185	6	Leave APPROVED	Leave application #47 is now APPROVED	LEAVE	f	leave-47-APPROVED	2026-11-10 08:22:12.7948+00	/leave	1	2026-11-10 08:22:12.7948+00	DELIVERED
186	4	Leave cancellation approval needed	Tania Sultana requested cancellation of leave #47	LEAVE	f	leave-cancel-47-4	2026-11-10 08:22:12.87604+00	/approvals	1	2026-11-10 08:22:12.87604+00	DELIVERED
187	6	Leave cancellation approved	Leave application #47 is now CANCELLED	LEAVE	f	leave-47-CANCELLED	2026-11-10 08:22:12.980978+00	/leave	1	2026-11-10 08:22:12.958217+00	DELIVERED
188	4	Leave approval needed	Tania Sultana submitted leave application #48	LEAVE	f	leave-submitted-48-4	2026-11-10 08:22:13.153943+00	/approvals	1	2026-11-10 08:22:13.153943+00	DELIVERED
189	6	Leave rejected	Leave application #48 is now REJECTED	LEAVE	f	leave-48-REJECTED	2026-11-10 08:22:13.217029+00	/leave	1	2026-11-10 08:22:13.217029+00	DELIVERED
190	4	Leave approval needed	Tania Sultana submitted leave application #49	LEAVE	f	leave-submitted-49-4	2026-11-10 08:22:13.458027+00	/approvals	1	2026-11-10 08:22:13.456465+00	DELIVERED
191	1	Leave approval needed	Lina Ahmed submitted leave application #50	LEAVE	f	leave-submitted-50-1	2026-11-10 08:29:58.836641+00	/approvals	1	2026-11-10 08:29:58.830304+00	DELIVERED
192	4	Leave APPROVED	Leave application #50 is now APPROVED	LEAVE	t	leave-50-APPROVED	2026-11-10 08:36:58.573247+00	/leave	1	2026-11-10 08:36:58.57272+00	DELIVERED
193	1	Leave approval needed	Lina Ahmed submitted leave application #51	LEAVE	f	leave-submitted-51-1	2026-11-10 08:40:00.007428+00	/approvals	1	2026-11-10 08:39:59.998871+00	DELIVERED
194	4	Leave approval needed	Tania Sultana submitted leave application #52	LEAVE	f	leave-submitted-52-4	2026-11-10 08:40:02.283826+00	/approvals	1	2026-11-10 08:40:02.283278+00	DELIVERED
195	6	Leave APPROVED	Leave application #52 is now APPROVED	LEAVE	f	leave-52-APPROVED	2026-11-10 08:40:02.400981+00	/leave	1	2026-11-10 08:40:02.400431+00	DELIVERED
196	4	Leave cancellation approval needed	Tania Sultana requested cancellation of leave #52	LEAVE	f	leave-cancel-52-4	2026-11-10 08:40:02.47072+00	/approvals	1	2026-11-10 08:40:02.470126+00	DELIVERED
197	6	Leave cancellation approved	Leave application #52 is now CANCELLED	LEAVE	f	leave-52-CANCELLED	2026-11-10 08:40:02.560876+00	/leave	1	2026-11-10 08:40:02.560325+00	DELIVERED
198	4	Leave approval needed	Tania Sultana submitted leave application #53	LEAVE	f	leave-submitted-53-4	2026-11-10 08:40:02.713491+00	/approvals	1	2026-11-10 08:40:02.713491+00	DELIVERED
199	6	Leave rejected	Leave application #53 is now REJECTED	LEAVE	f	leave-53-REJECTED	2026-11-10 08:40:02.796803+00	/leave	1	2026-11-10 08:40:02.796803+00	DELIVERED
200	4	Leave approval needed	Tania Sultana submitted leave application #54	LEAVE	f	leave-submitted-54-4	2026-11-10 08:40:03.092123+00	/approvals	1	2026-11-10 08:40:03.091565+00	DELIVERED
201	1	Leave approval needed	With Manager Test submitted leave application #55	LEAVE	f	leave-submitted-55-1	2026-11-10 08:42:07.268313+00	/approvals	1	2026-11-10 08:42:07.268313+00	DELIVERED
202	1	Leave approval needed	With Manager Test submitted leave application #56	LEAVE	f	leave-submitted-56-1	2026-11-10 08:44:24.265679+00	/approvals	1	2026-11-10 08:44:24.265679+00	DELIVERED
203	1	Leave approval needed	With Manager Test submitted leave application #57	LEAVE	f	leave-submitted-57-1	2026-11-10 08:44:32.85382+00	/approvals	1	2026-11-10 08:44:32.85382+00	DELIVERED
204	1	Leave approval needed	With Manager Test submitted leave application #58	LEAVE	f	leave-submitted-58-1	2026-11-10 08:45:26.016659+00	/approvals	1	2026-11-10 08:45:26.016659+00	DELIVERED
205	31	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r1:31	2026-11-10 10:58:41.779397+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:41.778866+00	DELIVERED
206	1	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r1:1	2026-11-10 10:58:41.869189+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:41.869189+00	DELIVERED
207	12	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r1:12	2026-11-10 10:58:41.903278+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:41.90275+00	DELIVERED
208	7	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r1:7	2026-11-10 10:58:41.935291+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:41.934771+00	DELIVERED
209	9	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r1:9	2026-11-10 10:58:41.973671+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:41.973132+00	DELIVERED
210	4	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r1:4	2026-11-10 10:58:42.013813+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:42.013813+00	DELIVERED
211	5	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r1:5	2026-11-10 10:58:42.043327+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:42.043327+00	DELIVERED
212	2	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r1:2	2026-11-10 10:58:42.071009+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:42.071009+00	DELIVERED
213	11	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r1:11	2026-11-10 10:58:42.1082+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:42.107659+00	DELIVERED
214	10	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r1:10	2026-11-10 10:58:42.142535+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:42.142014+00	DELIVERED
215	3	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r1:3	2026-11-10 10:58:42.173513+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:42.173513+00	DELIVERED
216	30	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r1:30	2026-11-10 10:58:42.212561+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:42.212561+00	DELIVERED
217	8	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r1:8	2026-11-10 10:58:42.249199+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:42.248609+00	DELIVERED
218	6	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r1:6	2026-11-10 10:58:42.273047+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:42.273047+00	DELIVERED
219	29	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r1:29	2026-11-10 10:58:42.30098+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:42.30098+00	DELIVERED
220	31	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r3:31	2026-11-10 10:58:44.746628+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:44.746105+00	DELIVERED
221	1	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r3:1	2026-11-10 10:58:44.775811+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:44.775303+00	DELIVERED
222	12	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r3:12	2026-11-10 10:58:44.800581+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:44.800581+00	DELIVERED
223	7	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r3:7	2026-11-10 10:58:44.844241+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:44.844241+00	DELIVERED
224	9	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r3:9	2026-11-10 10:58:44.872095+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:44.872095+00	DELIVERED
225	4	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r3:4	2026-11-10 10:58:44.913081+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:44.91255+00	DELIVERED
226	5	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r3:5	2026-11-10 10:58:44.937226+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:44.936699+00	DELIVERED
227	2	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r3:2	2026-11-10 10:58:44.968303+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:44.968303+00	DELIVERED
228	11	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r3:11	2026-11-10 10:58:44.997049+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:44.996528+00	DELIVERED
229	10	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r3:10	2026-11-10 10:58:45.018609+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:45.018106+00	DELIVERED
230	3	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r3:3	2026-11-10 10:58:45.036612+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:45.036093+00	DELIVERED
231	30	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r3:30	2026-11-10 10:58:45.05501+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:45.051716+00	DELIVERED
232	8	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r3:8	2026-11-10 10:58:45.073161+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:45.073161+00	DELIVERED
233	6	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r3:6	2026-11-10 10:58:45.099618+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:45.099618+00	DELIVERED
234	29	Payslip ready	Your payslip for 2026-09 is ready.	PAYROLL	f	payslip-ready:2026-09:r3:29	2026-11-10 10:58:45.123986+00	/payroll?tab=mypay&period=2026-09	1	2026-11-10 10:58:45.123986+00	DELIVERED
235	14	Payslip ready	Your payslip for 2026-10 is ready.	PAYROLL	f	payslip-ready:2026-10:r5:14	2026-11-10 10:58:48.151766+00	/payroll?tab=mypay&period=2026-10	1	2026-11-10 10:58:48.151211+00	DELIVERED
236	31	Payslip ready	Your payslip for 2026-10 is ready.	PAYROLL	f	payslip-ready:2026-10:r5:31	2026-11-10 10:58:48.17854+00	/payroll?tab=mypay&period=2026-10	1	2026-11-10 10:58:48.17854+00	DELIVERED
237	1	Payslip ready	Your payslip for 2026-10 is ready.	PAYROLL	f	payslip-ready:2026-10:r5:1	2026-11-10 10:58:48.210496+00	/payroll?tab=mypay&period=2026-10	1	2026-11-10 10:58:48.210496+00	DELIVERED
238	12	Payslip ready	Your payslip for 2026-10 is ready.	PAYROLL	f	payslip-ready:2026-10:r5:12	2026-11-10 10:58:48.239693+00	/payroll?tab=mypay&period=2026-10	1	2026-11-10 10:58:48.239693+00	DELIVERED
239	7	Payslip ready	Your payslip for 2026-10 is ready.	PAYROLL	f	payslip-ready:2026-10:r5:7	2026-11-10 10:58:48.28476+00	/payroll?tab=mypay&period=2026-10	1	2026-11-10 10:58:48.28476+00	DELIVERED
240	9	Payslip ready	Your payslip for 2026-10 is ready.	PAYROLL	f	payslip-ready:2026-10:r5:9	2026-11-10 10:58:48.324313+00	/payroll?tab=mypay&period=2026-10	1	2026-11-10 10:58:48.323764+00	DELIVERED
241	4	Payslip ready	Your payslip for 2026-10 is ready.	PAYROLL	f	payslip-ready:2026-10:r5:4	2026-11-10 10:58:48.350702+00	/payroll?tab=mypay&period=2026-10	1	2026-11-10 10:58:48.350702+00	DELIVERED
242	5	Payslip ready	Your payslip for 2026-10 is ready.	PAYROLL	f	payslip-ready:2026-10:r5:5	2026-11-10 10:58:48.384724+00	/payroll?tab=mypay&period=2026-10	1	2026-11-10 10:58:48.384724+00	DELIVERED
243	2	Payslip ready	Your payslip for 2026-10 is ready.	PAYROLL	f	payslip-ready:2026-10:r5:2	2026-11-10 10:58:48.412242+00	/payroll?tab=mypay&period=2026-10	1	2026-11-10 10:58:48.412242+00	DELIVERED
244	11	Payslip ready	Your payslip for 2026-10 is ready.	PAYROLL	f	payslip-ready:2026-10:r5:11	2026-11-10 10:58:48.436446+00	/payroll?tab=mypay&period=2026-10	1	2026-11-10 10:58:48.436446+00	DELIVERED
245	10	Payslip ready	Your payslip for 2026-10 is ready.	PAYROLL	f	payslip-ready:2026-10:r5:10	2026-11-10 10:58:48.460352+00	/payroll?tab=mypay&period=2026-10	1	2026-11-10 10:58:48.459824+00	DELIVERED
246	3	Payslip ready	Your payslip for 2026-10 is ready.	PAYROLL	f	payslip-ready:2026-10:r5:3	2026-11-10 10:58:48.485492+00	/payroll?tab=mypay&period=2026-10	1	2026-11-10 10:58:48.485492+00	DELIVERED
247	30	Payslip ready	Your payslip for 2026-10 is ready.	PAYROLL	f	payslip-ready:2026-10:r5:30	2026-11-10 10:58:48.508949+00	/payroll?tab=mypay&period=2026-10	1	2026-11-10 10:58:48.508949+00	DELIVERED
248	13	Payslip ready	Your payslip for 2026-10 is ready.	PAYROLL	f	payslip-ready:2026-10:r5:13	2026-11-10 10:58:48.532347+00	/payroll?tab=mypay&period=2026-10	1	2026-11-10 10:58:48.532347+00	DELIVERED
249	8	Payslip ready	Your payslip for 2026-10 is ready.	PAYROLL	f	payslip-ready:2026-10:r5:8	2026-11-10 10:58:48.550292+00	/payroll?tab=mypay&period=2026-10	1	2026-11-10 10:58:48.550292+00	DELIVERED
250	6	Payslip ready	Your payslip for 2026-10 is ready.	PAYROLL	f	payslip-ready:2026-10:r5:6	2026-11-10 10:58:48.56803+00	/payroll?tab=mypay&period=2026-10	1	2026-11-10 10:58:48.56803+00	DELIVERED
251	29	Payslip ready	Your payslip for 2026-10 is ready.	PAYROLL	f	payslip-ready:2026-10:r5:29	2026-11-10 10:58:48.59283+00	/payroll?tab=mypay&period=2026-10	1	2026-11-10 10:58:48.592297+00	DELIVERED
\.


ALTER TABLE public.notifications ENABLE TRIGGER ALL;

--
-- Name: notification_audit_log_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.notification_audit_log_id_seq', 112, true);


--
-- Name: notifications_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.notifications_id_seq', 251, true);


--
-- PostgreSQL database dump complete
--

\unrestrict a7iN2w5zxUzchB7FGj6BazIIYscl8jODfXCooZfUyhDi0LYxd20rSIJgggwnoHm

