--
-- PostgreSQL database dump
--

\restrict J0vGyE6IuUUuKxP0dBfEz31lFB3c89R1tgrjBflj5CdbM1R9yLeoa3i9QfsExS5

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
-- Data for Name: leave_application; Type: TABLE DATA; Schema: public; Owner: -
--

SET SESSION AUTHORIZATION DEFAULT;

ALTER TABLE public.leave_application DISABLE TRIGGER ALL;

COPY public.leave_application (id, employee_id, start_date, end_date, working_days_counted, status, reason, union_member, notice_days_given, notice_days_required, coverage_ok, needs_relief, relief_employee_id, store_id, shift_pattern, coverage_snapshot, current_step, reject_reason, created_at, updated_at, leave_type_code, paid, balance_deducted, half_day_part, attachment_ref, team_clash_alert, warnings) FROM stdin;
11	6	2026-12-06	2026-12-06	1.00	WITHDRAWN	E2E withdraw	f	37	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-10-30 11:06:49.201112+00	2026-10-30 11:06:49.247156+00	CASUAL	t	f	FULL	\N	f	{}
12	6	2026-12-20	2026-12-20	1.00	WITHDRAWN	chain check	f	51	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-10-30 11:06:49.7036+00	2026-10-30 11:06:49.740848+00	CASUAL	t	f	FULL	\N	f	{}
13	6	2026-11-22	2026-11-22	0.50	REJECTED	E2E half day first half	f	23	7	t	f	\N	\N	\N	{"skipped": true}	1	Not enough coverage this week	2026-10-30 11:07:51.129696+00	2026-10-30 11:07:51.254632+00	ANNUAL	t	f	FIRST_HALF	\N	f	{}
22	6	2026-12-20	2026-12-20	1.00	WITHDRAWN	chain check	f	51	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-10-30 11:08:17.272239+00	2026-10-30 11:08:17.408306+00	CASUAL	t	f	FULL	\N	f	{}
14	6	2026-11-29	2026-11-29	1.00	CANCELLED	E2E approve then cancel	f	30	7	t	f	\N	\N	\N	{"skipped": true}	2	\N	2026-10-30 11:07:51.629181+00	2026-10-30 11:07:51.953777+00	ANNUAL	t	t	FULL	\N	f	{}
15	6	2026-12-06	2026-12-06	1.00	WITHDRAWN	E2E withdraw	f	37	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-10-30 11:07:52.024754+00	2026-10-30 11:07:52.072033+00	CASUAL	t	f	FULL	\N	f	{}
17	6	2026-12-20	2026-12-20	1.00	WITHDRAWN	chain check	f	51	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-10-30 11:07:52.452895+00	2026-10-30 11:07:52.4909+00	CASUAL	t	f	FULL	\N	f	{}
18	6	2026-12-21	2026-12-21	1.00	APPROVED	E2E manager-then-HR chain	f	52	7	t	f	\N	\N	\N	{"skipped": true}	2	\N	2026-10-30 11:08:12.069159+00	2026-10-30 11:08:13.249916+00	ANNUAL	t	t	FULL	\N	f	{}
19	6	2026-11-22	2026-11-22	0.50	REJECTED	E2E half day first half	f	23	7	t	f	\N	\N	\N	{"skipped": true}	1	Not enough coverage this week	2026-10-30 11:08:15.297992+00	2026-10-30 11:08:15.51965+00	ANNUAL	t	f	FIRST_HALF	\N	f	{}
20	6	2026-11-29	2026-11-29	1.00	CANCELLED	E2E approve then cancel	f	30	7	t	f	\N	\N	\N	{"skipped": true}	2	\N	2026-10-30 11:08:15.786919+00	2026-10-30 11:08:16.397004+00	ANNUAL	t	t	FULL	\N	f	{}
21	6	2026-12-06	2026-12-06	1.00	WITHDRAWN	E2E withdraw	f	37	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-10-30 11:08:16.558992+00	2026-10-30 11:08:16.655792+00	CASUAL	t	f	FULL	\N	f	{}
4	14	2026-11-02	2026-11-02	1.00	APPROVED	ewlkngengj	f	3	0	t	f	\N	\N	\N	{"skipped": true}	2	\N	2026-10-30 09:25:51.62048+00	2026-10-30 09:31:56.855908+00	SICK	t	t	FULL	\N	f	{}
2	8	2026-11-19	2026-11-21	3.00	APPROVED	Short break	f	14	7	t	f	\N	\N	\N	{}	2	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	ANNUAL	t	t	FULL	\N	f	{}
5	6	2026-12-07	2026-12-07	1.00	APPROVED	E2E manager-then-HR chain	f	38	7	t	f	\N	\N	\N	{"skipped": true}	2	\N	2026-10-30 09:48:13.975219+00	2026-10-30 09:48:14.775641+00	ANNUAL	t	t	FULL	\N	f	{}
6	6	2026-12-07	2026-12-07	1.00	APPROVED	E2E manager-then-HR chain	f	38	7	t	f	\N	\N	\N	{"skipped": true}	2	\N	2026-10-30 09:49:03.105679+00	2026-10-30 09:49:03.636306+00	ANNUAL	t	t	FULL	\N	f	{}
7	6	2026-11-22	2026-11-22	0.50	REJECTED	E2E half day first half	f	23	7	t	f	\N	\N	\N	{"skipped": true}	1	Not enough coverage this week	2026-10-30 11:05:23.818207+00	2026-10-30 11:05:23.952796+00	ANNUAL	t	f	FIRST_HALF	\N	f	{}
9	6	2026-12-06	2026-12-06	1.00	WITHDRAWN	E2E withdraw	f	37	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-10-30 11:05:25.099375+00	2026-10-30 11:05:25.151325+00	CASUAL	t	f	FULL	\N	f	{}
10	6	2026-12-20	2026-12-20	1.00	WITHDRAWN	chain check	f	51	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-10-30 11:05:25.467878+00	2026-10-30 11:05:25.526646+00	CASUAL	t	f	FULL	\N	f	{}
23	8	2026-11-04	2026-11-04	1.00	APPROVED	jcsdbyuvgfyusrhusr	f	1	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-03 06:32:11.296052+00	2026-11-03 06:32:36.905311+00	CASUAL	t	t	FULL	\N	f	{}
24	8	2026-11-10	2026-11-12	3.00	CANCELLED	fdbgcvjhjjjg	f	7	7	t	f	\N	\N	\N	{"skipped": true}	2	\N	2026-11-03 09:24:45.088518+00	2026-11-03 10:06:33.846003+00	ANNUAL	t	t	FULL	\N	f	{}
8	6	2026-11-29	2026-11-29	1.00	CANCELLED	E2E approve then cancel	f	30	7	t	f	\N	\N	\N	{"skipped": true}	2	\N	2026-10-30 11:05:24.768886+00	2026-11-03 11:53:54.56544+00	ANNUAL	t	t	FULL	\N	f	{}
1	6	2026-11-04	2026-11-04	1.00	APPROVED	Family errand	f	0	0	t	f	\N	\N	\N	{}	1	\N	2026-10-30 06:24:07.91854+00	2026-11-03 11:56:02.617843+00	CASUAL	t	t	FULL	\N	f	{}
25	6	2026-12-01	2026-12-01	1.00	CANCELLED	E2E casual apply	f	28	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-03 11:56:03.165458+00	2026-11-03 11:56:05.450685+00	CASUAL	t	t	FULL	\N	f	{}
26	6	2026-12-02	2026-12-03	2.00	CANCELLED	E2E annual multi-step	f	29	7	t	f	\N	\N	\N	{"skipped": true}	2	\N	2026-11-03 11:56:05.670384+00	2026-11-03 11:56:07.875727+00	ANNUAL	t	t	FULL	\N	f	{}
27	6	2026-12-08	2026-12-08	1.00	WITHDRAWN	will withdraw	f	35	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-03 11:56:08.082741+00	2026-11-03 11:56:08.257348+00	CASUAL	t	f	FULL	\N	f	{}
28	6	2026-12-09	2026-12-09	1.00	REJECTED	will reject	f	36	0	t	f	\N	\N	\N	{"skipped": true}	1	Coverage insufficient this week	2026-11-03 11:56:08.469472+00	2026-11-03 11:56:08.72464+00	CASUAL	t	f	FULL	\N	f	{}
16	6	2026-12-13	2026-12-13	1.00	APPROVED	hourly should fail	f	44	7	t	f	\N	\N	\N	{"skipped": true}	2	\N	2026-10-30 11:07:52.373462+00	2026-11-03 11:56:09.358605+00	ANNUAL	t	t	FULL	\N	f	{}
29	6	2026-12-10	2026-12-10	1.00	CANCELLED	E2E casual apply	f	37	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-03 11:56:39.756383+00	2026-11-03 11:56:41.605484+00	CASUAL	t	t	FULL	\N	f	{}
30	6	2026-12-14	2026-12-15	2.00	CANCELLED	E2E annual multi-step	f	41	7	t	f	\N	\N	\N	{"skipped": true}	2	\N	2026-11-03 11:56:41.870104+00	2026-11-03 11:56:43.727834+00	ANNUAL	t	t	FULL	\N	f	{}
31	6	2026-12-17	2026-12-17	1.00	REJECTED	will reject	f	44	0	t	f	\N	\N	\N	{"skipped": true}	1	Coverage insufficient this week	2026-11-03 11:56:44.257264+00	2026-11-03 11:56:44.505141+00	CASUAL	t	f	FULL	\N	f	{}
32	6	2026-12-10	2026-12-10	1.00	CANCELLED	E2E casual apply	f	37	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-03 11:57:16.316227+00	2026-11-03 11:57:18.255035+00	CASUAL	t	t	FULL	\N	f	{}
3	12	2026-11-06	2026-11-06	1.00	APPROVED	schedule-day-count test	f	7	0	t	f	\N	\N	\N	{"skipped": true}	2	\N	2026-10-30 09:04:52.148267+00	2026-11-04 07:10:44.581287+00	CASUAL	t	t	FULL	\N	f	{}
33	6	2026-12-14	2026-12-15	2.00	CANCELLED	E2E annual multi-step	f	41	7	t	f	\N	\N	\N	{"skipped": true}	2	\N	2026-11-03 11:57:18.461557+00	2026-11-03 11:57:20.207379+00	ANNUAL	t	t	FULL	\N	f	{}
34	6	2026-12-22	2026-12-22	1.00	WITHDRAWN	will withdraw	f	49	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-03 11:57:20.464374+00	2026-11-03 11:57:20.687901+00	CASUAL	t	f	FULL	\N	f	{}
35	6	2026-12-23	2026-12-23	1.00	REJECTED	will reject	f	50	0	t	f	\N	\N	\N	{"skipped": true}	1	Coverage insufficient this week	2026-11-03 11:57:20.864577+00	2026-11-03 11:57:21.113425+00	CASUAL	t	f	FULL	\N	f	{}
\.


ALTER TABLE public.leave_application ENABLE TRIGGER ALL;

--
-- Data for Name: leave_approval; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.leave_approval DISABLE TRIGGER ALL;

COPY public.leave_approval (id, application_id, step_order, approver_role, status, actor_name, comment_text, decided_at, approver_employee_id, resolver, named_employee_id, expected_approver_id) FROM stdin;
3	4	1	MANAGER	APPROVED	Nadia Chowdhury	kjjhbhjbhjb	2026-10-30 09:30:58.373273+00	2	\N	\N	\N
4	4	2	HR	APPROVED	Nadia Chowdhury	nb b  bhb	2026-10-30 09:31:56.855908+00	2	\N	\N	\N
5	5	1	MANAGER	APPROVED	Nadia Chowdhury	HR trying early	2026-10-30 09:48:14.300356+00	2	\N	\N	\N
6	5	2	HR	APPROVED	Nadia Chowdhury	HR final	2026-10-30 09:48:14.775641+00	2	\N	\N	\N
7	6	1	MANAGER	APPROVED	Lina Ahmed	Manager OK	2026-10-30 09:49:03.42955+00	4	\N	\N	\N
8	6	2	HR	APPROVED	Nadia Chowdhury	HR final	2026-10-30 09:49:03.636306+00	2	\N	\N	\N
1	3	1	MANAGER	APPROVED	Rafiul Islam	mkjhjbhj	2026-10-30 10:29:46.562601+00	3	\N	\N	\N
10	7	2	HR	PENDING	\N	\N	\N	\N	DESIGNATION_OR_RBAC	\N	\N
9	7	1	MANAGER	REJECTED	Lina Ahmed	Not enough coverage this week	2026-10-30 11:05:23.952796+00	4	REPORTING_MANAGER	\N	4
15	9	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	4
16	10	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	4
17	11	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	4
18	12	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	4
20	13	2	HR	PENDING	\N	\N	\N	\N	DESIGNATION_OR_RBAC	\N	\N
19	13	1	MANAGER	REJECTED	Lina Ahmed	Not enough coverage this week	2026-10-30 11:07:51.254632+00	4	REPORTING_MANAGER	\N	4
23	14	1	MANAGER	APPROVED	Lina Ahmed	cancel ok	2026-10-30 11:07:51.894066+00	4	REPORTING_MANAGER	\N	4
24	14	2	HR	APPROVED	Nadia Chowdhury	cancel hr ok	2026-10-30 11:07:51.953777+00	2	DESIGNATION_OR_RBAC	\N	\N
25	15	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	4
28	17	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	4
29	18	1	MANAGER	APPROVED	Lina Ahmed	Manager OK	2026-10-30 11:08:12.611287+00	4	REPORTING_MANAGER	\N	4
30	18	2	HR	APPROVED	Nadia Chowdhury	HR final	2026-10-30 11:08:13.249916+00	2	DESIGNATION_OR_RBAC	\N	\N
32	19	2	HR	PENDING	\N	\N	\N	\N	DESIGNATION_OR_RBAC	\N	\N
31	19	1	MANAGER	REJECTED	Lina Ahmed	Not enough coverage this week	2026-10-30 11:08:15.51965+00	4	REPORTING_MANAGER	\N	4
35	20	1	MANAGER	APPROVED	Lina Ahmed	cancel ok	2026-10-30 11:08:16.297183+00	4	REPORTING_MANAGER	\N	4
36	20	2	HR	APPROVED	Nadia Chowdhury	cancel hr ok	2026-10-30 11:08:16.397004+00	2	DESIGNATION_OR_RBAC	\N	\N
37	21	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	4
38	22	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	4
26	16	1	MANAGER	APPROVED	Asman Rahman	hnbhgvhsd	2026-10-30 12:45:17.099997+00	1	REPORTING_MANAGER	\N	4
39	23	1	MANAGER	APPROVED	Asman Rahman	kjnjdngrberd	2026-11-03 06:32:36.905311+00	1	REPORTING_MANAGER	\N	5
53	27	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	4
42	24	1	MANAGER	APPROVED	Asman Rahman	\N	2026-11-03 10:05:54.754572+00	1	REPORTING_MANAGER	\N	5
43	24	2	HR	APPROVED	Nadia Chowdhury	\N	2026-11-03 10:06:33.846003+00	2	DESIGNATION_OR_RBAC	\N	\N
13	8	1	MANAGER	APPROVED	Lina Ahmed	\N	2026-11-03 11:53:53.596719+00	4	REPORTING_MANAGER	\N	4
14	8	2	HR	APPROVED	Nadia Chowdhury	\N	2026-11-03 11:53:54.56544+00	2	DESIGNATION_OR_RBAC	\N	\N
44	1	1	MANAGER	APPROVED	Lina Ahmed	\N	2026-11-03 11:56:02.617843+00	4	REPORTING_MANAGER	\N	4
54	28	1	MANAGER	REJECTED	Lina Ahmed	Coverage insufficient this week	2026-11-03 11:56:08.72464+00	4	REPORTING_MANAGER	\N	4
27	16	2	HR	APPROVED	Nadia Chowdhury	\N	2026-11-03 11:56:09.358605+00	2	DESIGNATION_OR_RBAC	\N	\N
48	25	1	MANAGER	APPROVED	Lina Ahmed	\N	2026-11-03 11:56:05.450685+00	4	REPORTING_MANAGER	\N	4
51	26	1	MANAGER	APPROVED	Lina Ahmed	\N	2026-11-03 11:56:07.652954+00	4	REPORTING_MANAGER	\N	4
52	26	2	HR	APPROVED	Nadia Chowdhury	\N	2026-11-03 11:56:07.875727+00	2	DESIGNATION_OR_RBAC	\N	\N
71	33	2	HR	APPROVED	Nadia Chowdhury	\N	2026-11-03 11:57:20.207379+00	2	DESIGNATION_OR_RBAC	\N	\N
58	29	1	MANAGER	APPROVED	Lina Ahmed	\N	2026-11-03 11:56:41.605484+00	4	REPORTING_MANAGER	\N	4
61	30	1	MANAGER	APPROVED	Lina Ahmed	\N	2026-11-03 11:56:43.455952+00	4	REPORTING_MANAGER	\N	4
62	30	2	HR	APPROVED	Nadia Chowdhury	\N	2026-11-03 11:56:43.727834+00	2	DESIGNATION_OR_RBAC	\N	\N
63	31	1	MANAGER	REJECTED	Lina Ahmed	Coverage insufficient this week	2026-11-03 11:56:44.505141+00	4	REPORTING_MANAGER	\N	4
72	34	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	4
67	32	1	MANAGER	APPROVED	Lina Ahmed	\N	2026-11-03 11:57:18.255035+00	4	REPORTING_MANAGER	\N	4
73	35	1	MANAGER	REJECTED	Lina Ahmed	Coverage insufficient this week	2026-11-03 11:57:21.113425+00	4	REPORTING_MANAGER	\N	4
70	33	1	MANAGER	APPROVED	Lina Ahmed	\N	2026-11-03 11:57:19.989876+00	4	REPORTING_MANAGER	\N	4
2	3	2	HR	APPROVED	Nadia Chowdhury	\N	2026-11-04 07:10:44.581287+00	2	\N	\N	\N
\.


ALTER TABLE public.leave_approval ENABLE TRIGGER ALL;

--
-- Data for Name: leave_approval_delegate; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.leave_approval_delegate DISABLE TRIGGER ALL;

COPY public.leave_approval_delegate (id, employee_id, delegate_employee_id, from_date, to_date, active, created_at) FROM stdin;
1	4	6	2026-11-22	2026-11-29	t	2026-10-30 11:05:25.290389+00
2	4	6	2026-11-22	2026-11-29	t	2026-10-30 11:06:49.430474+00
3	4	6	2026-11-22	2026-11-29	t	2026-10-30 11:07:52.207526+00
4	4	6	2026-11-22	2026-11-29	t	2026-10-30 11:08:16.895399+00
\.


ALTER TABLE public.leave_approval_delegate ENABLE TRIGGER ALL;

--
-- Data for Name: leave_audit_log; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.leave_audit_log DISABLE TRIGGER ALL;

COPY public.leave_audit_log (id, at, actor_employee_id, action, entity, entity_id, before_json, after_json, ip) FROM stdin;
97	2026-10-30 11:08:16.655792+00	6	WITHDRAW	LeaveApplication	21	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
31	2026-10-30 09:04:52.162747+00	12	CREATE	LeaveApplication	3	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-06", "startDate": "2026-11-06", "employeeId": 12, "leaveTypeCode": "CASUAL"}	\N
52	2026-10-30 09:25:51.631803+00	14	CREATE	LeaveApplication	4	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-02", "startDate": "2026-11-02", "employeeId": 14, "leaveTypeCode": "SICK"}	\N
55	2026-10-30 09:48:14.105699+00	6	CREATE	LeaveApplication	5	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-07", "startDate": "2026-12-07", "employeeId": 6, "leaveTypeCode": "ANNUAL"}	\N
56	2026-10-30 09:49:03.114022+00	6	CREATE	LeaveApplication	6	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-07", "startDate": "2026-12-07", "employeeId": 6, "leaveTypeCode": "ANNUAL"}	\N
98	2026-10-30 11:08:16.754478+00	1	RENEW	LeaveCategory	6	{"sourceCode": "ANNUAL"}	{"newId": 7, "newCode": "ANNUAL_2029"}	\N
99	2026-10-30 11:08:17.348082+00	6	CREATE	LeaveApplication	22	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-20", "startDate": "2026-12-20", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
100	2026-10-30 11:08:17.409816+00	6	WITHDRAW	LeaveApplication	22	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
71	2026-10-30 11:05:23.83886+00	6	CREATE	LeaveApplication	7	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-22", "startDate": "2026-11-22", "employeeId": 6, "leaveTypeCode": "ANNUAL"}	\N
72	2026-10-30 11:05:24.774309+00	6	CREATE	LeaveApplication	8	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-29", "startDate": "2026-11-29", "employeeId": 6, "leaveTypeCode": "ANNUAL"}	\N
73	2026-10-30 11:05:24.963961+00	6	CANCEL_REQUEST	LeaveApplication	8	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
74	2026-10-30 11:05:25.109938+00	6	CREATE	LeaveApplication	9	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-06", "startDate": "2026-12-06", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
75	2026-10-30 11:05:25.151903+00	6	WITHDRAW	LeaveApplication	9	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
76	2026-10-30 11:05:25.209264+00	1	RENEW	LeaveCategory	2	{"oldCode": "ANNUAL_2025"}	{"newId": 5, "newCode": "ANNUAL"}	\N
77	2026-10-30 11:05:25.470681+00	6	CREATE	LeaveApplication	10	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-20", "startDate": "2026-12-20", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
78	2026-10-30 11:05:25.526646+00	6	WITHDRAW	LeaveApplication	10	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
79	2026-10-30 11:06:49.208871+00	6	CREATE	LeaveApplication	11	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-06", "startDate": "2026-12-06", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
80	2026-10-30 11:06:49.247731+00	6	WITHDRAW	LeaveApplication	11	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
81	2026-10-30 11:06:49.342064+00	1	RENEW	LeaveCategory	5	{"oldCode": "ANNUAL_2027"}	{"newId": 6, "newCode": "ANNUAL"}	\N
82	2026-10-30 11:06:49.708623+00	6	CREATE	LeaveApplication	12	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-20", "startDate": "2026-12-20", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
83	2026-10-30 11:06:49.741486+00	6	WITHDRAW	LeaveApplication	12	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
84	2026-10-30 11:07:51.138532+00	6	CREATE	LeaveApplication	13	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-22", "startDate": "2026-11-22", "employeeId": 6, "leaveTypeCode": "ANNUAL"}	\N
85	2026-10-30 11:07:51.635125+00	6	CREATE	LeaveApplication	14	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-29", "startDate": "2026-11-29", "employeeId": 6, "leaveTypeCode": "ANNUAL"}	\N
86	2026-10-30 11:07:51.842907+00	6	CANCEL_REQUEST	LeaveApplication	14	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
87	2026-10-30 11:07:52.028298+00	6	CREATE	LeaveApplication	15	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-06", "startDate": "2026-12-06", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
88	2026-10-30 11:07:52.073715+00	6	WITHDRAW	LeaveApplication	15	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
89	2026-10-30 11:07:52.378908+00	6	CREATE	LeaveApplication	16	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-13", "startDate": "2026-12-13", "employeeId": 6, "leaveTypeCode": "ANNUAL"}	\N
90	2026-10-30 11:07:52.457037+00	6	CREATE	LeaveApplication	17	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-20", "startDate": "2026-12-20", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
91	2026-10-30 11:07:52.491378+00	6	WITHDRAW	LeaveApplication	17	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
92	2026-10-30 11:08:12.08289+00	6	CREATE	LeaveApplication	18	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-21", "startDate": "2026-12-21", "employeeId": 6, "leaveTypeCode": "ANNUAL"}	\N
93	2026-10-30 11:08:15.313541+00	6	CREATE	LeaveApplication	19	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-22", "startDate": "2026-11-22", "employeeId": 6, "leaveTypeCode": "ANNUAL"}	\N
94	2026-10-30 11:08:15.794304+00	6	CREATE	LeaveApplication	20	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-29", "startDate": "2026-11-29", "employeeId": 6, "leaveTypeCode": "ANNUAL"}	\N
95	2026-10-30 11:08:16.215925+00	6	CANCEL_REQUEST	LeaveApplication	20	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
96	2026-10-30 11:08:16.567608+00	6	CREATE	LeaveApplication	21	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-06", "startDate": "2026-12-06", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
107	2026-10-30 12:12:16.607642+00	1	EXPORT	LeaveBalances	\N	\N	{"rows": 21, "year": 2026}	\N
108	2026-10-30 12:12:16.715494+00	1	EXPORT	LeaveRequests	\N	\N	{"to": "2026-10-30", "from": "2026-01-01", "rows": 0}	\N
119	2026-10-30 12:12:34.935472+00	1	EXPORT	LeaveBalances	\N	\N	{"rows": 21, "year": 2026}	\N
120	2026-10-30 12:12:35.000177+00	1	EXPORT	LeaveRequests	\N	\N	{"to": "2026-10-30", "from": "2026-01-01", "rows": 0}	\N
123	2026-10-30 12:18:06.567096+00	1	RENEW	LeaveCategory	8	{"sourceCode": "HUDAI"}	{"newId": 9, "newCode": "HUDAI_2027"}	\N
124	2026-10-30 12:40:00.911275+00	1	CREATE	LeaveCategory	10	\N	{"code": "TEST_64000", "name": "Test Leave TEST_64000"}	\N
125	2026-10-30 14:00:22.527559+00	9	EXPORT	LeaveBalances	\N	\N	{"rows": 1, "year": 2026}	\N
126	2026-10-30 14:07:39.949833+00	1	EXPORT	LeaveBalances	\N	\N	{"rows": 21, "year": 2026}	\N
153	2026-11-03 06:32:11.359696+00	8	CREATE	LeaveApplication	23	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-04", "startDate": "2026-11-04", "employeeId": 8, "leaveTypeCode": "CASUAL"}	\N
157	2026-11-03 09:24:45.140598+00	8	CREATE	LeaveApplication	24	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-12", "startDate": "2026-11-10", "employeeId": 8, "leaveTypeCode": "ANNUAL"}	\N
158	2026-11-03 09:25:52.448358+00	8	CANCEL_REQUEST	LeaveApplication	24	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
159	2026-11-03 11:56:03.181913+00	6	CREATE	LeaveApplication	25	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-01", "startDate": "2026-12-01", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
160	2026-11-03 11:56:04.162243+00	6	CANCEL_REQUEST	LeaveApplication	25	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
161	2026-11-03 11:56:04.687432+00	6	CANCEL_WITHDRAW	LeaveApplication	25	{"status": "PENDING_CANCEL"}	{"status": "APPROVED"}	\N
162	2026-11-03 11:56:04.954803+00	6	CANCEL_REQUEST	LeaveApplication	25	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
163	2026-11-03 11:56:05.329061+00	6	CANCEL_REQUEST	LeaveApplication	25	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
164	2026-11-03 11:56:05.67867+00	6	CREATE	LeaveApplication	26	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-03", "startDate": "2026-12-02", "employeeId": 6, "leaveTypeCode": "ANNUAL"}	\N
165	2026-11-03 11:56:06.96568+00	6	CANCEL_REQUEST	LeaveApplication	26	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
166	2026-11-03 11:56:08.090911+00	6	CREATE	LeaveApplication	27	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-08", "startDate": "2026-12-08", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
167	2026-11-03 11:56:08.257348+00	6	WITHDRAW	LeaveApplication	27	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
168	2026-11-03 11:56:08.475111+00	6	CREATE	LeaveApplication	28	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-09", "startDate": "2026-12-09", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
169	2026-11-03 11:56:39.76535+00	6	CREATE	LeaveApplication	29	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-10", "startDate": "2026-12-10", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
170	2026-11-03 11:56:40.578233+00	6	CANCEL_REQUEST	LeaveApplication	29	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
171	2026-11-03 11:56:40.955479+00	6	CANCEL_WITHDRAW	LeaveApplication	29	{"status": "PENDING_CANCEL"}	{"status": "APPROVED"}	\N
172	2026-11-03 11:56:41.164038+00	6	CANCEL_REQUEST	LeaveApplication	29	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
173	2026-11-03 11:56:41.509635+00	6	CANCEL_REQUEST	LeaveApplication	29	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
174	2026-11-03 11:56:41.878078+00	6	CREATE	LeaveApplication	30	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-15", "startDate": "2026-12-14", "employeeId": 6, "leaveTypeCode": "ANNUAL"}	\N
175	2026-11-03 11:56:43.110828+00	6	CANCEL_REQUEST	LeaveApplication	30	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
176	2026-11-03 11:56:44.265617+00	6	CREATE	LeaveApplication	31	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-17", "startDate": "2026-12-17", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
177	2026-11-03 11:57:16.32425+00	6	CREATE	LeaveApplication	32	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-10", "startDate": "2026-12-10", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
178	2026-11-03 11:57:17.193064+00	6	CANCEL_REQUEST	LeaveApplication	32	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
179	2026-11-03 11:57:17.661117+00	6	CANCEL_WITHDRAW	LeaveApplication	32	{"status": "PENDING_CANCEL"}	{"status": "APPROVED"}	\N
180	2026-11-03 11:57:17.86204+00	6	CANCEL_REQUEST	LeaveApplication	32	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
181	2026-11-03 11:57:18.149518+00	6	CANCEL_REQUEST	LeaveApplication	32	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
182	2026-11-03 11:57:18.469507+00	6	CREATE	LeaveApplication	33	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-15", "startDate": "2026-12-14", "employeeId": 6, "leaveTypeCode": "ANNUAL"}	\N
183	2026-11-03 11:57:19.589824+00	6	CANCEL_REQUEST	LeaveApplication	33	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
184	2026-11-03 11:57:20.469305+00	6	CREATE	LeaveApplication	34	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-22", "startDate": "2026-12-22", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
185	2026-11-03 11:57:20.688441+00	6	WITHDRAW	LeaveApplication	34	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
186	2026-11-03 11:57:20.870177+00	6	CREATE	LeaveApplication	35	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-23", "startDate": "2026-12-23", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
\.


ALTER TABLE public.leave_audit_log ENABLE TRIGGER ALL;

--
-- Data for Name: leave_balance; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.leave_balance DISABLE TRIGGER ALL;

COPY public.leave_balance (id, employee_id, leave_type_code, year, balance_days, updated_at, version) FROM stdin;
1	2	CASUAL	2026	10.00	2026-10-30 06:24:07.91854+00	0
3	2	SICK	2026	10.00	2026-10-30 06:24:07.91854+00	0
4	3	CASUAL	2026	10.00	2026-10-30 06:24:07.91854+00	0
6	3	SICK	2026	10.00	2026-10-30 06:24:07.91854+00	0
7	4	CASUAL	2026	10.00	2026-10-30 06:24:07.91854+00	0
9	4	SICK	2026	10.00	2026-10-30 06:24:07.91854+00	0
10	5	CASUAL	2026	10.00	2026-10-30 06:24:07.91854+00	0
12	5	SICK	2026	10.00	2026-10-30 06:24:07.91854+00	0
15	12	SICK	2026	10.00	2026-10-30 06:24:07.91854+00	0
18	6	SICK	2026	10.00	2026-10-30 06:24:07.91854+00	0
21	7	SICK	2026	10.00	2026-10-30 06:24:07.91854+00	0
24	10	SICK	2026	10.00	2026-10-30 06:24:07.91854+00	0
27	8	SICK	2026	10.00	2026-10-30 06:24:07.91854+00	0
28	11	CASUAL	2026	10.00	2026-10-30 06:24:07.91854+00	0
30	11	SICK	2026	10.00	2026-10-30 06:24:07.91854+00	0
31	9	CASUAL	2026	10.00	2026-10-30 06:24:07.91854+00	0
33	9	SICK	2026	10.00	2026-10-30 06:24:07.91854+00	0
34	1	CASUAL	2026	10.00	2026-10-30 06:24:07.91854+00	0
36	1	SICK	2026	10.00	2026-10-30 06:24:07.91854+00	0
37	14	SICK	2026	0.00	2026-10-30 09:31:56.871954+00	0
2	2	ANNUAL	2026	14.00	2026-10-30 06:24:07.91854+00	0
5	3	ANNUAL	2026	14.00	2026-10-30 06:24:07.91854+00	0
8	4	ANNUAL	2026	14.00	2026-10-30 06:24:07.91854+00	0
11	5	ANNUAL	2026	14.00	2026-10-30 06:24:07.91854+00	0
29	11	ANNUAL	2026	14.00	2026-10-30 06:24:07.91854+00	0
32	9	ANNUAL	2026	14.00	2026-10-30 06:24:07.91854+00	0
35	1	ANNUAL	2026	20.00	2026-10-30 06:24:07.91854+00	0
38	14	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
39	18	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
40	1	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
41	11	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
42	8	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
43	9	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
44	2	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
45	12	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
46	15	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
47	4	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
48	16	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
49	3	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
50	19	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
51	5	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
52	7	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
53	6	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
54	10	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
55	21	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
56	13	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
57	17	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
58	20	TEST_64000	2026	5.00	2026-10-30 12:40:00.792488+00	0
22	10	CASUAL	2026	0.00	2026-11-04 09:35:46.48436+00	0
20	7	ANNUAL	2026	7.00	2026-11-04 11:51:38.534694+00	0
19	7	CASUAL	2026	0.00	2026-11-04 11:51:38.653083+00	0
14	12	ANNUAL	2026	0.00	2026-11-04 09:27:20.849564+00	0
13	12	CASUAL	2026	0.00	2026-11-04 09:27:20.880448+00	0
17	6	ANNUAL	2026	0.00	2026-11-04 09:34:56.399338+00	0
16	6	CASUAL	2026	0.00	2026-11-04 09:34:56.473475+00	0
23	10	ANNUAL	2026	6.00	2026-11-04 09:35:46.476016+00	0
26	8	ANNUAL	2026	0.00	2026-11-08 19:00:41.66681+00	1
25	8	CASUAL	2026	0.00	2026-11-08 19:00:41.846896+00	1
\.


ALTER TABLE public.leave_balance ENABLE TRIGGER ALL;

--
-- Data for Name: leave_balance_ledger; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.leave_balance_ledger DISABLE TRIGGER ALL;

COPY public.leave_balance_ledger (id, employee_id, leave_type_code, year, delta_days, reason, actor_employee_id, created_at, operation_key, source_type, source_id) FROM stdin;
1	6	ANNUAL	2026	-1.00	Leave approved deduction	\N	2026-10-30 09:48:14.788387+00	\N	\N	\N
2	6	ANNUAL	2026	-1.00	Leave approved deduction	\N	2026-10-30 09:49:03.644077+00	\N	\N	\N
3	6	ANNUAL	2026	0.25	E2E FR-LV-05 audit adjust	1	2026-10-30 11:05:23.646985+00	\N	\N	\N
4	6	ANNUAL	2026	0.25		1	2026-10-30 11:05:23.697749+00	\N	\N	\N
5	6	ANNUAL	2026	-1.00	Leave approved deduction	\N	2026-10-30 11:05:24.910795+00	\N	\N	\N
6	6	ANNUAL	2026	0.25	E2E FR-LV-05 audit adjust	1	2026-10-30 11:06:48.939463+00	\N	\N	\N
7	6	ANNUAL	2026	0.25		1	2026-10-30 11:06:48.984294+00	\N	\N	\N
8	6	ANNUAL	2026	0.25	E2E FR-LV-05 audit adjust	1	2026-10-30 11:07:50.879535+00	\N	\N	\N
9	6	ANNUAL	2026	0.25		1	2026-10-30 11:07:50.923041+00	\N	\N	\N
10	6	ANNUAL	2026	-1.00	Leave approved deduction	\N	2026-10-30 11:07:51.771253+00	\N	\N	\N
11	6	ANNUAL	2026	1.00	Leave cancel restore	2	2026-10-30 11:07:51.958118+00	\N	\N	\N
12	6	ANNUAL	2026	-1.00	Leave approved deduction	\N	2026-10-30 11:08:13.272997+00	\N	\N	\N
13	6	ANNUAL	2026	0.25	E2E FR-LV-05 audit adjust	1	2026-10-30 11:08:14.958626+00	\N	\N	\N
14	6	ANNUAL	2026	0.25		1	2026-10-30 11:08:15.088156+00	\N	\N	\N
15	6	ANNUAL	2026	-1.00	Leave approved deduction	\N	2026-10-30 11:08:16.098979+00	\N	\N	\N
16	6	ANNUAL	2026	1.00	Leave cancel restore	2	2026-10-30 11:08:16.425331+00	\N	\N	\N
17	12	ANNUAL	2026	-12.00	Payroll absence cover 2026-09	\N	2026-10-31 07:54:47.231081+00	\N	\N	\N
18	12	CASUAL	2026	-10.00	Payroll absence cover 2026-09	\N	2026-10-31 07:54:47.252483+00	\N	\N	\N
19	8	CASUAL	2026	-1.00	Leave approved deduction	\N	2026-11-03 06:32:37.027371+00	\N	\N	\N
20	12	ANNUAL	2026	-8.00	Payroll absence cover 2026-10	\N	2026-11-03 06:34:58.698648+00	\N	\N	\N
21	12	CASUAL	2026	-10.00	Payroll absence cover 2026-10	\N	2026-11-03 06:34:58.709304+00	\N	\N	\N
22	8	ANNUAL	2026	-3.00	Leave approved deduction	\N	2026-11-03 09:25:32.319502+00	\N	\N	\N
23	8	ANNUAL	2026	3.00	Leave cancel restore	2	2026-11-03 10:06:33.868153+00	\N	\N	\N
24	6	ANNUAL	2026	1.00	Leave cancel restore	2	2026-11-03 11:53:54.573273+00	\N	\N	\N
25	6	CASUAL	2026	-1.00	Leave approved deduction	\N	2026-11-03 11:56:02.6395+00	\N	\N	\N
26	6	CASUAL	2026	-1.00	Leave approved deduction	\N	2026-11-03 11:56:03.912719+00	\N	\N	\N
27	6	CASUAL	2026	1.00	Leave cancel restore	4	2026-11-03 11:56:05.455529+00	\N	\N	\N
28	6	ANNUAL	2026	-2.00	Leave approved deduction	\N	2026-11-03 11:56:06.713914+00	\N	\N	\N
29	6	ANNUAL	2026	2.00	Leave cancel restore	2	2026-11-03 11:56:07.879894+00	\N	\N	\N
30	6	ANNUAL	2026	-1.00	Leave approved deduction	\N	2026-11-03 11:56:09.366265+00	\N	\N	\N
31	6	CASUAL	2026	-1.00	Leave approved deduction	\N	2026-11-03 11:56:40.370408+00	\N	\N	\N
32	6	CASUAL	2026	1.00	Leave cancel restore	4	2026-11-03 11:56:41.609378+00	\N	\N	\N
33	6	ANNUAL	2026	-2.00	Leave approved deduction	\N	2026-11-03 11:56:42.890982+00	\N	\N	\N
34	6	ANNUAL	2026	2.00	Leave cancel restore	2	2026-11-03 11:56:43.734938+00	\N	\N	\N
35	6	CASUAL	2026	-1.00	Leave approved deduction	\N	2026-11-03 11:57:16.980027+00	\N	\N	\N
36	6	CASUAL	2026	1.00	Leave cancel restore	4	2026-11-03 11:57:18.258984+00	\N	\N	\N
37	6	ANNUAL	2026	-2.00	Leave approved deduction	\N	2026-11-03 11:57:19.326585+00	\N	\N	\N
38	6	ANNUAL	2026	2.00	Leave cancel restore	2	2026-11-03 11:57:20.210801+00	\N	\N	\N
39	10	ANNUAL	2026	-8.00	Payroll absence cover 2026-10	\N	2026-11-03 13:32:49.295661+00	\N	\N	\N
40	10	CASUAL	2026	-10.00	Payroll absence cover 2026-10	\N	2026-11-03 13:32:49.311004+00	\N	\N	\N
41	10	ANNUAL	2026	8.00	Payroll absence cover 2026-10	\N	2026-11-03 13:32:52.273906+00	\N	\N	\N
42	10	CASUAL	2026	10.00	Payroll absence cover 2026-10	\N	2026-11-03 13:32:52.28192+00	\N	\N	\N
43	6	ANNUAL	2026	-9.00	Payroll absence cover 2026-10	\N	2026-11-04 05:51:25.50424+00	\N	\N	\N
44	6	CASUAL	2026	-9.00	Payroll absence cover 2026-10	\N	2026-11-04 05:51:25.597369+00	\N	\N	\N
45	6	ANNUAL	2026	9.00	Payroll absence cover 2026-10	\N	2026-11-04 06:53:47.161092+00	\N	\N	\N
46	6	CASUAL	2026	9.00	Payroll absence cover 2026-10	\N	2026-11-04 06:53:47.551429+00	\N	\N	\N
47	12	ANNUAL	2026	8.00	Payroll absence cover 2026-10	\N	2026-11-04 06:54:26.707226+00	\N	\N	\N
48	12	CASUAL	2026	10.00	Payroll absence cover 2026-10	\N	2026-11-04 06:54:26.716293+00	\N	\N	\N
49	12	CASUAL	2026	-1.00	Leave approved deduction	\N	2026-11-04 07:10:44.661348+00	\N	\N	\N
50	8	ANNUAL	2026	-9.00	Payroll absence cover 2026-10	\N	2026-11-04 09:10:52.210973+00	\N	\N	\N
51	8	CASUAL	2026	-9.00	Payroll absence cover 2026-10	\N	2026-11-04 09:10:52.253718+00	\N	\N	\N
52	12	ANNUAL	2026	-8.00	Payroll absence cover 2026-10	\N	2026-11-04 09:27:20.849564+00	\N	\N	\N
53	12	CASUAL	2026	-10.00	Payroll absence cover 2026-10	\N	2026-11-04 09:27:20.880448+00	\N	\N	\N
54	6	ANNUAL	2026	-12.00	Payroll absence cover 2026-06	\N	2026-11-04 09:34:56.399338+00	\N	\N	\N
55	6	CASUAL	2026	-9.00	Payroll absence cover 2026-06	\N	2026-11-04 09:34:56.473475+00	\N	\N	\N
56	10	ANNUAL	2026	-8.00	Payroll absence cover 2026-10	\N	2026-11-04 09:35:46.476016+00	\N	\N	\N
57	10	CASUAL	2026	-10.00	Payroll absence cover 2026-10	\N	2026-11-04 09:35:46.48436+00	\N	\N	\N
58	7	ANNUAL	2026	-7.00	Payroll absence cover 2026-10	\N	2026-11-04 11:51:38.534694+00	\N	\N	\N
59	7	CASUAL	2026	-10.00	Payroll absence cover 2026-10	\N	2026-11-04 11:51:38.653083+00	\N	\N	\N
60	8	ANNUAL	2026	-9.00	Payroll absence cover 2026-10	\N	2026-11-08 19:00:41.66681+00	\N	\N	\N
61	8	CASUAL	2026	-9.00	Payroll absence cover 2026-10	\N	2026-11-08 19:00:41.846896+00	\N	\N	\N
\.


ALTER TABLE public.leave_balance_ledger ENABLE TRIGGER ALL;

--
-- Data for Name: leave_blackout; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.leave_blackout DISABLE TRIGGER ALL;

COPY public.leave_blackout (id, store_id, start_date, end_date, reason) FROM stdin;
\.


ALTER TABLE public.leave_blackout ENABLE TRIGGER ALL;

--
-- Data for Name: leave_category; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.leave_category DISABLE TRIGGER ALL;

COPY public.leave_category (id, code, name, quota_days, period_start, period_end, paid, half_day_allowed, attachment_mode, attachment_after_days, notice_days, max_per_request, negative_cap, approval_chain, active, applies_to, pro_rata, carry_mode, carry_cap_days, carry_expiry_date) FROM stdin;
1	CASUAL	Casual Leave	10.00	\N	\N	t	t	NEVER	0	0	3	0.00	[{"role": "MANAGER", "step": 1}]	t	\N	f	NONE	\N	\N
3	SICK	Sick Leave	10.00	\N	\N	t	t	AFTER_DAYS	0	0	5	-2.00	[{"role": "MANAGER", "step": 1}]	t	\N	f	NONE	\N	\N
4	UNPAID	Unpaid Leave	0.00	\N	\N	f	t	NEVER	0	3	30	0.00	[{"role": "MANAGER", "step": 1}, {"role": "HR", "step": 2}]	t	\N	f	NONE	\N	\N
2	ANNUAL_2025	Annual Leave	14.00	\N	\N	t	t	NEVER	0	7	14	0.00	[{"role": "MANAGER", "step": 1}, {"role": "HR", "step": 2}]	f	\N	f	NONE	\N	\N
5	ANNUAL_2027	Annual Leave	14.00	2027-01-01	2027-12-31	t	t	NEVER	0	7	14	0.00	[{"role": "MANAGER", "step": 1}, {"role": "HR", "step": 2}]	f	\N	f	NONE	\N	\N
6	ANNUAL	Annual Leave	14.00	2026-01-01	2026-12-31	t	t	NEVER	0	7	14	0.00	[{"role": "MANAGER", "step": 1}, {"role": "HR", "step": 2}]	t	\N	f	NONE	\N	\N
7	ANNUAL_2029	Annual Leave (2029)	14.00	2029-01-01	2029-12-31	t	t	NEVER	0	7	14	0.00	[{"role": "MANAGER", "step": 1}, {"role": "HR", "step": 2}]	t	\N	f	NONE	\N	\N
8	HUDAI	Hudai	10.00	2026-10-27	2026-12-31	t	t	NEVER	0	0	2	\N	[{"role": "MANAGER", "step": 1}]	t	\N	f	NONE	\N	\N
9	HUDAI_2027	Hudai (2027)	10.00	2027-01-01	2027-12-31	f	t	NEVER	0	0	2	\N	[{"role": "MANAGER", "step": 1}]	t	\N	f	NONE	\N	\N
10	TEST_64000	Test Leave TEST_64000	5.00	2026-01-01	2026-12-31	t	t	NEVER	0	0	0	\N	\N	t	\N	f	NONE	\N	\N
\.


ALTER TABLE public.leave_category ENABLE TRIGGER ALL;

--
-- Data for Name: leave_operation; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.leave_operation DISABLE TRIGGER ALL;

COPY public.leave_operation (id, operation_key, event_id, employee_id, period_label, reverse_flag, status, result_json, created_at, completed_at) FROM stdin;
2	FINALIZE:33:r1	08def05c-f250-4933-b08c-aadea526b88c	8	2026-10	f	SUCCEEDED	{"status": "SUCCEEDED", "reverse": false, "success": true, "payslipId": 33, "employeeId": 8, "operationId": "878f66dd-0183-45a2-aa1d-8282ba6548e5", "periodLabel": "2026-10", "operationKey": "FINALIZE:33:r1"}	2026-11-08 19:00:41.652115+00	2026-11-08 19:00:41.849603+00
\.


ALTER TABLE public.leave_operation ENABLE TRIGGER ALL;

--
-- Data for Name: leave_policy; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.leave_policy DISABLE TRIGGER ALL;

COPY public.leave_policy (id, annual_days, work_week_days, updated_at, working_days, union_notice_days, non_union_notice_days, require_coverage_check, approval_chain, notice_mode, default_notice_days, coverage_mode, location_field, shift_field, require_relief_on_coverage_fail, default_leave_type_code) FROM stdin;
1	14	5	2026-10-30 06:24:07.91854+00	["SUN", "MON", "TUE", "WED", "THU"]	0	0	f	[{"role": "MANAGER", "step": 1, "resolver": "REPORTING_MANAGER", "allowOverride": true}, {"role": "HR", "step": 2, "resolver": "DESIGNATION_OR_RBAC", "allowOverride": false}]	NONE	0	NONE	\N	\N	f	CASUAL
\.


ALTER TABLE public.leave_policy ENABLE TRIGGER ALL;

--
-- Data for Name: leave_settings; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.leave_settings DISABLE TRIGGER ALL;

COPY public.leave_settings (id, settings, updated_at) FROM stdin;
1	{"businessPack": "qii_creative", "notificationEvents": {"events": false}}	2026-11-09 02:49:05.051689+00
\.


ALTER TABLE public.leave_settings ENABLE TRIGGER ALL;

--
-- Data for Name: leave_type; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.leave_type DISABLE TRIGGER ALL;

COPY public.leave_type (id, code, name, paid, requires_balance, allow_when_zero_balance, allow_when_coverage_fail, deduct_balance, active, sort_order) FROM stdin;
1	CASUAL	Casual Leave	t	t	f	t	t	t	1
2	ANNUAL	Annual Leave	t	t	f	t	t	t	2
4	UNPAID	Unpaid Leave	f	f	t	t	f	t	4
3	SICK	Sick Leave	t	t	f	t	t	t	3
5	ANNUAL_2025	Annual Leave	t	t	f	t	t	f	5
6	ANNUAL_2027	Annual Leave	t	t	f	t	t	f	6
7	ANNUAL_2029	Annual Leave (2029)	t	t	f	t	t	t	7
8	HUDAI	Hudai	t	t	f	t	t	t	8
9	HUDAI_2027	Hudai (2027)	f	f	t	t	f	t	9
10	TEST_64000	Test Leave TEST_64000	t	t	f	t	t	t	10
\.


ALTER TABLE public.leave_type ENABLE TRIGGER ALL;

--
-- Data for Name: store_shift_coverage; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.store_shift_coverage DISABLE TRIGGER ALL;

COPY public.store_shift_coverage (id, store_id, shift_pattern, min_staff) FROM stdin;
\.


ALTER TABLE public.store_shift_coverage ENABLE TRIGGER ALL;

--
-- Name: audit_log_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.audit_log_id_seq', 193, true);


--
-- Name: leave_application_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_application_id_seq', 35, true);


--
-- Name: leave_approval_delegate_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_approval_delegate_id_seq', 4, true);


--
-- Name: leave_approval_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_approval_id_seq', 73, true);


--
-- Name: leave_balance_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_balance_id_seq', 58, true);


--
-- Name: leave_balance_ledger_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_balance_ledger_id_seq', 61, true);


--
-- Name: leave_blackout_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_blackout_id_seq', 1, true);


--
-- Name: leave_category_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_category_id_seq', 10, true);


--
-- Name: leave_operation_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_operation_id_seq', 2, true);


--
-- Name: leave_policy_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_policy_id_seq', 1, true);


--
-- Name: leave_type_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_type_id_seq', 10, true);


--
-- Name: store_shift_coverage_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.store_shift_coverage_id_seq', 1, true);


--
-- Name: tenant_settings_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.tenant_settings_id_seq', 1, true);


--
-- PostgreSQL database dump complete
--

\unrestrict J0vGyE6IuUUuKxP0dBfEz31lFB3c89R1tgrjBflj5CdbM1R9yLeoa3i9QfsExS5

