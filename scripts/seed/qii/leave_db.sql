--
-- PostgreSQL database dump
--

\restrict bMtNtPsjlARyXE0A8Q6fymEXVCmuoxiFG7f4PSkbBdTBd8BAQQG9Chb6YpL0hvW

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
36	6	2026-12-01	2026-12-01	1.00	CANCELLED	QA full approve flow	f	21	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-10 07:20:18.546984+00	2026-11-10 07:20:19.321862+00	CASUAL	t	t	FULL	\N	f	{}
37	6	2026-12-08	2026-12-08	1.00	WITHDRAWN	overlap base	f	28	10	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-10 07:20:36.53263+00	2026-11-10 07:20:36.639373+00	QA_NT_MWAD20SK	t	f	FULL	\N	f	{}
38	6	2026-12-06	2026-12-06	1.00	CANCELLED	QA approve-cancel flow	f	26	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-10 07:23:15.442039+00	2026-11-10 07:23:15.846257+00	CASUAL	t	t	FULL	\N	f	{}
39	6	2026-12-20	2026-12-20	1.00	REJECTED	QA reject reason gate	f	40	0	t	f	\N	\N	\N	{"skipped": true}	1	QA reject — not enough cover	2026-11-10 07:23:15.982861+00	2026-11-10 07:23:16.048765+00	CASUAL	t	f	FULL	\N	f	{}
40	6	2026-12-30	2026-12-30	1.00	WITHDRAWN	QA withdraw	f	50	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-10 07:23:16.253231+00	2026-11-10 07:23:16.315007+00	CASUAL	t	f	FULL	\N	f	{}
41	6	2026-12-06	2026-12-06	1.00	CANCELLED	QA approve-cancel flow	f	26	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-10 07:24:01.044598+00	2026-11-10 07:24:01.791922+00	CASUAL	t	t	FULL	\N	f	{}
42	6	2026-12-20	2026-12-20	1.00	REJECTED	QA reject reason gate	f	40	0	t	f	\N	\N	\N	{"skipped": true}	1	QA reject — not enough cover	2026-11-10 07:24:02.039558+00	2026-11-10 07:24:02.187773+00	CASUAL	t	f	FULL	\N	f	{}
43	6	2026-12-30	2026-12-30	1.00	WITHDRAWN	QA withdraw	f	50	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-10 07:24:02.788653+00	2026-11-10 07:24:02.904838+00	CASUAL	t	f	FULL	\N	f	{}
44	6	2026-12-06	2026-12-06	1.00	CANCELLED	QA approve-cancel flow	f	26	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-10 07:24:48.74147+00	2026-11-10 07:24:48.984811+00	CASUAL	t	t	FULL	\N	f	{}
45	6	2026-12-20	2026-12-20	1.00	REJECTED	QA reject reason gate	f	40	0	t	f	\N	\N	\N	{"skipped": true}	1	QA reject — not enough cover	2026-11-10 07:24:49.100962+00	2026-11-10 07:24:49.188757+00	CASUAL	t	f	FULL	\N	f	{}
46	6	2026-12-30	2026-12-30	1.00	WITHDRAWN	QA withdraw	f	50	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-10 07:24:49.393861+00	2026-11-10 07:24:49.443257+00	CASUAL	t	f	FULL	\N	f	{}
47	6	2026-12-06	2026-12-06	1.00	CANCELLED	QA approve-cancel flow	f	26	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-10 08:22:12.285907+00	2026-11-10 08:22:12.932965+00	CASUAL	t	t	FULL	\N	f	{}
48	6	2026-12-20	2026-12-20	1.00	REJECTED	QA reject reason gate	f	40	0	t	f	\N	\N	\N	{"skipped": true}	1	QA reject — not enough cover	2026-11-10 08:22:13.117767+00	2026-11-10 08:22:13.210697+00	CASUAL	t	f	FULL	\N	f	{}
49	6	2026-12-30	2026-12-30	1.00	WITHDRAWN	QA withdraw	f	50	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-10 08:22:13.432891+00	2026-11-10 08:22:13.498888+00	CASUAL	t	f	FULL	\N	f	{}
50	4	2026-11-10	2026-11-10	1.00	APPROVED	gfcfrdxers	f	0	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-10 08:29:58.702927+00	2026-11-10 08:36:58.495107+00	QA_LV_MWAD0SF2	t	t	FULL	ytr65r5re54er	f	{}
51	4	2026-11-15	2026-11-15	1.00	WITHDRAWN	QA single apply	f	5	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-10 08:39:59.779698+00	2026-11-10 08:40:00.315185+00	QA_LV_MWAD0SF2	t	f	FULL	doc-qa-1	f	{}
52	6	2026-12-06	2026-12-06	1.00	CANCELLED	QA approve-cancel flow	f	26	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-10 08:40:02.232238+00	2026-11-10 08:40:02.534492+00	CASUAL	t	t	FULL	\N	f	{}
53	6	2026-12-20	2026-12-20	1.00	REJECTED	QA reject reason gate	f	40	0	t	f	\N	\N	\N	{"skipped": true}	1	QA reject — not enough cover	2026-11-10 08:40:02.674195+00	2026-11-10 08:40:02.790227+00	CASUAL	t	f	FULL	\N	f	{}
54	6	2026-12-30	2026-12-30	1.00	WITHDRAWN	QA withdraw	f	50	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-10 08:40:03.052341+00	2026-11-10 08:40:03.14577+00	CASUAL	t	f	FULL	\N	f	{}
55	16	2026-11-10	2026-11-10	1.00	WITHDRAWN	fgjhgkhvjk	f	0	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-10 08:42:07.230379+00	2026-11-10 08:42:23.21995+00	QA_LV_MWACAH2G	t	f	FULL	\N	f	{}
56	16	2026-11-10	2026-11-10	0.50	PENDING_APPROVAL	dcgfngh	f	0	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-10 08:44:24.220919+00	2026-11-10 08:44:24.220919+00	QA_LV_MWAD5GLJ	t	f	FIRST_HALF	\N	f	{}
57	16	2026-11-11	2026-11-11	1.00	WITHDRAWN	fvghhjyuk	f	1	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-10 08:44:32.826286+00	2026-11-10 08:45:13.741304+00	QA_LV_MWAD5GLJ	t	f	FULL	\N	f	{}
58	16	2026-11-11	2026-11-11	1.00	PENDING_APPROVAL	cfhgmjhkfghg	f	1	0	t	f	\N	\N	\N	{"skipped": true}	1	\N	2026-11-10 08:45:25.993605+00	2026-11-10 08:45:25.993605+00	QA_LV_MWAD5GLJ	t	f	FULL	\N	f	{}
61	6	2026-09-08	2026-09-09	2.00	APPROVED	Payroll scenario seed — family matter	f	14	0	t	f	\N	\N	\N	{}	2	\N	2026-08-25 04:00:00+00	2026-08-26 04:00:00+00	UNPAID	f	f	FULL	\N	f	{}
62	7	2026-10-12	2026-10-12	0.50	APPROVED	Payroll scenario seed — appointment	f	7	0	t	f	\N	\N	\N	{}	2	\N	2026-10-05 04:00:00+00	2026-10-06 04:00:00+00	CASUAL	t	t	FIRST_HALF	\N	f	{}
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
79	39	1	MANAGER	REJECTED	Lina Ahmed	QA reject — not enough cover	2026-11-10 07:23:16.048765+00	4	REPORTING_MANAGER	\N	4
75	36	1	MANAGER	APPROVED	Lina Ahmed	\N	2026-11-10 07:20:19.321862+00	4	REPORTING_MANAGER	\N	4
76	37	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	4
80	40	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	4
78	38	1	MANAGER	APPROVED	Lina Ahmed	\N	2026-11-10 07:23:15.846257+00	4	REPORTING_MANAGER	\N	4
83	42	1	MANAGER	REJECTED	Lina Ahmed	QA reject — not enough cover	2026-11-10 07:24:02.187773+00	4	REPORTING_MANAGER	\N	4
82	41	1	MANAGER	APPROVED	Lina Ahmed	\N	2026-11-10 07:24:01.791922+00	4	REPORTING_MANAGER	\N	4
84	43	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	4
86	44	1	MANAGER	APPROVED	Lina Ahmed	\N	2026-11-10 07:24:48.984811+00	4	REPORTING_MANAGER	\N	4
87	45	1	MANAGER	REJECTED	Lina Ahmed	QA reject — not enough cover	2026-11-10 07:24:49.188757+00	4	REPORTING_MANAGER	\N	4
88	46	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	4
90	47	1	MANAGER	APPROVED	Lina Ahmed	\N	2026-11-10 08:22:12.932965+00	4	REPORTING_MANAGER	\N	4
91	48	1	MANAGER	REJECTED	Lina Ahmed	QA reject — not enough cover	2026-11-10 08:22:13.210697+00	4	REPORTING_MANAGER	\N	4
92	49	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	4
93	50	1	MANAGER	APPROVED	Asman Rahman	\N	2026-11-10 08:36:58.495107+00	1	REPORTING_MANAGER	\N	1
94	51	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	1
95	51	2	HR	PENDING	\N	\N	\N	\N	DESIGNATION_OR_RBAC	\N	\N
97	52	1	MANAGER	APPROVED	Lina Ahmed	\N	2026-11-10 08:40:02.534492+00	4	REPORTING_MANAGER	\N	4
98	53	1	MANAGER	REJECTED	Lina Ahmed	QA reject — not enough cover	2026-11-10 08:40:02.790227+00	4	REPORTING_MANAGER	\N	4
99	54	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	4
100	55	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	1
101	56	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	1
102	57	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	1
103	58	1	MANAGER	PENDING	\N	\N	\N	\N	REPORTING_MANAGER	\N	1
108	61	1	MANAGER	APPROVED	Lina Ahmed	\N	2026-08-26 04:00:00+00	4	\N	\N	\N
109	61	2	HR	APPROVED	Nadia Chowdhury	\N	2026-08-26 04:00:00+00	2	\N	\N	\N
110	62	1	MANAGER	APPROVED	Lina Ahmed	\N	2026-10-06 04:00:00+00	4	\N	\N	\N
111	62	2	HR	APPROVED	Nadia Chowdhury	\N	2026-10-06 04:00:00+00	2	\N	\N	\N
\.


ALTER TABLE public.leave_approval ENABLE TRIGGER ALL;

--
-- Data for Name: leave_audit_log; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.leave_audit_log DISABLE TRIGGER ALL;

COPY public.leave_audit_log (id, at, actor_employee_id, action, entity, entity_id, before_json, after_json, ip) FROM stdin;
194	2026-11-10 06:48:42.490829+00	1	RENEW	LeaveCategory	1	{"sourceCode": "CASUAL"}	{"newId": 11, "newCode": "CASUAL_2027"}	\N
311	2026-11-10 07:20:36.334255+00	2	CREATE	LeaveCategory	21	\N	{"code": "QA_NT_MWAD20SK", "name": "QA Notice", "periodEnd": "2026-12-31", "periodStart": "2026-01-01"}	\N
312	2026-11-10 07:20:36.542296+00	6	CREATE	LeaveApplication	37	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-08", "startDate": "2026-12-08", "employeeId": 6, "leaveTypeCode": "QA_NT_MWAD20SK"}	\N
313	2026-11-10 07:20:36.639373+00	6	WITHDRAW	LeaveApplication	37	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
363	2026-11-10 08:19:28.188981+00	2	UPDATE	LeaveCategory	1	{"name": "Casual Leave"}	{"name": "Casual Leave", "quotaDays": 10}	\N
389	2026-11-10 08:42:23.22162+00	16	WITHDRAW	LeaveApplication	55	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
390	2026-11-10 08:44:24.236231+00	16	CREATE	LeaveApplication	56	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-10", "startDate": "2026-11-10", "employeeId": 16, "leaveTypeCode": "QA_LV_MWAD5GLJ"}	\N
97	2026-10-30 11:08:16.655792+00	6	WITHDRAW	LeaveApplication	21	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
195	2026-11-10 06:59:14.340882+00	2	CREATE	LeaveCategory	12	\N	{"code": "QA_LV_MWACAH2G", "name": "QA Leave Setup", "periodEnd": "2026-12-31", "periodStart": "2026-01-01"}	\N
196	2026-11-10 06:59:14.46225+00	2	CASCADE_QUOTA	LeaveBalance	1:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
197	2026-11-10 06:59:14.469443+00	2	CASCADE_QUOTA	LeaveBalance	15:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
198	2026-11-10 06:59:14.480649+00	2	CASCADE_QUOTA	LeaveBalance	16:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
199	2026-11-10 06:59:14.48756+00	2	CASCADE_QUOTA	LeaveBalance	2:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
200	2026-11-10 06:59:14.516531+00	2	CASCADE_QUOTA	LeaveBalance	19:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
201	2026-11-10 06:59:14.524209+00	2	CASCADE_QUOTA	LeaveBalance	6:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
202	2026-11-10 06:59:14.546019+00	2	CASCADE_QUOTA	LeaveBalance	10:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
203	2026-11-10 06:59:14.552656+00	2	CASCADE_QUOTA	LeaveBalance	21:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
204	2026-11-10 06:59:14.565279+00	2	CASCADE_QUOTA	LeaveBalance	17:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
205	2026-11-10 06:59:14.574076+00	2	CASCADE_QUOTA	LeaveBalance	20:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
31	2026-10-30 09:04:52.162747+00	12	CREATE	LeaveApplication	3	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-06", "startDate": "2026-11-06", "employeeId": 12, "leaveTypeCode": "CASUAL"}	\N
206	2026-11-10 06:59:14.58329+00	2	CASCADE_QUOTA	LeaveBalance	9:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
207	2026-11-10 06:59:14.594144+00	2	CASCADE_QUOTA	LeaveBalance	3:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
208	2026-11-10 06:59:14.605867+00	2	CASCADE_QUOTA	LeaveBalance	18:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
209	2026-11-10 06:59:14.613412+00	2	CASCADE_QUOTA	LeaveBalance	12:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
210	2026-11-10 06:59:14.633252+00	2	CASCADE_QUOTA	LeaveBalance	8:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
211	2026-11-10 06:59:14.654085+00	2	CASCADE_QUOTA	LeaveBalance	5:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
212	2026-11-10 06:59:14.677869+00	2	CASCADE_QUOTA	LeaveBalance	14:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
213	2026-11-10 06:59:14.697979+00	2	CASCADE_QUOTA	LeaveBalance	4:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
214	2026-11-10 06:59:14.717908+00	2	CASCADE_QUOTA	LeaveBalance	7:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
215	2026-11-10 06:59:14.741201+00	2	CASCADE_QUOTA	LeaveBalance	23:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
216	2026-11-10 06:59:14.757536+00	2	CASCADE_QUOTA	LeaveBalance	26:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
217	2026-11-10 06:59:14.766026+00	2	CASCADE_QUOTA	LeaveBalance	27:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
218	2026-11-10 06:59:14.777153+00	2	CASCADE_QUOTA	LeaveBalance	28:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
219	2026-11-10 06:59:14.79972+00	2	CASCADE_QUOTA	LeaveBalance	24:QA_LV_MWACAH2G:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
220	2026-11-10 06:59:14.805868+00	2	CASCADE_QUOTA	LeaveCategory	12	{"year": 2026, "oldQuota": 10.00, "periodEnd": "2026-12-31", "periodStart": "2026-01-01"}	{"delta": 2.00, "newQuota": 12, "employeesAffected": 24}	\N
221	2026-11-10 06:59:15.173874+00	2	ADJUST	LeaveBalance	6:QA_LV_MWACAH2G:2026	{"balanceDays": 12.00}	{"reason": "QA adjustment credit", "deltaDays": 1.5, "balanceDays": 13.50}	\N
314	2026-11-10 07:23:15.31602+00	2	ADJUST	LeaveBalance	6:CASUAL:2026	{"balanceDays": 5.00}	{"reason": "QA top-up apply flow", "deltaDays": 3, "balanceDays": 8.00}	\N
315	2026-11-10 07:23:15.458719+00	6	CREATE	LeaveApplication	38	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-06", "startDate": "2026-12-06", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
316	2026-11-10 07:23:15.786308+00	6	CANCEL_REQUEST	LeaveApplication	38	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
317	2026-11-10 07:23:15.989987+00	6	CREATE	LeaveApplication	39	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-20", "startDate": "2026-12-20", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
318	2026-11-10 07:23:16.259884+00	6	CREATE	LeaveApplication	40	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-30", "startDate": "2026-12-30", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
319	2026-11-10 07:23:16.315441+00	6	WITHDRAW	LeaveApplication	40	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
320	2026-11-10 07:23:17.021769+00	2	CREATE	LeaveCategory	22	\N	{"code": "QA_LV_MWAD5GLJ", "name": "QA Leave Setup", "periodEnd": "2026-12-31", "periodStart": "2026-01-01"}	\N
321	2026-11-10 07:23:17.089304+00	2	CASCADE_QUOTA	LeaveBalance	1:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
322	2026-11-10 07:23:17.097374+00	2	CASCADE_QUOTA	LeaveBalance	15:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
323	2026-11-10 07:23:17.109025+00	2	CASCADE_QUOTA	LeaveBalance	16:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
324	2026-11-10 07:23:17.130084+00	2	CASCADE_QUOTA	LeaveBalance	2:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
222	2026-11-10 06:59:15.984972+00	2	RENEW	LeaveCategory	12	{"sourceId": 12, "sourceCode": "QA_LV_MWACAH2G"}	{"code": "QA_LV_MWACAH2G", "name": "QA Leave Setup", "newId": 13, "periodEnd": "2027-12-31", "periodStart": "2027-01-01"}	\N
325	2026-11-10 07:23:17.152447+00	2	CASCADE_QUOTA	LeaveBalance	19:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
326	2026-11-10 07:23:17.15957+00	2	CASCADE_QUOTA	LeaveBalance	6:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
327	2026-11-10 07:23:17.172951+00	2	CASCADE_QUOTA	LeaveBalance	10:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
328	2026-11-10 07:23:17.192375+00	2	CASCADE_QUOTA	LeaveBalance	21:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
329	2026-11-10 07:23:17.20316+00	2	CASCADE_QUOTA	LeaveBalance	17:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
330	2026-11-10 07:23:17.215976+00	2	CASCADE_QUOTA	LeaveBalance	20:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
331	2026-11-10 07:23:17.22217+00	2	CASCADE_QUOTA	LeaveBalance	9:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
332	2026-11-10 07:23:17.243992+00	2	CASCADE_QUOTA	LeaveBalance	3:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
333	2026-11-10 07:23:17.256577+00	2	CASCADE_QUOTA	LeaveBalance	18:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
334	2026-11-10 07:23:17.274707+00	2	CASCADE_QUOTA	LeaveBalance	12:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
335	2026-11-10 07:23:17.288264+00	2	CASCADE_QUOTA	LeaveBalance	8:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
336	2026-11-10 07:23:17.297151+00	2	CASCADE_QUOTA	LeaveBalance	5:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
337	2026-11-10 07:23:17.303394+00	2	CASCADE_QUOTA	LeaveBalance	14:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
52	2026-10-30 09:25:51.631803+00	14	CREATE	LeaveApplication	4	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-02", "startDate": "2026-11-02", "employeeId": 14, "leaveTypeCode": "SICK"}	\N
338	2026-11-10 07:23:17.325981+00	2	CASCADE_QUOTA	LeaveBalance	4:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
339	2026-11-10 07:23:17.336849+00	2	CASCADE_QUOTA	LeaveBalance	7:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
55	2026-10-30 09:48:14.105699+00	6	CREATE	LeaveApplication	5	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-07", "startDate": "2026-12-07", "employeeId": 6, "leaveTypeCode": "ANNUAL"}	\N
56	2026-10-30 09:49:03.114022+00	6	CREATE	LeaveApplication	6	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-07", "startDate": "2026-12-07", "employeeId": 6, "leaveTypeCode": "ANNUAL"}	\N
98	2026-10-30 11:08:16.754478+00	1	RENEW	LeaveCategory	6	{"sourceCode": "ANNUAL"}	{"newId": 7, "newCode": "ANNUAL_2029"}	\N
340	2026-11-10 07:23:17.357762+00	2	CASCADE_QUOTA	LeaveBalance	23:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
341	2026-11-10 07:23:17.371466+00	2	CASCADE_QUOTA	LeaveBalance	26:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
342	2026-11-10 07:23:17.392972+00	2	CASCADE_QUOTA	LeaveBalance	27:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
343	2026-11-10 07:23:17.41864+00	2	CASCADE_QUOTA	LeaveBalance	28:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
344	2026-11-10 07:23:17.447871+00	2	CASCADE_QUOTA	LeaveBalance	24:QA_LV_MWAD5GLJ:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
345	2026-11-10 07:23:17.455974+00	2	CASCADE_QUOTA	LeaveCategory	22	{"year": 2026, "oldQuota": 10.00, "periodEnd": "2026-12-31", "periodStart": "2026-01-01"}	{"delta": 2.00, "newQuota": 12, "employeesAffected": 24}	\N
346	2026-11-10 07:23:17.684372+00	2	ADJUST	LeaveBalance	6:QA_LV_MWAD5GLJ:2026	{"balanceDays": 12.00}	{"reason": "QA adjustment credit", "deltaDays": 1.5, "balanceDays": 13.50}	\N
348	2026-11-10 07:23:26.970026+00	2	UPDATE	LeaveCategory	1	{"name": "Casual Leave"}	{"name": "Casual Leave", "quotaDays": 10}	\N
349	2026-11-10 07:23:27.133133+00	2	UPDATE	LeaveCategory	1	{"name": "Casual Leave"}	{"name": "Casual Leave", "quotaDays": 10}	\N
364	2026-11-10 08:22:12.139183+00	2	ADJUST	LeaveBalance	6:CASUAL:2026	{"balanceDays": 14.00}	{"reason": "QA top-up apply flow", "deltaDays": 3, "balanceDays": 17.00}	\N
365	2026-11-10 08:22:12.307631+00	6	CREATE	LeaveApplication	47	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-06", "startDate": "2026-12-06", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
366	2026-11-10 08:22:12.894014+00	6	CANCEL_REQUEST	LeaveApplication	47	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
367	2026-11-10 08:22:13.127116+00	6	CREATE	LeaveApplication	48	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-20", "startDate": "2026-12-20", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
368	2026-11-10 08:22:13.442616+00	6	CREATE	LeaveApplication	49	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-30", "startDate": "2026-12-30", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
369	2026-11-10 08:22:13.498888+00	6	WITHDRAW	LeaveApplication	49	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
370	2026-11-10 08:22:24.144098+00	2	UPDATE	LeaveCategory	1	{"name": "Casual Leave"}	{"name": "Casual Leave", "quotaDays": 10}	\N
371	2026-11-10 08:22:24.285217+00	2	UPDATE	LeaveCategory	20	{"name": "QA Always Attach"}	{"name": "QA Always Attach", "quotaDays": 5}	\N
372	2026-11-10 08:24:37.446368+00	1	UPDATE	LeaveCategory	19	{"name": "QA Leave Setup"}	{"name": "QA Leave Setup", "quotaDays": 12}	\N
374	2026-11-10 08:26:12.184559+00	1	UPDATE	LeaveCategory	19	{"name": "QA Leave Setup"}	{"name": "QA Leave Setup", "quotaDays": 12}	\N
223	2026-11-10 07:00:34.451572+00	2	CREATE	LeaveCategory	14	\N	{"code": "QA_LV_MWACC91R", "name": "QA Leave Setup", "periodEnd": "2026-12-31", "periodStart": "2026-01-01"}	\N
224	2026-11-10 07:00:34.556657+00	2	CASCADE_QUOTA	LeaveBalance	1:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
225	2026-11-10 07:00:34.56505+00	2	CASCADE_QUOTA	LeaveBalance	15:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
226	2026-11-10 07:00:34.573264+00	2	CASCADE_QUOTA	LeaveBalance	16:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
227	2026-11-10 07:00:34.580455+00	2	CASCADE_QUOTA	LeaveBalance	2:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
228	2026-11-10 07:00:34.587225+00	2	CASCADE_QUOTA	LeaveBalance	19:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
229	2026-11-10 07:00:34.596374+00	2	CASCADE_QUOTA	LeaveBalance	6:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
230	2026-11-10 07:00:34.603324+00	2	CASCADE_QUOTA	LeaveBalance	10:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
231	2026-11-10 07:00:34.610526+00	2	CASCADE_QUOTA	LeaveBalance	21:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
232	2026-11-10 07:00:34.618272+00	2	CASCADE_QUOTA	LeaveBalance	17:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
233	2026-11-10 07:00:34.626503+00	2	CASCADE_QUOTA	LeaveBalance	20:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
234	2026-11-10 07:00:34.634105+00	2	CASCADE_QUOTA	LeaveBalance	9:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
235	2026-11-10 07:00:34.642707+00	2	CASCADE_QUOTA	LeaveBalance	3:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
236	2026-11-10 07:00:34.756603+00	2	CASCADE_QUOTA	LeaveBalance	18:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
237	2026-11-10 07:00:34.763829+00	2	CASCADE_QUOTA	LeaveBalance	12:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
238	2026-11-10 07:00:34.770243+00	2	CASCADE_QUOTA	LeaveBalance	8:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
239	2026-11-10 07:00:34.779878+00	2	CASCADE_QUOTA	LeaveBalance	5:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
240	2026-11-10 07:00:34.786023+00	2	CASCADE_QUOTA	LeaveBalance	14:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
241	2026-11-10 07:00:34.793278+00	2	CASCADE_QUOTA	LeaveBalance	4:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
242	2026-11-10 07:00:34.799548+00	2	CASCADE_QUOTA	LeaveBalance	7:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
243	2026-11-10 07:00:34.805418+00	2	CASCADE_QUOTA	LeaveBalance	23:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
244	2026-11-10 07:00:34.811675+00	2	CASCADE_QUOTA	LeaveBalance	26:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
245	2026-11-10 07:00:34.81823+00	2	CASCADE_QUOTA	LeaveBalance	27:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
246	2026-11-10 07:00:34.823987+00	2	CASCADE_QUOTA	LeaveBalance	28:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
247	2026-11-10 07:00:34.830479+00	2	CASCADE_QUOTA	LeaveBalance	24:QA_LV_MWACC91R:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
248	2026-11-10 07:00:34.834334+00	2	CASCADE_QUOTA	LeaveCategory	14	{"year": 2026, "oldQuota": 10.00, "periodEnd": "2026-12-31", "periodStart": "2026-01-01"}	{"delta": 2.00, "newQuota": 12, "employeesAffected": 24}	\N
249	2026-11-10 07:00:35.072782+00	2	ADJUST	LeaveBalance	6:QA_LV_MWACC91R:2026	{"balanceDays": 12.00}	{"reason": "QA adjustment credit", "deltaDays": 1.5, "balanceDays": 13.50}	\N
251	2026-11-10 07:01:16.120931+00	2	CREATE	LeaveCategory	16	\N	{"code": "QA_LV_MWACD3QU", "name": "QA Leave Setup", "periodEnd": "2026-12-31", "periodStart": "2026-01-01"}	\N
252	2026-11-10 07:01:16.187057+00	2	CASCADE_QUOTA	LeaveBalance	1:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
253	2026-11-10 07:01:16.195722+00	2	CASCADE_QUOTA	LeaveBalance	15:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
254	2026-11-10 07:01:16.203006+00	2	CASCADE_QUOTA	LeaveBalance	16:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
255	2026-11-10 07:01:16.212188+00	2	CASCADE_QUOTA	LeaveBalance	2:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
256	2026-11-10 07:01:16.220087+00	2	CASCADE_QUOTA	LeaveBalance	19:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
257	2026-11-10 07:01:16.227039+00	2	CASCADE_QUOTA	LeaveBalance	6:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
258	2026-11-10 07:01:16.235187+00	2	CASCADE_QUOTA	LeaveBalance	10:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
259	2026-11-10 07:01:16.241938+00	2	CASCADE_QUOTA	LeaveBalance	21:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
260	2026-11-10 07:01:16.25197+00	2	CASCADE_QUOTA	LeaveBalance	17:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
261	2026-11-10 07:01:16.257607+00	2	CASCADE_QUOTA	LeaveBalance	20:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
262	2026-11-10 07:01:16.263542+00	2	CASCADE_QUOTA	LeaveBalance	9:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
99	2026-10-30 11:08:17.348082+00	6	CREATE	LeaveApplication	22	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-20", "startDate": "2026-12-20", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
100	2026-10-30 11:08:17.409816+00	6	WITHDRAW	LeaveApplication	22	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
250	2026-11-10 07:00:35.863819+00	2	RENEW	LeaveCategory	14	{"sourceId": 14, "sourceCode": "QA_LV_MWACC91R"}	{"code": "QA_LV_MWACC91R", "name": "QA Leave Setup", "newId": 15, "periodEnd": "2027-12-31", "periodStart": "2027-01-01"}	\N
278	2026-11-10 07:01:17.040375+00	2	RENEW	LeaveCategory	16	{"sourceId": 16, "sourceCode": "QA_LV_MWACD3QU"}	{"code": "QA_LV_MWACD3QU", "name": "QA Leave Setup", "newId": 17, "periodEnd": "2027-12-31", "periodStart": "2027-01-01"}	\N
347	2026-11-10 07:23:18.066048+00	2	RENEW	LeaveCategory	22	{"sourceId": 22, "sourceCode": "QA_LV_MWAD5GLJ"}	{"code": "QA_LV_MWAD5GLJ", "name": "QA Leave Setup", "newId": 23, "periodEnd": "2027-12-31", "periodStart": "2027-01-01"}	\N
373	2026-11-10 08:25:19.741185+00	1	UPDATE	LeaveCategory	19	{"name": "QA Leave Setup"}	{"name": "QA Leave Setup", "quotaDays": 12}	\N
375	2026-11-10 08:27:32.520809+00	1	UPDATE	LeaveCategory	19	{"name": "QA Leave Setup"}	{"name": "QA Leave Setup", "quotaDays": 12}	\N
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
376	2026-11-10 08:27:58.893159+00	1	UPDATE	LeaveCategory	18	{"name": "QA Leave Setup"}	{"name": "QA Leave Setup", "quotaDays": 12}	\N
377	2026-11-10 08:29:21.799918+00	1	UPDATE	LeaveCategory	18	{"name": "QA Leave Setup"}	{"name": "QA Leave Setup", "quotaDays": 12}	\N
95	2026-10-30 11:08:16.215925+00	6	CANCEL_REQUEST	LeaveApplication	20	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
96	2026-10-30 11:08:16.567608+00	6	CREATE	LeaveApplication	21	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-06", "startDate": "2026-12-06", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
263	2026-11-10 07:01:16.271013+00	2	CASCADE_QUOTA	LeaveBalance	3:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
264	2026-11-10 07:01:16.281151+00	2	CASCADE_QUOTA	LeaveBalance	18:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
265	2026-11-10 07:01:16.289861+00	2	CASCADE_QUOTA	LeaveBalance	12:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
266	2026-11-10 07:01:16.300331+00	2	CASCADE_QUOTA	LeaveBalance	8:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
267	2026-11-10 07:01:16.308303+00	2	CASCADE_QUOTA	LeaveBalance	5:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
107	2026-10-30 12:12:16.607642+00	1	EXPORT	LeaveBalances	\N	\N	{"rows": 21, "year": 2026}	\N
108	2026-10-30 12:12:16.715494+00	1	EXPORT	LeaveRequests	\N	\N	{"to": "2026-10-30", "from": "2026-01-01", "rows": 0}	\N
268	2026-11-10 07:01:16.315381+00	2	CASCADE_QUOTA	LeaveBalance	14:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
269	2026-11-10 07:01:16.323208+00	2	CASCADE_QUOTA	LeaveBalance	4:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
270	2026-11-10 07:01:16.332017+00	2	CASCADE_QUOTA	LeaveBalance	7:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
271	2026-11-10 07:01:16.3396+00	2	CASCADE_QUOTA	LeaveBalance	23:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
272	2026-11-10 07:01:16.347105+00	2	CASCADE_QUOTA	LeaveBalance	26:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
273	2026-11-10 07:01:16.354992+00	2	CASCADE_QUOTA	LeaveBalance	27:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
274	2026-11-10 07:01:16.362002+00	2	CASCADE_QUOTA	LeaveBalance	28:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
119	2026-10-30 12:12:34.935472+00	1	EXPORT	LeaveBalances	\N	\N	{"rows": 21, "year": 2026}	\N
120	2026-10-30 12:12:35.000177+00	1	EXPORT	LeaveRequests	\N	\N	{"to": "2026-10-30", "from": "2026-01-01", "rows": 0}	\N
275	2026-11-10 07:01:16.368723+00	2	CASCADE_QUOTA	LeaveBalance	24:QA_LV_MWACD3QU:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
276	2026-11-10 07:01:16.374185+00	2	CASCADE_QUOTA	LeaveCategory	16	{"year": 2026, "oldQuota": 10.00, "periodEnd": "2026-12-31", "periodStart": "2026-01-01"}	{"delta": 2.00, "newQuota": 12, "employeesAffected": 24}	\N
123	2026-10-30 12:18:06.567096+00	1	RENEW	LeaveCategory	8	{"sourceCode": "HUDAI"}	{"newId": 9, "newCode": "HUDAI_2027"}	\N
124	2026-10-30 12:40:00.911275+00	1	CREATE	LeaveCategory	10	\N	{"code": "TEST_64000", "name": "Test Leave TEST_64000"}	\N
125	2026-10-30 14:00:22.527559+00	9	EXPORT	LeaveBalances	\N	\N	{"rows": 1, "year": 2026}	\N
126	2026-10-30 14:07:39.949833+00	1	EXPORT	LeaveBalances	\N	\N	{"rows": 21, "year": 2026}	\N
277	2026-11-10 07:01:16.583831+00	2	ADJUST	LeaveBalance	6:QA_LV_MWACD3QU:2026	{"balanceDays": 12.00}	{"reason": "QA adjustment credit", "deltaDays": 1.5, "balanceDays": 13.50}	\N
350	2026-11-10 07:24:00.744661+00	2	ADJUST	LeaveBalance	6:CASUAL:2026	{"balanceDays": 8.00}	{"reason": "QA top-up apply flow", "deltaDays": 3, "balanceDays": 11.00}	\N
351	2026-11-10 07:24:01.061028+00	6	CREATE	LeaveApplication	41	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-06", "startDate": "2026-12-06", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
352	2026-11-10 07:24:01.71493+00	6	CANCEL_REQUEST	LeaveApplication	41	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
353	2026-11-10 07:24:02.049978+00	6	CREATE	LeaveApplication	42	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-20", "startDate": "2026-12-20", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
354	2026-11-10 07:24:02.800338+00	6	CREATE	LeaveApplication	43	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-30", "startDate": "2026-12-30", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
355	2026-11-10 07:24:02.905344+00	6	WITHDRAW	LeaveApplication	43	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
356	2026-11-10 07:24:48.61765+00	2	ADJUST	LeaveBalance	6:CASUAL:2026	{"balanceDays": 11.00}	{"reason": "QA top-up apply flow", "deltaDays": 3, "balanceDays": 14.00}	\N
357	2026-11-10 07:24:48.749935+00	6	CREATE	LeaveApplication	44	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-06", "startDate": "2026-12-06", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
358	2026-11-10 07:24:48.958455+00	6	CANCEL_REQUEST	LeaveApplication	44	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
359	2026-11-10 07:24:49.105978+00	6	CREATE	LeaveApplication	45	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-20", "startDate": "2026-12-20", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
360	2026-11-10 07:24:49.398268+00	6	CREATE	LeaveApplication	46	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-30", "startDate": "2026-12-30", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
361	2026-11-10 07:24:49.443785+00	6	WITHDRAW	LeaveApplication	46	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
378	2026-11-10 08:29:58.748261+00	4	CREATE	LeaveApplication	50	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-10", "startDate": "2026-11-10", "employeeId": 4, "leaveTypeCode": "QA_LV_MWAD0SF2"}	\N
379	2026-11-10 08:36:44.049937+00	2	UPDATE	LeaveCategory	18	{"name": "QA Leave Setup"}	{"name": "QA Leave Setup", "quotaDays": 12}	\N
279	2026-11-10 07:19:39.139662+00	2	CREATE	LeaveCategory	18	\N	{"code": "QA_LV_MWAD0SF2", "name": "QA Leave Setup", "periodEnd": "2026-12-31", "periodStart": "2026-01-01"}	\N
280	2026-11-10 07:19:39.227771+00	2	CASCADE_QUOTA	LeaveBalance	1:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
281	2026-11-10 07:19:39.236498+00	2	CASCADE_QUOTA	LeaveBalance	15:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
282	2026-11-10 07:19:39.248699+00	2	CASCADE_QUOTA	LeaveBalance	16:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
283	2026-11-10 07:19:39.255626+00	2	CASCADE_QUOTA	LeaveBalance	2:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
284	2026-11-10 07:19:39.264734+00	2	CASCADE_QUOTA	LeaveBalance	19:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
285	2026-11-10 07:19:39.271168+00	2	CASCADE_QUOTA	LeaveBalance	6:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
286	2026-11-10 07:19:39.278292+00	2	CASCADE_QUOTA	LeaveBalance	10:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
153	2026-11-03 06:32:11.359696+00	8	CREATE	LeaveApplication	23	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-04", "startDate": "2026-11-04", "employeeId": 8, "leaveTypeCode": "CASUAL"}	\N
287	2026-11-10 07:19:39.287833+00	2	CASCADE_QUOTA	LeaveBalance	21:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
288	2026-11-10 07:19:39.295012+00	2	CASCADE_QUOTA	LeaveBalance	17:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
289	2026-11-10 07:19:39.301583+00	2	CASCADE_QUOTA	LeaveBalance	20:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
157	2026-11-03 09:24:45.140598+00	8	CREATE	LeaveApplication	24	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-12", "startDate": "2026-11-10", "employeeId": 8, "leaveTypeCode": "ANNUAL"}	\N
158	2026-11-03 09:25:52.448358+00	8	CANCEL_REQUEST	LeaveApplication	24	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
159	2026-11-03 11:56:03.181913+00	6	CREATE	LeaveApplication	25	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-01", "startDate": "2026-12-01", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
160	2026-11-03 11:56:04.162243+00	6	CANCEL_REQUEST	LeaveApplication	25	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
161	2026-11-03 11:56:04.687432+00	6	CANCEL_WITHDRAW	LeaveApplication	25	{"status": "PENDING_CANCEL"}	{"status": "APPROVED"}	\N
162	2026-11-03 11:56:04.954803+00	6	CANCEL_REQUEST	LeaveApplication	25	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
163	2026-11-03 11:56:05.329061+00	6	CANCEL_REQUEST	LeaveApplication	25	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
164	2026-11-03 11:56:05.67867+00	6	CREATE	LeaveApplication	26	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-03", "startDate": "2026-12-02", "employeeId": 6, "leaveTypeCode": "ANNUAL"}	\N
290	2026-11-10 07:19:39.30942+00	2	CASCADE_QUOTA	LeaveBalance	9:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
291	2026-11-10 07:19:39.31606+00	2	CASCADE_QUOTA	LeaveBalance	3:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
292	2026-11-10 07:19:39.322108+00	2	CASCADE_QUOTA	LeaveBalance	18:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
293	2026-11-10 07:19:39.330225+00	2	CASCADE_QUOTA	LeaveBalance	12:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
294	2026-11-10 07:19:39.337049+00	2	CASCADE_QUOTA	LeaveBalance	8:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
295	2026-11-10 07:19:39.346034+00	2	CASCADE_QUOTA	LeaveBalance	5:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
296	2026-11-10 07:19:39.354604+00	2	CASCADE_QUOTA	LeaveBalance	14:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
297	2026-11-10 07:19:39.360125+00	2	CASCADE_QUOTA	LeaveBalance	4:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
298	2026-11-10 07:19:39.368791+00	2	CASCADE_QUOTA	LeaveBalance	7:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
299	2026-11-10 07:19:39.377761+00	2	CASCADE_QUOTA	LeaveBalance	23:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
300	2026-11-10 07:19:39.384039+00	2	CASCADE_QUOTA	LeaveBalance	26:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
301	2026-11-10 07:19:39.394346+00	2	CASCADE_QUOTA	LeaveBalance	27:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
302	2026-11-10 07:19:39.40787+00	2	CASCADE_QUOTA	LeaveBalance	28:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
303	2026-11-10 07:19:39.420623+00	2	CASCADE_QUOTA	LeaveBalance	24:QA_LV_MWAD0SF2:2026	{"balanceDays": 10.00}	{"deltaDays": 2.00, "balanceDays": 12.00}	\N
304	2026-11-10 07:19:39.423876+00	2	CASCADE_QUOTA	LeaveCategory	18	{"year": 2026, "oldQuota": 10.00, "periodEnd": "2026-12-31", "periodStart": "2026-01-01"}	{"delta": 2.00, "newQuota": 12, "employeesAffected": 24}	\N
305	2026-11-10 07:19:39.739417+00	2	ADJUST	LeaveBalance	6:QA_LV_MWAD0SF2:2026	{"balanceDays": 12.00}	{"reason": "QA adjustment credit", "deltaDays": 1.5, "balanceDays": 13.50}	\N
307	2026-11-10 07:20:09.07486+00	2	ADJUST	LeaveBalance	6:CASUAL:2026	{"balanceDays": 0.00}	{"reason": "QA top-up for apply flow", "deltaDays": 5, "balanceDays": 5.00}	\N
308	2026-11-10 07:20:09.632655+00	2	CREATE	LeaveCategory	20	\N	{"code": "QA_ATT_95209", "name": "QA Attach Always", "periodEnd": "2026-12-31", "periodStart": "2026-01-01"}	\N
309	2026-11-10 07:20:18.568647+00	6	CREATE	LeaveApplication	36	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-01", "startDate": "2026-12-01", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
310	2026-11-10 07:20:19.294035+00	6	CANCEL_REQUEST	LeaveApplication	36	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
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
306	2026-11-10 07:19:40.185425+00	2	RENEW	LeaveCategory	18	{"sourceId": 18, "sourceCode": "QA_LV_MWAD0SF2"}	{"code": "QA_LV_MWAD0SF2", "name": "QA Leave Setup", "newId": 19, "periodEnd": "2027-12-31", "periodStart": "2027-01-01"}	\N
362	2026-11-10 08:15:44.655469+00	1	UPDATE	LeaveCategory	15	{"name": "QA Leave Setup"}	{"name": "QA Leave Setup", "quotaDays": 12}	\N
380	2026-11-10 08:39:59.871162+00	4	CREATE	LeaveApplication	51	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-15", "startDate": "2026-11-15", "employeeId": 4, "leaveTypeCode": "QA_LV_MWAD0SF2"}	\N
381	2026-11-10 08:40:00.317741+00	4	WITHDRAW	LeaveApplication	51	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
382	2026-11-10 08:40:02.078363+00	2	ADJUST	LeaveBalance	6:CASUAL:2026	{"balanceDays": 17.00}	{"reason": "QA top-up apply flow", "deltaDays": 3, "balanceDays": 20.00}	\N
383	2026-11-10 08:40:02.243195+00	6	CREATE	LeaveApplication	52	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-06", "startDate": "2026-12-06", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
384	2026-11-10 08:40:02.485295+00	6	CANCEL_REQUEST	LeaveApplication	52	{"status": "APPROVED"}	{"status": "PENDING_CANCEL"}	\N
385	2026-11-10 08:40:02.682004+00	6	CREATE	LeaveApplication	53	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-20", "startDate": "2026-12-20", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
386	2026-11-10 08:40:03.060912+00	6	CREATE	LeaveApplication	54	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-12-30", "startDate": "2026-12-30", "employeeId": 6, "leaveTypeCode": "CASUAL"}	\N
387	2026-11-10 08:40:03.1463+00	6	WITHDRAW	LeaveApplication	54	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
388	2026-11-10 08:42:07.240672+00	16	CREATE	LeaveApplication	55	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-10", "startDate": "2026-11-10", "employeeId": 16, "leaveTypeCode": "QA_LV_MWACAH2G"}	\N
391	2026-11-10 08:44:32.834715+00	16	CREATE	LeaveApplication	57	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-11", "startDate": "2026-11-11", "employeeId": 16, "leaveTypeCode": "QA_LV_MWAD5GLJ"}	\N
392	2026-11-10 08:45:13.743737+00	16	WITHDRAW	LeaveApplication	57	{"status": "PENDING_APPROVAL"}	{"status": "WITHDRAWN"}	\N
393	2026-11-10 08:45:26.001403+00	16	CREATE	LeaveApplication	58	\N	{"status": "PENDING_APPROVAL", "endDate": "2026-11-11", "startDate": "2026-11-11", "employeeId": 16, "leaveTypeCode": "QA_LV_MWAD5GLJ"}	\N
\.


ALTER TABLE public.leave_audit_log ENABLE TRIGGER ALL;

--
-- Data for Name: leave_balance; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.leave_balance DISABLE TRIGGER ALL;

COPY public.leave_balance (id, employee_id, leave_type_code, year, balance_days, updated_at, version) FROM stdin;
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
20	7	ANNUAL	2026	7.00	2026-11-04 11:51:38.534694+00	0
17	6	ANNUAL	2026	0.00	2026-11-04 09:34:56.399338+00	0
26	8	ANNUAL	2026	0.00	2026-11-08 19:00:41.66681+00	1
25	8	CASUAL	2026	0.00	2026-11-08 19:00:41.846896+00	1
14	12	ANNUAL	2026	0.00	2026-11-10 11:03:49.593262+00	2
19	7	CASUAL	2026	3.50	2026-11-10 11:03:49.600875+00	2
59	1	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
60	15	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
61	16	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
62	2	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
63	19	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
64	6	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
65	10	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
66	21	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
67	17	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
68	20	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
69	9	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
70	3	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
71	18	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
72	12	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
73	8	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
74	5	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
75	11	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
76	13	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
77	14	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
78	4	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
79	7	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
80	23	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
81	26	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
82	27	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
83	25	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
84	28	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
85	24	CASUAL_2027	2026	10.00	2026-11-10 06:48:41.620506+00	0
2	2	ANNUAL	2026	14.00	2026-11-10 10:49:59.228013+00	1
1	2	CASUAL	2026	10.00	2026-11-10 10:49:59.228013+00	1
16	6	CASUAL	2026	20.00	2026-11-10 08:40:02.55057+00	18
13	12	CASUAL	2026	0.00	2026-11-10 11:03:36.664682+00	0
22	10	CASUAL	2026	0.00	2026-11-10 11:03:36.664682+00	0
23	10	ANNUAL	2026	0.00	2026-11-10 11:03:36.664682+00	1
86	1	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
87	15	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
88	16	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
89	2	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
90	19	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
92	10	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
93	21	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
94	17	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
95	20	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
96	9	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
97	3	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
98	18	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
99	12	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
100	8	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
101	5	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
102	14	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
103	4	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
104	7	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
105	23	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
106	26	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
107	27	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
108	28	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
109	24	QA_LV_MWACAH2G	2026	12.00	2026-11-10 06:59:14.456535+00	1
91	6	QA_LV_MWACAH2G	2026	13.50	2026-11-10 06:59:15.171754+00	2
110	1	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
111	15	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
112	16	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
113	2	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
114	19	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
115	6	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
116	10	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
117	21	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
118	17	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
119	20	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
120	9	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
121	3	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
122	18	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
123	12	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
124	8	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
125	5	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
126	14	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
127	4	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
128	7	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
129	23	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
130	26	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
131	27	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
132	28	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
133	24	QA_LV_MWACAH2G	2027	12.00	2026-11-10 06:59:15.303062+00	0
134	1	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
135	15	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
136	16	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
137	2	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
138	19	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
140	10	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
141	21	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
142	17	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
143	20	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
144	9	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
145	3	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
146	18	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
147	12	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
148	8	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
149	5	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
139	6	QA_LV_MWACC91R	2026	13.50	2026-11-10 07:00:35.070243+00	2
150	14	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
151	4	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
152	7	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
153	23	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
154	26	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
155	27	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
156	28	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
157	24	QA_LV_MWACC91R	2026	12.00	2026-11-10 07:00:34.551573+00	1
158	1	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
159	15	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
160	16	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
161	2	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
162	19	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
163	6	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
164	10	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
165	21	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
166	17	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
167	20	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
168	9	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
169	3	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
170	18	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
171	12	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
172	8	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
173	5	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
174	14	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
175	4	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
176	7	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
177	23	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
178	26	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
179	27	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
180	28	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
181	24	QA_LV_MWACC91R	2027	12.00	2026-11-10 07:00:35.143831+00	0
182	1	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
183	15	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
184	16	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
185	2	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
186	19	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
228	28	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
188	10	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
189	21	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
190	17	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
191	20	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
192	9	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
193	3	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
194	18	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
195	12	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
196	8	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
197	5	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
198	14	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
199	4	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
200	7	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
201	23	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
202	26	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
203	27	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
204	28	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
205	24	QA_LV_MWACD3QU	2026	12.00	2026-11-10 07:01:16.184198+00	1
187	6	QA_LV_MWACD3QU	2026	13.50	2026-11-10 07:01:16.581586+00	2
206	1	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
207	15	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
208	16	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
209	2	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
210	19	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
211	6	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
212	10	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
213	21	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
214	17	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
215	20	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
216	9	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
217	3	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
218	18	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
219	12	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
220	8	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
221	5	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
222	14	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
223	4	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
224	7	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
225	23	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
226	26	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
227	27	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
229	24	QA_LV_MWACD3QU	2027	12.00	2026-11-10 07:01:16.651642+00	0
230	1	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
231	15	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
232	16	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
233	2	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
234	19	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
236	10	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
237	21	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
238	17	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
239	20	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
240	9	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
241	3	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
242	18	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
243	12	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
244	8	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
245	5	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
246	14	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
248	7	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
249	23	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
250	26	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
251	27	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
252	28	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
253	24	QA_LV_MWAD0SF2	2026	12.00	2026-11-10 07:19:39.221401+00	1
235	6	QA_LV_MWAD0SF2	2026	13.50	2026-11-10 07:19:39.734261+00	2
254	1	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
255	15	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
256	16	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
257	2	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
258	19	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
259	6	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
260	10	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
261	21	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
262	17	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
263	20	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
264	9	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
265	3	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
266	18	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
267	12	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
268	8	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
269	5	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
270	14	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
271	4	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
272	7	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
273	23	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
274	26	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
275	27	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
276	28	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
277	24	QA_LV_MWAD0SF2	2027	12.00	2026-11-10 07:19:39.822066+00	0
278	6	QA_ATT_95209	2026	5.00	2026-11-10 07:20:09.275402+00	0
279	6	QA_NT_MWAD20SK	2026	5.00	2026-11-10 07:20:36.002209+00	0
280	1	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
281	15	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
282	16	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
283	2	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
284	19	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
286	10	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
287	21	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
288	17	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
289	20	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
290	9	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
291	3	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
292	18	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
293	12	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
294	8	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
295	5	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
296	14	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
297	4	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
285	6	QA_LV_MWAD5GLJ	2026	13.50	2026-11-10 07:23:17.68166+00	2
247	4	QA_LV_MWAD0SF2	2026	11.00	2026-11-10 08:36:58.526093+00	2
298	7	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
299	23	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
300	26	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
301	27	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
302	28	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
303	24	QA_LV_MWAD5GLJ	2026	12.00	2026-11-10 07:23:17.086582+00	1
304	1	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
305	15	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
306	16	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
307	2	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
308	19	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
309	6	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
310	10	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
311	21	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
312	17	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
313	20	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
314	9	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
315	3	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
316	18	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
317	12	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
318	8	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
319	5	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
320	14	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
321	4	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
322	7	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
323	23	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
324	26	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
325	27	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
326	28	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
327	24	QA_LV_MWAD5GLJ	2027	12.00	2026-11-10 07:23:17.740428+00	0
329	31	ANNUAL	2026	0.00	2026-11-10 11:03:36.664682+00	0
330	31	SICK	2026	10.00	2026-11-10 11:03:36.664682+00	0
331	29	CASUAL	2026	10.00	2026-11-10 11:03:36.664682+00	0
332	29	ANNUAL	2026	14.00	2026-11-10 11:03:36.664682+00	0
333	29	SICK	2026	10.00	2026-11-10 11:03:36.664682+00	0
334	30	CASUAL	2026	5.00	2026-11-10 11:03:36.664682+00	0
335	30	ANNUAL	2026	0.00	2026-11-10 11:03:36.664682+00	0
336	30	SICK	2026	10.00	2026-11-10 11:03:36.664682+00	0
328	31	CASUAL	2026	0.00	2026-11-10 11:03:46.516163+00	6
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
65	1	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:1:CASUAL_2027:2026	OPENING	CASUAL_2027
66	15	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:15:CASUAL_2027:2026	OPENING	CASUAL_2027
67	16	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:16:CASUAL_2027:2026	OPENING	CASUAL_2027
68	2	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:2:CASUAL_2027:2026	OPENING	CASUAL_2027
69	19	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:19:CASUAL_2027:2026	OPENING	CASUAL_2027
70	6	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:6:CASUAL_2027:2026	OPENING	CASUAL_2027
71	10	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:10:CASUAL_2027:2026	OPENING	CASUAL_2027
72	21	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:21:CASUAL_2027:2026	OPENING	CASUAL_2027
73	17	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:17:CASUAL_2027:2026	OPENING	CASUAL_2027
74	20	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:20:CASUAL_2027:2026	OPENING	CASUAL_2027
75	9	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:9:CASUAL_2027:2026	OPENING	CASUAL_2027
76	3	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:3:CASUAL_2027:2026	OPENING	CASUAL_2027
77	18	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:18:CASUAL_2027:2026	OPENING	CASUAL_2027
78	12	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:12:CASUAL_2027:2026	OPENING	CASUAL_2027
79	8	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:8:CASUAL_2027:2026	OPENING	CASUAL_2027
80	5	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:5:CASUAL_2027:2026	OPENING	CASUAL_2027
81	11	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:11:CASUAL_2027:2026	OPENING	CASUAL_2027
82	13	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:13:CASUAL_2027:2026	OPENING	CASUAL_2027
83	14	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:14:CASUAL_2027:2026	OPENING	CASUAL_2027
84	4	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:4:CASUAL_2027:2026	OPENING	CASUAL_2027
85	7	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:7:CASUAL_2027:2026	OPENING	CASUAL_2027
86	23	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:23:CASUAL_2027:2026	OPENING	CASUAL_2027
87	26	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:26:CASUAL_2027:2026	OPENING	CASUAL_2027
88	27	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:27:CASUAL_2027:2026	OPENING	CASUAL_2027
89	25	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:25:CASUAL_2027:2026	OPENING	CASUAL_2027
90	28	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:28:CASUAL_2027:2026	OPENING	CASUAL_2027
91	24	CASUAL_2027	2026	10.00	Opening quota	\N	2026-11-10 06:48:41.620506+00	OPENING:24:CASUAL_2027:2026	OPENING	CASUAL_2027
92	1	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:1:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
93	15	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:15:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
94	16	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:16:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
95	2	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:2:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
96	19	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:19:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
97	6	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:6:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
98	10	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:10:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
99	21	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:21:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
100	17	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:17:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
101	20	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:20:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
102	9	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:9:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
103	3	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:3:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
104	18	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:18:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
105	12	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:12:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
106	8	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:8:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
107	5	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:5:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
108	14	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:14:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
109	4	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:4:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
110	7	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:7:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
111	23	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:23:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
112	26	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:26:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
113	27	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:27:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
114	28	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:28:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
115	24	QA_LV_MWACAH2G	2026	10.00	Opening quota	\N	2026-11-10 06:59:12.754405+00	OPENING:24:QA_LV_MWACAH2G:2026	OPENING	QA_LV_MWACAH2G
116	1	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:1:1794293954456:2626d732-211a-479c-ac5a-b13ddf51d359	QUOTA	QA_LV_MWACAH2G
117	15	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:15:1794293954456:dc166ced-d970-4dd3-a97f-dc4ae6d06946	QUOTA	QA_LV_MWACAH2G
118	16	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:16:1794293954456:c05960b3-34dd-49d6-81dc-704de69a094f	QUOTA	QA_LV_MWACAH2G
119	2	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:2:1794293954456:ad564f7d-8931-4133-b69b-92de661b9a1e	QUOTA	QA_LV_MWACAH2G
120	19	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:19:1794293954456:83f97c5f-4aa8-4189-9be2-8f96915cba72	QUOTA	QA_LV_MWACAH2G
121	6	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:6:1794293954456:0fed9726-16d1-418b-996a-6162662d9c5a	QUOTA	QA_LV_MWACAH2G
122	10	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:10:1794293954456:e97f02d9-42a5-4692-9ea5-820429c7113d	QUOTA	QA_LV_MWACAH2G
123	21	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:21:1794293954456:c49b962d-281a-4098-b547-310904ced530	QUOTA	QA_LV_MWACAH2G
124	17	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:17:1794293954456:dfc20a8f-77b4-4997-9f54-3cd16882b42c	QUOTA	QA_LV_MWACAH2G
125	20	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:20:1794293954456:bc8d5ea7-5802-43e3-956f-b568e7fa586b	QUOTA	QA_LV_MWACAH2G
126	9	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:9:1794293954456:caf479e3-bdf5-4542-a47a-7bb91adffeee	QUOTA	QA_LV_MWACAH2G
127	3	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:3:1794293954456:1661e0c6-1ce3-42e1-bcc4-9311900ee347	QUOTA	QA_LV_MWACAH2G
128	18	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:18:1794293954456:92ec2134-d940-4a22-a6d6-2dc227bbfa56	QUOTA	QA_LV_MWACAH2G
129	12	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:12:1794293954456:230ebf65-2b3d-412c-aa2e-15837086197d	QUOTA	QA_LV_MWACAH2G
130	8	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:8:1794293954456:9b641e65-759c-4a5c-b2e8-1cbd2a770414	QUOTA	QA_LV_MWACAH2G
131	5	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:5:1794293954456:8e0ba9a8-230a-49dd-9eca-f26551ab640c	QUOTA	QA_LV_MWACAH2G
132	14	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:14:1794293954456:4e4f1f22-b829-4ba3-a829-591ea4c9bc3d	QUOTA	QA_LV_MWACAH2G
133	4	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:4:1794293954456:845a1728-6d09-4996-a0cc-0e1184ee0ebf	QUOTA	QA_LV_MWACAH2G
134	7	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:7:1794293954456:acf586ec-d337-4c2a-b2a3-2cac8f08e135	QUOTA	QA_LV_MWACAH2G
135	23	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:23:1794293954456:475dfd27-5805-46f6-8e56-fad9b5600c8f	QUOTA	QA_LV_MWACAH2G
136	26	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:26:1794293954456:56ca9a07-0fa5-4a7f-a816-8de30c774c40	QUOTA	QA_LV_MWACAH2G
137	27	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:27:1794293954456:7bf2a857-b333-4cef-8588-4e09872c8442	QUOTA	QA_LV_MWACAH2G
138	28	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:28:1794293954456:4f915022-7171-43f5-92e9-1b8b65e22254	QUOTA	QA_LV_MWACAH2G
139	24	QA_LV_MWACAH2G	2026	2.00	Quota change mid-period	2	2026-11-10 06:59:14.456535+00	QUOTA:QA_LV_MWACAH2G:2026:24:1794293954456:5cb74624-ea7b-406e-846b-0c0c3cb1ced4	QUOTA	QA_LV_MWACAH2G
140	6	QA_LV_MWACAH2G	2026	1.50	QA adjustment credit	2	2026-11-10 06:59:15.171754+00	fbaf2001-638a-4a41-bee2-5d917b0ff340	ADJUST	6
141	1	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:1:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
142	15	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:15:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
143	16	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:16:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
144	2	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:2:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
145	19	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:19:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
146	6	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:6:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
147	10	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:10:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
148	21	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:21:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
149	17	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:17:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
150	20	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:20:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
151	9	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:9:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
152	3	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:3:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
153	18	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:18:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
154	12	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:12:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
155	8	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:8:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
156	5	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:5:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
157	14	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:14:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
158	4	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:4:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
159	7	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:7:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
160	23	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:23:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
161	26	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:26:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
162	27	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:27:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
163	28	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:28:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
164	24	QA_LV_MWACAH2G	2027	12.00	Opening quota	\N	2026-11-10 06:59:15.303062+00	OPENING:24:QA_LV_MWACAH2G:2027	OPENING	QA_LV_MWACAH2G
165	1	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:1:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
166	15	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:15:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
167	16	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:16:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
168	2	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:2:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
169	19	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:19:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
170	6	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:6:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
171	10	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:10:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
172	21	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:21:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
173	17	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:17:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
174	20	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:20:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
175	9	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:9:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
176	3	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:3:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
177	18	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:18:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
178	12	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:12:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
179	8	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:8:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
180	5	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:5:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
181	14	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:14:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
182	4	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:4:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
183	7	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:7:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
184	23	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:23:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
185	26	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:26:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
186	27	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:27:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
187	28	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:28:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
188	24	QA_LV_MWACC91R	2026	10.00	Opening quota	\N	2026-11-10 07:00:33.912459+00	OPENING:24:QA_LV_MWACC91R:2026	OPENING	QA_LV_MWACC91R
189	1	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:1:1794294034551:4d9f25ec-69aa-495f-ae29-29460b2101bc	QUOTA	QA_LV_MWACC91R
190	15	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:15:1794294034551:39651284-b18c-404d-a397-7935a2004554	QUOTA	QA_LV_MWACC91R
191	16	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:16:1794294034551:0f4b1853-f2c5-4700-bc0c-6eb0d916affb	QUOTA	QA_LV_MWACC91R
192	2	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:2:1794294034551:a50d4e89-3799-4af3-a543-4445cbf538d6	QUOTA	QA_LV_MWACC91R
193	19	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:19:1794294034551:6f921269-75fa-43ca-9096-02605c704146	QUOTA	QA_LV_MWACC91R
194	6	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:6:1794294034551:c31e4769-a35d-48c7-8a18-50f50c462713	QUOTA	QA_LV_MWACC91R
195	10	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:10:1794294034551:e126cb5c-377d-4600-b13c-1319099d44a9	QUOTA	QA_LV_MWACC91R
196	21	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:21:1794294034551:68ab0d95-8036-4ab6-be36-5977beea225c	QUOTA	QA_LV_MWACC91R
197	17	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:17:1794294034551:43fc79de-0eff-4dc1-9100-25988455cbe5	QUOTA	QA_LV_MWACC91R
198	20	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:20:1794294034551:bebf9df9-8efe-4966-ba77-58280dcdc514	QUOTA	QA_LV_MWACC91R
199	9	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:9:1794294034551:ceec05fc-d626-4999-8639-50155cc49d39	QUOTA	QA_LV_MWACC91R
200	3	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:3:1794294034551:cba07f01-d53a-4b6a-9371-6b77b38991e4	QUOTA	QA_LV_MWACC91R
201	18	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:18:1794294034551:66fdd15a-34be-447f-9d7d-562e709a618b	QUOTA	QA_LV_MWACC91R
202	12	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:12:1794294034551:21e593d6-5e49-43e0-a637-faa72000d364	QUOTA	QA_LV_MWACC91R
203	8	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:8:1794294034551:e715dc32-bc9f-42bf-8d1b-debe375cc9c7	QUOTA	QA_LV_MWACC91R
204	5	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:5:1794294034551:572673ed-9ebe-4e12-ac33-9197c5acf0c3	QUOTA	QA_LV_MWACC91R
205	14	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:14:1794294034551:f1156a0a-2e2b-4e54-91d5-f3ca21886b6f	QUOTA	QA_LV_MWACC91R
206	4	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:4:1794294034551:f8be103e-0ad1-466f-8c0c-a99bc6322a41	QUOTA	QA_LV_MWACC91R
207	7	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:7:1794294034551:33fc030a-d8fe-4387-a39b-865e9124754c	QUOTA	QA_LV_MWACC91R
208	23	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:23:1794294034551:41babbc0-0447-4ff7-9079-d6accb2e1f7b	QUOTA	QA_LV_MWACC91R
209	26	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:26:1794294034551:90c904a9-1595-4891-9651-20fd7e8fa267	QUOTA	QA_LV_MWACC91R
210	27	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:27:1794294034551:0042efb3-1b60-4423-b74b-d389cad5893e	QUOTA	QA_LV_MWACC91R
304	4	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:4:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
211	28	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:28:1794294034551:ab37900a-f22f-4d7a-9962-5932c6518ec1	QUOTA	QA_LV_MWACC91R
212	24	QA_LV_MWACC91R	2026	2.00	Quota change mid-period	2	2026-11-10 07:00:34.551573+00	QUOTA:QA_LV_MWACC91R:2026:24:1794294034551:b300a43f-37dc-423b-a9e8-8492d91fbb87	QUOTA	QA_LV_MWACC91R
213	6	QA_LV_MWACC91R	2026	1.50	QA adjustment credit	2	2026-11-10 07:00:35.070243+00	76310f8f-1f59-44ff-921e-c54a2669237f	ADJUST	6
214	1	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:1:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
215	15	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:15:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
216	16	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:16:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
217	2	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:2:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
218	19	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:19:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
219	6	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:6:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
220	10	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:10:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
221	21	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:21:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
222	17	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:17:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
223	20	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:20:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
224	9	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:9:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
225	3	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:3:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
226	18	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:18:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
227	12	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:12:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
228	8	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:8:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
229	5	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:5:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
230	14	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:14:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
231	4	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:4:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
232	7	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:7:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
233	23	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:23:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
234	26	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:26:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
235	27	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:27:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
236	28	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:28:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
237	24	QA_LV_MWACC91R	2027	12.00	Opening quota	\N	2026-11-10 07:00:35.143831+00	OPENING:24:QA_LV_MWACC91R:2027	OPENING	QA_LV_MWACC91R
238	1	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:1:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
239	15	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:15:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
240	16	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:16:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
241	2	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:2:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
242	19	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:19:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
243	6	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:6:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
244	10	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:10:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
245	21	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:21:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
246	17	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:17:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
247	20	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:20:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
248	9	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:9:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
249	3	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:3:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
250	18	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:18:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
251	12	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:12:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
252	8	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:8:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
253	5	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:5:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
254	14	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:14:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
255	4	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:4:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
256	7	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:7:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
257	23	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:23:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
258	26	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:26:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
259	27	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:27:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
260	28	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:28:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
261	24	QA_LV_MWACD3QU	2026	10.00	Opening quota	\N	2026-11-10 07:01:15.254434+00	OPENING:24:QA_LV_MWACD3QU:2026	OPENING	QA_LV_MWACD3QU
262	1	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:1:1794294076184:00981ca4-509d-467a-b10f-cff6b1765724	QUOTA	QA_LV_MWACD3QU
263	15	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:15:1794294076184:98988687-85ce-40e5-bb96-6bb2742100b2	QUOTA	QA_LV_MWACD3QU
264	16	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:16:1794294076184:e041ea18-d206-45d4-98e1-2c2fb3b3836b	QUOTA	QA_LV_MWACD3QU
265	2	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:2:1794294076184:20a79825-4499-461d-9510-41c41eff92d1	QUOTA	QA_LV_MWACD3QU
266	19	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:19:1794294076184:dd288f02-f651-43a2-8412-2ed7591c1d57	QUOTA	QA_LV_MWACD3QU
267	6	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:6:1794294076184:3cee8d19-d5c3-441e-9f4c-b85fb3a4d029	QUOTA	QA_LV_MWACD3QU
268	10	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:10:1794294076184:a2b9504d-0830-4490-b6a9-091a7fd38b60	QUOTA	QA_LV_MWACD3QU
269	21	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:21:1794294076184:f79ed73c-85a1-4487-afd6-85c413b3f920	QUOTA	QA_LV_MWACD3QU
270	17	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:17:1794294076184:1d1a8dcb-f0af-4fb1-a078-734cc6dc40bc	QUOTA	QA_LV_MWACD3QU
271	20	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:20:1794294076184:8556a0b4-4741-4b24-ac36-9d8c9412d33b	QUOTA	QA_LV_MWACD3QU
272	9	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:9:1794294076184:1e8b560c-5180-4304-a906-97339279f59f	QUOTA	QA_LV_MWACD3QU
273	3	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:3:1794294076184:ba09c820-8f11-4ce1-9862-a4137b40a777	QUOTA	QA_LV_MWACD3QU
274	18	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:18:1794294076184:1403c2e2-5317-4886-9e57-d4aca619322e	QUOTA	QA_LV_MWACD3QU
275	12	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:12:1794294076184:2eee6dc9-9013-406e-9aa7-43a2d29645e7	QUOTA	QA_LV_MWACD3QU
276	8	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:8:1794294076184:0fc5025c-5f22-45c8-8b6b-8d23d31ec177	QUOTA	QA_LV_MWACD3QU
277	5	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:5:1794294076184:7f002708-7adf-49ba-ad38-92c2ea1e62de	QUOTA	QA_LV_MWACD3QU
278	14	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:14:1794294076184:7d0bafa2-78e5-4ad7-b1ed-c16e85794953	QUOTA	QA_LV_MWACD3QU
279	4	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:4:1794294076184:776afb7f-f8a1-4c14-85ec-40d5bc531f0f	QUOTA	QA_LV_MWACD3QU
280	7	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:7:1794294076184:5f8749e4-fab6-4aa0-a378-8e9040417c11	QUOTA	QA_LV_MWACD3QU
281	23	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:23:1794294076184:7f8e9bbe-a6c2-4a9f-9ada-74f6fcb0d086	QUOTA	QA_LV_MWACD3QU
282	26	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:26:1794294076184:952ab3ae-dddb-4e4c-83da-b4fb7e91fb00	QUOTA	QA_LV_MWACD3QU
283	27	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:27:1794294076184:365b501a-c172-4bcc-b337-5e64fc43c58d	QUOTA	QA_LV_MWACD3QU
284	28	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:28:1794294076184:600a999b-b3cc-48f7-bfe8-2b837981b56f	QUOTA	QA_LV_MWACD3QU
285	24	QA_LV_MWACD3QU	2026	2.00	Quota change mid-period	2	2026-11-10 07:01:16.184198+00	QUOTA:QA_LV_MWACD3QU:2026:24:1794294076184:ef744d8e-8a18-4408-b37f-70e426e11bdf	QUOTA	QA_LV_MWACD3QU
286	6	QA_LV_MWACD3QU	2026	1.50	QA adjustment credit	2	2026-11-10 07:01:16.581586+00	20b9827c-ca21-43aa-85a9-353966269107	ADJUST	6
287	1	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:1:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
288	15	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:15:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
289	16	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:16:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
290	2	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:2:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
291	19	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:19:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
292	6	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:6:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
293	10	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:10:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
294	21	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:21:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
295	17	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:17:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
296	20	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:20:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
297	9	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:9:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
298	3	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:3:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
299	18	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:18:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
300	12	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:12:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
301	8	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:8:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
302	5	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:5:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
303	14	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:14:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
305	7	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:7:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
306	23	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:23:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
307	26	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:26:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
308	27	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:27:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
309	28	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:28:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
310	24	QA_LV_MWACD3QU	2027	12.00	Opening quota	\N	2026-11-10 07:01:16.651642+00	OPENING:24:QA_LV_MWACD3QU:2027	OPENING	QA_LV_MWACD3QU
311	1	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:1:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
312	15	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:15:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
313	16	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:16:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
314	2	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:2:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
315	19	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:19:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
316	6	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:6:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
317	10	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:10:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
318	21	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:21:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
319	17	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:17:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
320	20	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:20:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
321	9	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:9:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
322	3	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:3:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
323	18	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:18:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
324	12	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:12:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
325	8	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:8:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
326	5	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:5:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
327	14	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:14:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
328	4	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:4:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
329	7	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:7:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
330	23	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:23:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
331	26	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:26:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
332	27	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:27:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
333	28	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:28:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
334	24	QA_LV_MWAD0SF2	2026	10.00	Opening quota	\N	2026-11-10 07:19:38.781109+00	OPENING:24:QA_LV_MWAD0SF2:2026	OPENING	QA_LV_MWAD0SF2
335	1	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:1:1794295179221:495aa12a-628d-46b6-b9df-f1533cf5c3cb	QUOTA	QA_LV_MWAD0SF2
336	15	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:15:1794295179221:9aadeb49-92f8-49ba-9fd0-86d6d0d98704	QUOTA	QA_LV_MWAD0SF2
337	16	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:16:1794295179221:99c3c1e9-4ccb-4e7b-93fe-54158f717902	QUOTA	QA_LV_MWAD0SF2
338	2	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:2:1794295179221:fb4e4c1e-0af0-4e2c-8476-973ae4a9c961	QUOTA	QA_LV_MWAD0SF2
339	19	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:19:1794295179221:c70688f5-5738-4040-9fc3-c67a05007060	QUOTA	QA_LV_MWAD0SF2
340	6	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:6:1794295179221:899995be-c1f1-41ef-af5a-8a07ebab7984	QUOTA	QA_LV_MWAD0SF2
341	10	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:10:1794295179221:33321f90-7ca8-4c3f-b246-9e51db94a771	QUOTA	QA_LV_MWAD0SF2
342	21	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:21:1794295179221:d702ac1d-a404-4f60-90b5-27778d594eb9	QUOTA	QA_LV_MWAD0SF2
343	17	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:17:1794295179221:c951bf29-7429-47b3-bcd9-9f3ba24a8104	QUOTA	QA_LV_MWAD0SF2
344	20	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:20:1794295179221:771682b0-6584-4d8e-a55a-a607085e028b	QUOTA	QA_LV_MWAD0SF2
345	9	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:9:1794295179221:7b16240e-c272-4e50-984c-83e009435652	QUOTA	QA_LV_MWAD0SF2
346	3	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:3:1794295179221:06063e5e-cf1c-4414-977f-0f24f7f62a08	QUOTA	QA_LV_MWAD0SF2
347	18	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:18:1794295179221:85ce46a0-fb5a-41c1-ac3f-809a34dd84dc	QUOTA	QA_LV_MWAD0SF2
348	12	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:12:1794295179221:fa3faeba-d772-46d3-862d-1ca4a3ae7e70	QUOTA	QA_LV_MWAD0SF2
349	8	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:8:1794295179221:8afe422b-8b19-4e1f-9003-9bb4d44a8580	QUOTA	QA_LV_MWAD0SF2
399	21	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:21:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
350	5	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:5:1794295179221:540350e0-29e7-4342-be82-f593df1c6f72	QUOTA	QA_LV_MWAD0SF2
351	14	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:14:1794295179221:41ea9717-878f-44d2-bd6d-f5fca120a18f	QUOTA	QA_LV_MWAD0SF2
352	4	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:4:1794295179221:2faa8e99-5052-481f-bf1e-201b93f70fa8	QUOTA	QA_LV_MWAD0SF2
353	7	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:7:1794295179221:ea30b10c-5e7f-417a-99e3-a84b43881926	QUOTA	QA_LV_MWAD0SF2
354	23	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:23:1794295179221:f7190f1f-31cc-4f07-a854-a0be14381a9b	QUOTA	QA_LV_MWAD0SF2
355	26	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:26:1794295179221:d75134ad-19db-4f84-b2d2-617110bbe349	QUOTA	QA_LV_MWAD0SF2
356	27	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:27:1794295179221:1770c0e6-4fef-48e7-9b17-77ff4649d377	QUOTA	QA_LV_MWAD0SF2
357	28	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:28:1794295179221:015f68af-bd94-4781-99f5-d94d349e029f	QUOTA	QA_LV_MWAD0SF2
358	24	QA_LV_MWAD0SF2	2026	2.00	Quota change mid-period	2	2026-11-10 07:19:39.221401+00	QUOTA:QA_LV_MWAD0SF2:2026:24:1794295179221:4227bd1b-3fb7-41a1-8208-5e09c7b698c8	QUOTA	QA_LV_MWAD0SF2
359	6	QA_LV_MWAD0SF2	2026	1.50	QA adjustment credit	2	2026-11-10 07:19:39.734261+00	0f1d0739-195e-476a-bbbf-730fa812f6f4	ADJUST	6
360	1	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:1:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
361	15	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:15:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
362	16	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:16:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
363	2	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:2:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
364	19	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:19:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
365	6	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:6:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
366	10	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:10:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
367	21	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:21:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
368	17	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:17:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
369	20	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:20:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
370	9	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:9:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
371	3	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:3:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
372	18	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:18:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
373	12	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:12:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
374	8	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:8:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
375	5	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:5:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
376	14	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:14:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
377	4	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:4:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
378	7	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:7:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
379	23	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:23:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
380	26	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:26:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
381	27	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:27:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
382	28	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:28:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
383	24	QA_LV_MWAD0SF2	2027	12.00	Opening quota	\N	2026-11-10 07:19:39.822066+00	OPENING:24:QA_LV_MWAD0SF2:2027	OPENING	QA_LV_MWAD0SF2
384	6	CASUAL	2026	5.00	QA top-up for apply flow	2	2026-11-10 07:20:09.068362+00	e7e2010a-61ca-4b35-905e-2165fd22ef09	ADJUST	6
385	6	QA_ATT_95209	2026	5.00	Opening quota	\N	2026-11-10 07:20:09.275402+00	OPENING:6:QA_ATT_95209:2026	OPENING	QA_ATT_95209
386	6	CASUAL	2026	-1.00	Leave approved deduction	4	2026-11-10 07:20:19.195238+00	APPROVE:36	APPROVE	36
387	6	CASUAL	2026	1.00	Leave cancel restore	4	2026-11-10 07:20:19.330345+00	CANCEL:36	CANCEL	36
388	6	QA_NT_MWAD20SK	2026	5.00	Opening quota	\N	2026-11-10 07:20:36.002209+00	OPENING:6:QA_NT_MWAD20SK:2026	OPENING	QA_NT_MWAD20SK
389	6	CASUAL	2026	3.00	QA top-up apply flow	2	2026-11-10 07:23:15.284697+00	2c5e8170-b24e-4d0a-9bf7-5d29ffcd476b	ADJUST	6
390	6	CASUAL	2026	-1.00	Leave approved deduction	4	2026-11-10 07:23:15.697254+00	APPROVE:38	APPROVE	38
391	6	CASUAL	2026	1.00	Leave cancel restore	4	2026-11-10 07:23:15.859921+00	CANCEL:38	CANCEL	38
392	1	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:1:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
393	15	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:15:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
394	16	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:16:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
395	2	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:2:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
396	19	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:19:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
397	6	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:6:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
398	10	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:10:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
400	17	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:17:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
401	20	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:20:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
402	9	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:9:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
403	3	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:3:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
404	18	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:18:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
405	12	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:12:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
406	8	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:8:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
407	5	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:5:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
408	14	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:14:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
409	4	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:4:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
410	7	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:7:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
411	23	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:23:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
412	26	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:26:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
413	27	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:27:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
414	28	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:28:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
415	24	QA_LV_MWAD5GLJ	2026	10.00	Opening quota	\N	2026-11-10 07:23:16.705321+00	OPENING:24:QA_LV_MWAD5GLJ:2026	OPENING	QA_LV_MWAD5GLJ
416	1	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:1:1794295397086:8618e638-8724-4582-bc1b-5db51f9a70b1	QUOTA	QA_LV_MWAD5GLJ
417	15	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:15:1794295397086:181bb70c-83ab-4bf5-b779-6c829769eb43	QUOTA	QA_LV_MWAD5GLJ
418	16	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:16:1794295397086:c2fc722c-7440-44ac-bf00-abfe6cda1f9f	QUOTA	QA_LV_MWAD5GLJ
419	2	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:2:1794295397086:5862bf29-8819-4571-9111-9983f515a188	QUOTA	QA_LV_MWAD5GLJ
420	19	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:19:1794295397086:59cdf377-bd2e-4054-a9e0-4b5a4bbae683	QUOTA	QA_LV_MWAD5GLJ
421	6	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:6:1794295397086:c211b957-5c56-4849-9c8e-4dd5cb59c3ea	QUOTA	QA_LV_MWAD5GLJ
422	10	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:10:1794295397086:114675d3-a2ae-4aaf-a87a-66ba724f540e	QUOTA	QA_LV_MWAD5GLJ
423	21	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:21:1794295397086:d4852bab-8397-4bc6-823e-2679304cf832	QUOTA	QA_LV_MWAD5GLJ
424	17	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:17:1794295397086:43643655-a3b6-4b1d-b137-cb054baa6833	QUOTA	QA_LV_MWAD5GLJ
425	20	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:20:1794295397086:c14a9574-3c87-4a7f-b82c-68a8cbc82c68	QUOTA	QA_LV_MWAD5GLJ
426	9	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:9:1794295397086:a7286c70-7b70-4359-8bdb-458d09ab231b	QUOTA	QA_LV_MWAD5GLJ
427	3	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:3:1794295397086:13589d94-ffe8-430e-bba7-1bddbc7fa8d0	QUOTA	QA_LV_MWAD5GLJ
428	18	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:18:1794295397086:1d7283fa-d2a8-45be-822d-79be87b2e511	QUOTA	QA_LV_MWAD5GLJ
429	12	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:12:1794295397086:55bf5f4c-188c-4824-abc8-ea44b7722084	QUOTA	QA_LV_MWAD5GLJ
430	8	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:8:1794295397086:29feeb27-eb9f-4a8a-aa2b-f3521ce5655d	QUOTA	QA_LV_MWAD5GLJ
431	5	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:5:1794295397086:54f09482-1ce1-409b-9fc7-811ecb449529	QUOTA	QA_LV_MWAD5GLJ
432	14	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:14:1794295397086:44a6b83d-ccd0-4e81-af63-9d883bebb0d1	QUOTA	QA_LV_MWAD5GLJ
433	4	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:4:1794295397086:1e421838-6b68-467a-af20-7ac51a70ceb5	QUOTA	QA_LV_MWAD5GLJ
434	7	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:7:1794295397086:f9ce5aca-3011-42a4-977d-b5c46ac7bf42	QUOTA	QA_LV_MWAD5GLJ
435	23	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:23:1794295397086:a224a8cc-2882-4678-be36-76cf696088f2	QUOTA	QA_LV_MWAD5GLJ
436	26	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:26:1794295397086:00469170-6873-4d60-8e30-8b24d3261054	QUOTA	QA_LV_MWAD5GLJ
437	27	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:27:1794295397086:a2e6f754-e435-4fd0-9959-01604134949b	QUOTA	QA_LV_MWAD5GLJ
438	28	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:28:1794295397086:055e222b-dd57-4725-bb2f-153e50373b0a	QUOTA	QA_LV_MWAD5GLJ
439	24	QA_LV_MWAD5GLJ	2026	2.00	Quota change mid-period	2	2026-11-10 07:23:17.086582+00	QUOTA:QA_LV_MWAD5GLJ:2026:24:1794295397086:f439d2d9-52fa-46c1-80d5-5b9535220c1d	QUOTA	QA_LV_MWAD5GLJ
440	6	QA_LV_MWAD5GLJ	2026	1.50	QA adjustment credit	2	2026-11-10 07:23:17.68166+00	83c55f08-2d5a-45f2-bafd-f819180bc822	ADJUST	6
441	1	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:1:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
442	15	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:15:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
443	16	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:16:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
444	2	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:2:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
445	19	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:19:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
446	6	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:6:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
447	10	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:10:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
448	21	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:21:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
449	17	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:17:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
450	20	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:20:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
451	9	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:9:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
452	3	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:3:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
453	18	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:18:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
454	12	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:12:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
455	8	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:8:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
456	5	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:5:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
457	14	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:14:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
458	4	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:4:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
459	7	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:7:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
460	23	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:23:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
461	26	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:26:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
462	27	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:27:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
463	28	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:28:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
464	24	QA_LV_MWAD5GLJ	2027	12.00	Opening quota	\N	2026-11-10 07:23:17.740428+00	OPENING:24:QA_LV_MWAD5GLJ:2027	OPENING	QA_LV_MWAD5GLJ
465	6	CASUAL	2026	3.00	QA top-up apply flow	2	2026-11-10 07:24:00.737977+00	edc9989b-9207-4e8d-ad67-ebed70ac9caf	ADJUST	6
466	6	CASUAL	2026	-1.00	Leave approved deduction	4	2026-11-10 07:24:01.520681+00	APPROVE:41	APPROVE	41
467	6	CASUAL	2026	1.00	Leave cancel restore	4	2026-11-10 07:24:01.811923+00	CANCEL:41	CANCEL	41
468	6	CASUAL	2026	3.00	QA top-up apply flow	2	2026-11-10 07:24:48.610768+00	ec0b7fa7-c4ec-4c46-951f-8b87913731fe	ADJUST	6
469	6	CASUAL	2026	-1.00	Leave approved deduction	4	2026-11-10 07:24:48.878712+00	APPROVE:44	APPROVE	44
470	6	CASUAL	2026	1.00	Leave cancel restore	4	2026-11-10 07:24:48.995505+00	CANCEL:44	CANCEL	44
471	6	CASUAL	2026	3.00	QA top-up apply flow	2	2026-11-10 08:22:12.108675+00	d6efe33e-378e-469b-a33c-4249011f8a74	ADJUST	6
472	6	CASUAL	2026	-1.00	Leave approved deduction	4	2026-11-10 08:22:12.775169+00	APPROVE:47	APPROVE	47
473	6	CASUAL	2026	1.00	Leave cancel restore	4	2026-11-10 08:22:12.945888+00	CANCEL:47	CANCEL	47
474	4	QA_LV_MWAD0SF2	2026	-1.00	Leave approved deduction	1	2026-11-10 08:36:58.526093+00	APPROVE:50	APPROVE	50
475	6	CASUAL	2026	3.00	QA top-up apply flow	2	2026-11-10 08:40:02.069504+00	743d5b9a-a591-40e5-987a-76b0d95fdb3b	ADJUST	6
476	6	CASUAL	2026	-1.00	Leave approved deduction	4	2026-11-10 08:40:02.382552+00	APPROVE:52	APPROVE	52
477	6	CASUAL	2026	1.00	Leave cancel restore	4	2026-11-10 08:40:02.55057+00	CANCEL:52	CANCEL	52
494	31	CASUAL	2026	1.00	Payroll scenario seed — opening balance	1	2026-11-10 11:03:36.664682+00	SEED:PAYROLL:BAL:31:CASUAL	ADJUST	seed
505	31	CASUAL	2026	-1.00	Payroll absence cover 2026-09	\N	2026-11-10 11:03:42.586776+00	COVER:FINALIZE:2026-09:r1:31:CASUAL	COVER	2026-09
506	31	CASUAL	2026	1.00	Payroll 2026-09 reopened — absence cover returned	\N	2026-11-10 11:03:44.479571+00	REVERSE:COVER:FINALIZE:2026-09:r1:31:CASUAL	COVER_REVERSAL	2026-09
507	31	CASUAL	2026	-1.00	Payroll absence cover 2026-09	\N	2026-11-10 11:03:46.516163+00	COVER:FINALIZE:2026-09:r3:31:CASUAL	COVER	2026-09
508	12	ANNUAL	2026	-2.00	Payroll absence cover 2026-10	\N	2026-11-10 11:03:49.593262+00	COVER:FINALIZE:2026-10:r5:12:ANNUAL	COVER	2026-10
509	7	CASUAL	2026	-1.50	Payroll absence cover 2026-10	\N	2026-11-10 11:03:49.600875+00	COVER:FINALIZE:2026-10:r5:7:CASUAL	COVER	2026-10
495	31	SICK	2026	10.00	Payroll scenario seed — opening balance	1	2026-11-10 11:03:36.664682+00	SEED:PAYROLL:BAL:31:SICK	ADJUST	seed
496	29	CASUAL	2026	10.00	Payroll scenario seed — opening balance	1	2026-11-10 11:03:36.664682+00	SEED:PAYROLL:BAL:29:CASUAL	ADJUST	seed
497	29	ANNUAL	2026	14.00	Payroll scenario seed — opening balance	1	2026-11-10 11:03:36.664682+00	SEED:PAYROLL:BAL:29:ANNUAL	ADJUST	seed
498	29	SICK	2026	10.00	Payroll scenario seed — opening balance	1	2026-11-10 11:03:36.664682+00	SEED:PAYROLL:BAL:29:SICK	ADJUST	seed
499	30	CASUAL	2026	5.00	Payroll scenario seed — opening balance	1	2026-11-10 11:03:36.664682+00	SEED:PAYROLL:BAL:30:CASUAL	ADJUST	seed
500	30	SICK	2026	10.00	Payroll scenario seed — opening balance	1	2026-11-10 11:03:36.664682+00	SEED:PAYROLL:BAL:30:SICK	ADJUST	seed
501	7	CASUAL	2026	5.50	Payroll scenario seed — opening balance	1	2026-11-10 11:03:36.664682+00	SEED:PAYROLL:BAL:7:CASUAL	ADJUST	seed
502	12	ANNUAL	2026	2.00	Payroll scenario seed — opening balance	1	2026-11-10 11:03:36.664682+00	SEED:PAYROLL:BAL:12:ANNUAL	ADJUST	seed
503	10	ANNUAL	2026	-6.00	Payroll scenario seed — opening balance	1	2026-11-10 11:03:36.664682+00	SEED:PAYROLL:BAL:10:ANNUAL	ADJUST	seed
504	7	CASUAL	2026	-0.50	Leave approved	2	2026-11-10 11:03:36.664682+00	SEED:PAYROLL:APPROVE:62	APPROVE	62
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
6	ANNUAL	Annual Leave	14.00	2026-01-01	2026-12-31	t	t	NEVER	0	7	14	0.00	[{"role": "MANAGER", "step": 1}, {"role": "HR", "step": 2}]	t	\N	f	NONE	\N	\N
7	ANNUAL_2029	Annual Leave (2029)	14.00	2029-01-01	2029-12-31	t	t	NEVER	0	7	14	0.00	[{"role": "MANAGER", "step": 1}, {"role": "HR", "step": 2}]	t	\N	f	NONE	\N	\N
8	HUDAI	Hudai	10.00	2026-10-27	2026-12-31	t	t	NEVER	0	0	2	\N	[{"role": "MANAGER", "step": 1}]	t	\N	f	NONE	\N	\N
9	HUDAI_2027	Hudai (2027)	10.00	2027-01-01	2027-12-31	f	t	NEVER	0	0	2	\N	[{"role": "MANAGER", "step": 1}]	t	\N	f	NONE	\N	\N
10	TEST_64000	Test Leave TEST_64000	5.00	2026-01-01	2026-12-31	t	t	NEVER	0	0	0	\N	\N	t	\N	f	NONE	\N	\N
5	ANNUAL_2027	Annual Leave	14.00	2027-01-01	2027-12-31	t	t	NEVER	0	7	14	0.00	[{"role": "MANAGER", "step": 1}, {"role": "HR", "step": 2}]	f	\N	f	NONE	\N	\N
11	CASUAL_2027	Casual Leave (2027)	10.00	2027-01-01	2027-12-31	t	t	NEVER	0	0	3	0.00	[{"role": "MANAGER", "step": 1}]	t	\N	f	NONE	\N	\N
3	SICK	Sick Leave	10.00	2026-01-01	2026-12-31	t	t	AFTER_DAYS	0	0	5	-2.00	[{"role": "MANAGER", "step": 1}]	t	\N	f	NONE	\N	\N
4	UNPAID	Unpaid Leave	0.00	2026-01-01	2026-12-31	f	t	NEVER	0	3	30	0.00	[{"role": "MANAGER", "step": 1}, {"role": "HR", "step": 2}]	t	\N	f	NONE	\N	\N
2	ANNUAL_2025	Annual Leave	14.00	2026-01-01	2026-12-31	t	t	NEVER	0	7	14	0.00	[{"role": "MANAGER", "step": 1}, {"role": "HR", "step": 2}]	f	\N	f	NONE	\N	\N
12	QA_LV_MWACAH2G	QA Leave Setup	12.00	2026-01-01	2026-12-31	t	t	NEVER	0	0	0	\N	[{"role": "MANAGER", "step": 1}]	t	{"teams": [], "locations": [], "blockHourly": true, "employeeIds": [], "employmentTypes": []}	f	CAP	3.00	\N
13	QA_LV_MWACAH2G	QA Leave Setup	12.00	2027-01-01	2027-12-31	t	t	NEVER	0	0	0	\N	[{"role": "MANAGER", "step": 1}]	t	{"teams": [], "locations": [], "blockHourly": true, "employeeIds": [], "employmentTypes": []}	f	CAP	3.00	\N
14	QA_LV_MWACC91R	QA Leave Setup	12.00	2026-01-01	2026-12-31	t	t	NEVER	0	0	0	\N	[{"role": "MANAGER", "step": 1}]	t	{"teams": [], "locations": [], "blockHourly": true, "employeeIds": [], "employmentTypes": []}	f	CAP	3.00	\N
16	QA_LV_MWACD3QU	QA Leave Setup	12.00	2026-01-01	2026-12-31	t	t	NEVER	0	0	0	\N	[{"role": "MANAGER", "step": 1}]	t	{"teams": [], "locations": [], "blockHourly": true, "employeeIds": [], "employmentTypes": []}	f	CAP	3.00	\N
17	QA_LV_MWACD3QU	QA Leave Setup	12.00	2027-01-01	2027-12-31	t	t	NEVER	0	0	0	\N	[{"role": "MANAGER", "step": 1}]	t	{"teams": [], "locations": [], "blockHourly": true, "employeeIds": [], "employmentTypes": []}	f	CAP	3.00	\N
18	QA_LV_MWAD0SF2	QA Leave Setup	12.00	2026-01-01	2026-12-31	t	t	ALWAYS	0	0	0	\N	[{"role": "MANAGER", "step": 1}, {"role": "HR", "step": 2}]	t	{"teams": [], "locations": [], "blockHourly": true, "employeeIds": [4, 8], "employmentTypes": []}	f	CAP	3.00	\N
15	QA_LV_MWACC91R	QA Leave Setup	12.00	2027-01-01	2027-12-31	t	t	NEVER	0	0	0	\N	[{"role": "MANAGER", "step": 1}]	t	{"teams": [], "locations": [], "blockHourly": true, "employeeIds": [], "employmentTypes": []}	f	CAP	3.00	\N
21	QA_NT_MWAD20SK	QA Notice	5.00	2026-01-01	2026-12-31	t	t	NEVER	0	10	1	\N	[{"role": "MANAGER", "step": 1}]	t	{"teams": [], "locations": [], "blockHourly": true, "employeeIds": [6], "employmentTypes": []}	f	NONE	\N	\N
20	QA_ATT_95209	QA Always Attach	5.00	2026-01-01	2026-12-31	t	t	ALWAYS	0	0	0	\N	[{"role": "MANAGER", "step": 1}]	t	{"teams": [1], "locations": [1], "blockHourly": true, "employeeIds": [6], "employmentTypes": ["FULL_TIME"]}	f	NONE	\N	\N
22	QA_LV_MWAD5GLJ	QA Leave Setup	12.00	2026-01-01	2026-12-31	t	t	NEVER	0	0	0	\N	[{"role": "MANAGER", "step": 1}]	t	{"teams": [], "locations": [], "blockHourly": true, "employeeIds": [], "employmentTypes": []}	f	CAP	3.00	\N
23	QA_LV_MWAD5GLJ	QA Leave Setup	12.00	2027-01-01	2027-12-31	t	t	NEVER	0	0	0	\N	[{"role": "MANAGER", "step": 1}]	t	{"teams": [], "locations": [], "blockHourly": true, "employeeIds": [], "employmentTypes": []}	f	CAP	3.00	\N
19	QA_LV_MWAD0SF2	QA Leave Setup	12.00	2027-01-01	2027-12-31	t	t	NEVER	0	0	0	\N	[{"role": "MANAGER", "step": 1}]	t	{"teams": [], "locations": [], "blockHourly": true, "employeeIds": [], "employmentTypes": []}	f	CAP	3.00	\N
1	CASUAL	Casual Leave	10.00	2026-01-01	2026-12-31	t	t	NEVER	0	0	3	0.00	[{"role": "MANAGER", "step": 1}]	t	{"teams": [], "locations": [], "blockHourly": true, "employeeIds": [], "employmentTypes": []}	f	NONE	\N	\N
\.


ALTER TABLE public.leave_category ENABLE TRIGGER ALL;

--
-- Data for Name: leave_operation; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.leave_operation DISABLE TRIGGER ALL;

COPY public.leave_operation (id, operation_key, event_id, employee_id, period_label, reverse_flag, status, result_json, created_at, completed_at) FROM stdin;
9	FINALIZE:2026-09:r1	412b5f88-2e38-4418-b52b-fa4764a63b08	0	2026-09	f	SUCCEEDED	{"action": "FINALIZE", "period": "2026-09", "status": "SUCCEEDED", "success": true, "revision": 1, "employees": 1, "operationId": "db7c3989-d052-4ad7-b0ae-39a6e4083720", "operationKey": "FINALIZE:2026-09:r1"}	2026-11-10 11:03:42.568052+00	2026-11-10 11:03:42.616306+00
10	REOPEN:2026-09:r1:a1	643b6ca5-8e70-4589-8d29-624812c855da	0	2026-09	t	SUCCEEDED	{"action": "REOPEN", "period": "2026-09", "status": "SUCCEEDED", "success": true, "revision": 1, "operationId": "a6354052-0391-414a-819c-be43184044f0", "operationKey": "REOPEN:2026-09:r1:a1", "reversedRows": 1}	2026-11-10 11:03:44.470352+00	2026-11-10 11:03:44.484424+00
11	FINALIZE:2026-09:r3	40d6e149-c5ad-4627-b24c-c54383befe3a	0	2026-09	f	SUCCEEDED	{"action": "FINALIZE", "period": "2026-09", "status": "SUCCEEDED", "success": true, "revision": 3, "employees": 1, "operationId": "91302c22-3dd6-4678-bcfd-1586e272ad55", "operationKey": "FINALIZE:2026-09:r3"}	2026-11-10 11:03:46.505461+00	2026-11-10 11:03:46.520838+00
12	FINALIZE:2026-10:r5	9088c107-d9a2-4dfe-920e-4737e0807991	0	2026-10	f	SUCCEEDED	{"action": "FINALIZE", "period": "2026-10", "status": "SUCCEEDED", "success": true, "revision": 5, "employees": 2, "operationId": "a37b1855-7827-4b23-86ec-dce75253544c", "operationKey": "FINALIZE:2026-10:r5"}	2026-11-10 11:03:49.573121+00	2026-11-10 11:03:49.606726+00
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
11	CASUAL_2027	Casual Leave (2027)	t	t	f	t	t	t	11
12	QA_LV_MWACAH2G	QA Leave Setup	t	t	f	t	t	t	12
13	QA_LV_MWACC91R	QA Leave Setup	t	t	f	t	t	t	13
14	QA_LV_MWACD3QU	QA Leave Setup	t	t	f	t	t	t	14
15	QA_LV_MWAD0SF2	QA Leave Setup	t	t	f	t	t	t	15
17	QA_NT_MWAD20SK	QA Notice	t	t	f	t	t	t	17
18	QA_LV_MWAD5GLJ	QA Leave Setup	t	t	f	t	t	t	18
16	QA_ATT_95209	QA Always Attach	t	t	f	t	t	t	16
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

SELECT pg_catalog.setval('public.audit_log_id_seq', 393, true);


--
-- Name: leave_application_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_application_id_seq', 62, true);


--
-- Name: leave_approval_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_approval_id_seq', 111, true);


--
-- Name: leave_balance_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_balance_id_seq', 336, true);


--
-- Name: leave_balance_ledger_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_balance_ledger_id_seq', 509, true);


--
-- Name: leave_blackout_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_blackout_id_seq', 1, true);


--
-- Name: leave_category_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_category_id_seq', 23, true);


--
-- Name: leave_operation_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_operation_id_seq', 12, true);


--
-- Name: leave_policy_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_policy_id_seq', 1, true);


--
-- Name: leave_type_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_type_id_seq', 18, true);


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

\unrestrict bMtNtPsjlARyXE0A8Q6fymEXVCmuoxiFG7f4PSkbBdTBd8BAQQG9Chb6YpL0hvW

