--
-- PostgreSQL database dump
--

\restrict lM1VTOUZFhYcV8mkF5dnT8FhAtYPgLYoQxRtV4vkA5AkWd6aQioCx0PMn14R0mo

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
-- Data for Name: app_permission; Type: TABLE DATA; Schema: public; Owner: -
--

SET SESSION AUTHORIZATION DEFAULT;

ALTER TABLE public.app_permission DISABLE TRIGGER ALL;

COPY public.app_permission (id, feature, action, description, scoped, label, sort_order, matrix_visible) FROM stdin;
1	checklists	view	View onboarding/offboarding	t	Onboarding and offboarding	120	t
2	notifications	view	View notifications	t	notifications	900	f
3	emails	create	Send monthly report emails	t	Monthly report emails	170	t
4	roles	assign	Assign roles to people	f	roles	900	f
5	org	view	View org	f	org	900	f
6	org	manage	Manage org	f	org	900	f
7	payroll	view	View payroll runs	f	Payroll runs	160	t
8	roles	view	View roles	f	Roles and permissions	180	t
9	holidays	view	View holiday calendars	f	Holiday calendars	110	t
10	profile_personal	edit	Edit personal profile	t	Employee profile: personal	20	t
11	settings	manage	Manage settings	f	settings	900	f
12	audit	view	View audit log	f	Audit log	200	t
13	roles	manage	Manage role permission matrix	f	roles	900	f
14	worklogs	view	View work logs	t	Work logs	140	t
15	reports	export	Export reports	t	Reports and HR dashboard	190	t
16	leave	approve	Approve leave	t	leave	900	f
17	leave_setup	view	View leave categories	f	Leave categories and quotas	60	t
18	attendance_settings	view	View attendance settings	f	Attendance settings	100	t
19	salary	edit	Edit salary	t	Salary details and payslips	150	t
20	attendance	view	View attendance records	t	Attendance records	90	t
21	profile_employment	view	View employment profile	t	Employee profile: employment	30	t
22	tasks	edit	Edit tasks	t	Tasks (until the project module)	130	t
23	leave_requests	approve	Approve leave requests	t	Leave requests	70	t
24	documents	view	View employee documents	t	Employee documents	40	t
25	attendance	correct	Correct attendance	t	attendance	900	f
26	tasks	create	Create tasks	t	Tasks (until the project module)	130	t
27	leave_requests	create	Create leave requests	t	Leave requests	70	t
28	leave_balances	edit	Edit leave balances	t	Leave balances	80	t
29	hierarchy	view	View org hierarchy	f	Org hierarchy	50	t
30	employees	create	Create employees	t	employees	900	f
31	employees	edit	Edit employees	t	employees	900	f
32	emails	view	View monthly report emails	t	Monthly report emails	170	t
33	checklists	edit	Edit checklists	t	Onboarding and offboarding	120	t
34	checklists	create	Create checklists	t	Onboarding and offboarding	120	t
35	attendance	export	Export attendance	t	Attendance records	90	t
36	payroll	create	Create payroll runs	f	Payroll runs	160	t
37	payroll	approve	Approve payroll runs	f	Payroll runs	160	t
38	attendance	checkin	Self check-in/out	t	attendance	900	f
39	leave_setup	delete	Delete leave categories	f	Leave categories and quotas	60	t
40	profile_personal	view	View personal profile	t	Employee profile: personal	20	t
41	holidays	edit	Edit holidays	f	Holiday calendars	110	t
42	holidays	create	Create holidays	f	Holiday calendars	110	t
43	leave	request	Request leave	t	leave	900	f
44	roles	create	Create roles	f	Roles and permissions	180	t
45	roles	edit	Edit roles / assign people	f	Roles and permissions	180	t
46	worklogs	create	Create work logs	t	Work logs	140	t
47	documents	delete	Delete employee documents	t	Employee documents	40	t
48	worklogs	edit	Edit work logs	t	Work logs	140	t
49	leave_setup	create	Create leave categories	f	Leave categories and quotas	60	t
50	leave_setup	edit	Edit leave categories	f	Leave categories and quotas	60	t
51	leave	view	View leave	t	leave	900	f
52	leave	manage	Manage leave balances	t	leave	900	f
53	attendance	edit	Edit attendance / corrections	t	Attendance records	90	t
54	payroll	export	Export payroll	f	Payroll runs	160	t
55	salary	view	View salary and payslips	t	Salary details and payslips	150	t
56	attendance_settings	edit	Edit attendance settings	f	Attendance settings	100	t
57	payroll	run	Run payroll	f	payroll	900	f
58	leave_balances	view	View leave balances	t	Leave balances	80	t
59	leave_requests	view	View leave requests	t	Leave requests	70	t
60	tasks	view	View tasks	t	Tasks (until the project module)	130	t
61	documents	create	Upload employee documents	t	Employee documents	40	t
62	profile_employment	edit	Edit employment profile	t	Employee profile: employment	30	t
63	profile_employment	create	Create employment records	t	Employee profile: employment	30	t
64	roles	delete	Delete roles	f	Roles and permissions	180	t
65	reports	view	View reports	t	Reports and HR dashboard	190	t
66	directory	view	View employee directory	t	Employee directory	10	t
67	holidays	delete	Delete holidays	f	Holiday calendars	110	t
68	employees	view	View employees	t	employees	900	f
69	hierarchy	edit	Edit org hierarchy	f	Org hierarchy	50	t
\.


ALTER TABLE public.app_permission ENABLE TRIGGER ALL;

--
-- Data for Name: app_role; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.app_role DISABLE TRIGGER ALL;

COPY public.app_role (id, code, name, description, system_role, active, protect_from_offboard, immutable_permissions, grants_wildcard, min_holders, assignable_by) FROM stdin;
1	SUPERADMIN	Super Admin	Full platform access	t	t	t	t	t	1	SUPERADMIN
2	ADMIN	Admin	Company admin	t	t	f	f	f	0	SUPERADMIN
3	HR	HR	Human resources	t	t	f	f	f	0	SUPERADMIN,ADMIN
4	MANAGER	Manager	Team manager	t	t	f	f	f	0	SUPERADMIN,ADMIN,HR
5	EMPLOYEE	Employee	Standard employee	t	t	f	f	f	0	SUPERADMIN,ADMIN,HR
\.


ALTER TABLE public.app_role ENABLE TRIGGER ALL;

--
-- Data for Name: app_role_permission; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.app_role_permission DISABLE TRIGGER ALL;

COPY public.app_role_permission (id, role_id, permission_id, data_scope) FROM stdin;
1	1	1	ALL
2	1	2	ALL
3	1	3	ALL
4	1	4	ALL
5	1	5	ALL
6	1	6	ALL
7	1	7	ALL
8	1	8	ALL
9	1	9	ALL
10	1	10	ALL
11	1	11	ALL
12	1	12	ALL
13	1	13	ALL
14	1	14	ALL
15	1	15	ALL
16	1	16	ALL
17	1	17	ALL
18	1	18	ALL
19	1	19	ALL
20	1	20	ALL
21	1	21	ALL
22	1	22	ALL
23	1	23	ALL
24	1	24	ALL
25	1	25	ALL
26	1	26	ALL
27	1	27	ALL
28	1	28	ALL
29	1	29	ALL
30	1	30	ALL
31	1	31	ALL
32	1	32	ALL
33	1	33	ALL
34	1	34	ALL
35	1	35	ALL
36	1	36	ALL
37	1	37	ALL
38	1	38	ALL
39	1	39	ALL
40	1	40	ALL
41	1	41	ALL
42	1	42	ALL
43	1	43	ALL
44	1	44	ALL
45	1	45	ALL
46	1	46	ALL
47	1	47	ALL
48	1	48	ALL
49	1	49	ALL
50	1	50	ALL
51	1	51	ALL
52	1	52	ALL
53	1	53	ALL
54	1	54	ALL
55	1	55	ALL
56	1	56	ALL
57	1	57	ALL
58	1	58	ALL
59	1	59	ALL
60	1	60	ALL
61	1	61	ALL
62	1	62	ALL
63	1	63	ALL
64	1	64	ALL
65	1	65	ALL
66	1	66	ALL
67	1	67	ALL
68	1	68	ALL
69	1	69	ALL
70	2	1	ALL
71	2	2	ALL
72	2	3	ALL
73	2	4	ALL
74	2	5	ALL
75	2	6	ALL
76	2	7	ALL
77	2	8	ALL
78	2	9	ALL
79	2	10	ALL
80	2	11	ALL
81	2	12	ALL
82	2	14	ALL
83	2	15	ALL
84	2	16	ALL
85	2	17	ALL
86	2	18	ALL
87	2	19	ALL
88	2	20	ALL
89	2	21	ALL
90	2	22	ALL
91	2	23	ALL
92	2	24	ALL
93	2	25	ALL
94	2	26	ALL
95	2	27	ALL
96	2	28	ALL
97	2	29	ALL
98	2	30	ALL
99	2	31	ALL
100	2	32	ALL
101	2	33	ALL
102	2	34	ALL
103	2	35	ALL
104	2	36	ALL
105	2	37	ALL
106	2	38	ALL
107	2	39	ALL
108	2	40	ALL
109	2	41	ALL
110	2	42	ALL
111	2	43	ALL
112	2	45	ALL
113	2	46	ALL
114	2	47	ALL
115	2	48	ALL
116	2	49	ALL
117	2	50	ALL
118	2	51	ALL
119	2	52	ALL
120	2	53	ALL
121	2	54	ALL
122	2	55	ALL
123	2	56	ALL
124	2	57	ALL
125	2	58	ALL
126	2	59	ALL
127	2	60	ALL
128	2	61	ALL
129	2	62	ALL
130	2	63	ALL
131	2	65	ALL
132	2	66	ALL
133	2	67	ALL
134	2	68	ALL
135	2	69	ALL
136	3	1	ALL
137	3	2	ALL
138	3	3	ALL
139	3	5	ALL
140	3	8	ALL
141	3	9	ALL
142	3	10	ALL
143	3	14	ALL
144	3	15	ALL
145	3	16	ALL
146	3	17	ALL
147	3	18	ALL
148	3	20	ALL
149	3	21	ALL
150	3	23	ALL
151	3	24	ALL
152	3	25	ALL
154	3	28	ALL
155	3	29	ALL
156	3	30	ALL
157	3	31	ALL
158	3	32	ALL
159	3	33	ALL
160	3	34	ALL
161	3	35	ALL
162	3	38	ALL
163	3	39	ALL
164	3	40	ALL
165	3	41	ALL
166	3	42	ALL
167	3	43	ALL
168	3	45	ALL
169	3	47	ALL
170	3	49	ALL
171	3	50	ALL
172	3	51	ALL
173	3	52	ALL
174	3	53	ALL
175	3	56	ALL
176	3	58	ALL
177	3	59	ALL
179	3	61	ALL
180	3	62	ALL
181	3	63	ALL
182	3	65	ALL
183	3	66	ALL
184	3	67	ALL
185	3	68	ALL
153	3	27	OWN
186	4	1	TEAM
187	4	2	OWN
188	4	5	ALL
189	4	9	ALL
190	4	14	TEAM
191	4	16	TEAM
192	4	20	TEAM
193	4	21	TEAM
194	4	22	TEAM
195	4	23	TEAM
196	4	25	TEAM
197	4	26	TEAM
198	4	29	ALL
199	4	33	TEAM
200	4	38	OWN
201	4	40	TEAM
202	4	43	OWN
203	4	51	TEAM
204	4	53	TEAM
205	4	58	TEAM
206	4	59	TEAM
207	4	60	TEAM
208	4	65	TEAM
209	4	66	TEAM
210	4	68	TEAM
211	5	2	OWN
212	5	7	ALL
213	5	9	ALL
214	5	10	OWN
215	5	14	OWN
216	5	20	OWN
217	5	21	OWN
218	5	27	OWN
219	5	29	ALL
220	5	32	OWN
221	5	38	OWN
222	5	40	OWN
223	5	43	OWN
224	5	46	OWN
225	5	48	OWN
226	5	51	OWN
227	5	55	OWN
228	5	58	OWN
229	5	59	OWN
230	5	60	OWN
231	5	65	OWN
233	5	68	OWN
232	5	66	ALL
178	3	60	OWN
\.


ALTER TABLE public.app_role_permission ENABLE TRIGGER ALL;

--
-- Data for Name: app_user_role; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.app_user_role DISABLE TRIGGER ALL;

COPY public.app_user_role (id, employee_id, role_id) FROM stdin;
1	1	1
2	2	3
3	3	2
4	4	4
5	5	4
6	12	5
7	6	5
8	7	5
9	10	5
10	8	5
11	11	5
12	9	5
14	14	5
15	15	5
16	16	5
17	17	5
18	18	5
19	19	5
20	20	5
21	21	5
22	13	5
23	22	5
24	23	5
25	24	5
26	25	5
27	26	5
28	27	5
29	28	5
30	29	5
31	30	5
32	31	5
\.


ALTER TABLE public.app_user_role ENABLE TRIGGER ALL;

--
-- Data for Name: department; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.department DISABLE TRIGGER ALL;

COPY public.department (id, code, name) FROM stdin;
1	EXEC	Executive
2	CREATIVE	Creative
3	PROD	Production
4	CLIENT	Client Services
5	HR	People & Culture
6	FIN	Finance
7	DEV	Software Development
8	ACC	Accounts
9	DEV2	Prod
\.


ALTER TABLE public.department ENABLE TRIGGER ALL;

--
-- Data for Name: designation; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.designation DISABLE TRIGGER ALL;

COPY public.designation (id, code, name, approver_role) FROM stdin;
1	SUPER_ADMIN	Super Admin	SUPERADMIN
2	ADMIN	Administrator	ADMIN
3	HR_LEAD	HR Lead	HR
4	CREATIVE_DIR	Creative Director	MANAGER
5	PROD_MGR	Production Manager	MANAGER
6	DESIGNER	Designer	\N
7	PRODUCER	Producer	\N
8	ACCOUNT_MGR	Account Manager	\N
9	FINANCE	Finance Officer	\N
\.


ALTER TABLE public.designation ENABLE TRIGGER ALL;

--
-- Data for Name: employee; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.employee DISABLE TRIGGER ALL;

COPY public.employee (id, employee_code, full_name, email, base_salary, hourly_rate, custom_fields, active, created_at, updated_at, department_id, designation_id, manager_id, preferred_name, job_title, location_id, team_id, employment_type, start_date, probation_end, contract_end, pay_currency, status, phone, personal_email, work_pattern, photo_object_key, date_of_birth, address, emergency_name, emergency_relation, emergency_phone, exit_date, exit_reason) FROM stdin;
1	QII-0001	Asman Rahman	sazzad53913@gmail.com	150000.0000	0.0000	{"gender": "M", "country": "BD"}	t	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	1	1	\N	Asman	Founder & Super Admin	1	\N	FULL_TIME	2019-01-15	\N	\N	BDT	Active	+8801700000001	\N	FIXED	\N	\N	\N	\N	\N	\N	\N	\N
15	QII-0033	No Manager Test	nomgr_1793353692090@qiicreative.local	0.0000	0.0000	{"documents": [{"name": "HRMS_Microservices_Developer_Guide.pdf", "type": "Contract", "fileId": "33", "uploadedAt": "2026-11-09T19:36:44.309697700Z", "uploadedBy": 1}, {"name": "convocation_application_confiramtion.pdf", "type": "Contract", "fileId": "34", "uploadedAt": "2026-11-09T19:39:39.913443100Z", "uploadedBy": 1}, {"name": "Qii HRMS v1 Software Requirements Specification.pdf", "type": "Contract", "fileId": "35", "uploadedAt": "2026-11-09T19:40:07.829190Z", "uploadedBy": 1}]}	t	2026-10-30 09:48:12.487365+00	2026-11-09 19:40:07.842756+00	1	1	\N	\N	Super Admin	1	\N	FULL_TIME	2026-11-01	\N	\N	BDT	Active	\N	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
16	QII-0034	With Manager Test	withmgr_1793353692841@qiicreative.local	0.0000	0.0000	{}	t	2026-10-30 09:48:12.922573+00	2026-11-09 20:35:11.1075+00	2	6	1	\N	Designer	1	\N	FULL_TIME	2026-11-01	\N	\N	BDT	Active	\N	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
2	QII-0002	Nadia Chowdhury	vendor.brollex@gmail.com	95000.0000	0.0000	{"gender": "F", "country": "BD"}	t	2026-10-30 06:24:07.91854+00	2026-11-09 20:35:12.409475+00	5	3	3	Nadia	Head of People	1	\N	FULL_TIME	2020-03-01	\N	\N	BDT	Active	+8801700000002	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
19	QII-0037	With Manager Test	withmgr_1793358407597@qiicreative.local	0.0000	0.0000	{}	t	2026-10-30 11:06:47.671737+00	2026-10-30 11:06:47.671737+00	2	6	4	\N	Designer	1	\N	FULL_TIME	2026-11-01	\N	\N	BDT	Active	\N	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
6	QII-0020	Tania Sultana	tania@qiicreative.local	65000.0000	0.0000	{"gender": "F", "country": "BD"}	t	2026-10-30 06:24:07.91854+00	2026-10-30 09:10:55.961008+00	2	6	4	Tania	Senior Designer	1	1	FULL_TIME	2022-01-15	\N	\N	BDT	Active	+8801700000020	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
10	QII-0024	Priya Das	priya@qiicreative.local	45000.0000	0.0000	{"gender": "F", "country": "BD"}	t	2026-10-30 06:24:07.91854+00	2026-10-30 09:10:57.486626+00	2	6	4	Priya	Junior Designer	1	1	FULL_TIME	2024-03-01	\N	\N	BDT	Active	+8801700000024	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
21	QII-0039	With Manager Test	withmgr_1793358491642@qiicreative.local	0.0000	0.0000	{}	t	2026-10-30 11:08:11.748971+00	2026-10-30 11:08:11.748971+00	2	6	4	\N	Designer	1	\N	FULL_TIME	2026-11-01	\N	\N	BDT	Active	\N	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
17	QII-0035	With Manager Test	withmgr_1793353742811@qiicreative.local	0.0000	0.0000	{}	t	2026-10-30 09:49:02.85163+00	2026-10-30 09:49:02.85163+00	2	6	4	\N	Designer	1	\N	FULL_TIME	2026-11-01	\N	\N	BDT	Active	\N	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
20	QII-0038	With Manager Test	withmgr_1793358469688@qiicreative.local	0.0000	0.0000	{}	t	2026-10-30 11:07:49.74267+00	2026-10-30 11:07:49.74267+00	2	6	4	\N	Designer	1	\N	FULL_TIME	2026-11-01	\N	\N	BDT	Active	\N	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
9	QII-0023	Karim Ali	sazzad.impit@gmail.com	58000.0000	0.0000	{"gender": "M", "country": "BD"}	t	2026-10-30 06:24:07.91854+00	2026-10-31 06:49:15.685225+00	4	8	3	Karim	Account Manager	1	\N	FULL_TIME	2023-01-08	\N	\N	BDT	Active	+8801700000023	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
3	QII-0003	Rafiul Islam	zohurulsazzad@gmail.com	90000.0000	0.0000	{"gender": "M", "country": "BD"}	t	2026-10-30 06:24:07.91854+00	2026-11-08 07:08:05.294746+00	1	2	8	Rafi	Operations Admin	1	\N	FULL_TIME	2020-06-01	\N	\N	BDT	Active	+8801700000003	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
18	QII-0036	With Manager Test	withmgr_1793358322442@qiicreative.local	0.0000	0.0000	{"documents": [{"name": "convocation_application_confiramtion.pdf", "type": "Contract", "fileId": "15", "uploadedAt": "2026-11-08T10:59:31.049385800Z", "uploadedBy": 1}]}	t	2026-10-30 11:05:22.506385+00	2026-11-08 10:59:31.049386+00	2	6	4	\N	Designer	1	\N	FULL_TIME	2026-11-01	\N	\N	BDT	Active	\N	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
12	QII-0030	Farhana Begum	farhana@qiicreative.local	70000.0000	0.0000	{"gender": "F", "country": "BD", "documents": [{"name": "Dami PDF", "type": "Contract", "fileId": "18", "uploadedAt": "2026-11-09T09:14:09.259346300Z", "uploadedBy": 1}]}	t	2026-10-30 06:24:07.91854+00	2026-11-09 09:14:09.259346+00	2	9	10	Farhana	Finance Officer	1	1	FULL_TIME	2021-11-01	\N	\N	BDT	Active	+8801700000030	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
8	QII-0022	Sophie Nguyen	sophie@qiicreative.local	72000.0000	0.0000	{"gender": "F", "country": "AU"}	t	2026-10-30 06:24:07.91854+00	2026-10-30 09:12:12.64751+00	3	7	5	Sophie	Producer	2	\N	FULL_TIME	2022-09-12	\N	\N	AUD	Active	+61400000022	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
5	QII-0011	Marcus Blake	marcus@qiicreative.local	95000.0000	0.0000	{"gender": "M", "country": "AU"}	t	2026-10-30 06:24:07.91854+00	2026-10-30 09:04:27.379849+00	3	5	1	Marcus	Production Manager	2	3	FULL_TIME	2021-08-01	\N	\N	AUD	Active	+61400000011	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
11	QII-0025	Owen Fraser	owen@qiicreative.local	0.0000	45.0000	{"gender": "M", "country": "AU", "documents": [{"name": "Demo", "type": "Contract", "fileId": "16", "uploadedAt": "2026-11-08T19:10:07.280615700Z", "uploadedBy": 1}]}	t	2026-10-30 06:24:07.91854+00	2026-11-08 19:10:07.280616+00	2	7	5	Owen	Junior Producer	2	1	HOURLY	2024-06-15	\N	\N	AUD	Active	+61400000025	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
13	QII-0031	Sazzad Khan	zohurulsazzad@qiicreative.com.au	0.0000	200.0000	{"payType": "MONTHLY", "documents": [{"name": "Qii HRMS v1 Software Requirements Specification.pdf", "type": "Contract", "fileId": "12", "uploadedAt": "2026-10-30T06:34:04.944611900Z", "uploadedBy": 2}], "onboardingChecklist": true}	t	2026-10-30 06:28:03.857691+00	2026-10-30 06:34:04.947631+00	4	7	3	\N	Producer	1	4	FULL_TIME	2026-10-30	\N	\N	BDT	Active	\N	zohurulsazzad@qiicreative.com.au	HOURLY	\N	\N	\N	\N	\N	\N	\N	\N
14	QII-0032	Anindya Basak	jeksdhre@qiicreative.com.au	0.0000	200.0000	{"payType": "HOURLY", "documents": [{"name": "Qii HRMS v1 Software Requirements Specification (1).pdf", "type": "Contract", "fileId": "14", "expiresAt": "2026-11-01", "uploadedAt": "2026-10-31T07:52:28.203805600Z", "uploadedBy": 1}], "onboardingChecklist": true}	t	2026-10-30 09:20:07.548706+00	2026-10-31 07:52:28.203806+00	7	7	1	\N	Producer	1	5	FULL_TIME	2026-10-30	\N	\N	BDT	Active	\N	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
4	QII-0010	Lina Ahmed	lina@qiicreative.local	110000.0000	0.0000	{"gender": "F", "country": "BD", "payType": "MONTHLY", "documents": [{"name": "convocation_application_confiramtion.pdf", "type": "Contract", "fileId": "19", "uploadedAt": "2026-11-09T12:11:01.784566900Z", "uploadedBy": 1}], "photoFileId": 36, "photoObjectKey": "qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2"}	t	2026-10-30 06:24:07.91854+00	2026-11-09 20:42:28.329308+00	2	4	1	Lina	Creative Director	1	1	FULL_TIME	2021-02-10	\N	\N	BDT	Active	+8801700000010	\N	LOCATION_DEFAULT	qii-hrms-documents/released/4f2ab877-ba4c-4a5a-ae46-44d5b19ec0e2	\N	\N	\N	\N	\N	\N	\N
7	QII-0021	Imran Hossain	imran@qiicreative.local	60000.0000	0.0000	{"gender": "M", "country": "BD", "payType": "MONTHLY"}	t	2026-10-30 06:24:07.91854+00	2026-11-09 22:02:56.002873+00	7	6	4	Imran	Motion Designer	1	5	FULL_TIME	2022-05-01	\N	\N	BDT	Active	+8801700000021	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
26	QII-0044	No Checklist	noct.1794249629@qiicreative.com.au	0.0000	0.0000	{}	t	2026-11-09 18:40:30.00228+00	2026-11-09 18:40:30.00228+00	2	6	4	\N	Designer	1	\N	FULL_TIME	2026-11-10	\N	\N	BDT	Active	\N	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
27	QII-0045	Debug Tx	dbg.1794249701@qiicreative.com.au	0.0000	0.0000	{}	t	2026-11-09 18:41:42.074385+00	2026-11-09 18:41:42.074385+00	2	6	4	\N	Designer	1	\N	FULL_TIME	2026-11-10	\N	\N	BDT	Active	\N	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
25	QII-0043	Hourly Test	hourly.test.1794249612@qiicreative.com.au	0.0000	0.0000	{"payType": "HOURLY", "onboardingChecklist": true}	t	2026-11-09 18:40:12.742582+00	2026-11-09 20:40:02.926358+00	2	6	27	\N	Designer	1	\N	FULL_TIME	2026-11-10	\N	\N	BDT	Active	\N	\N	HOURLY	\N	\N	\N	\N	\N	\N	\N	\N
28	QII-0046	Photo Ok	photo.ok.1794250004@qiicreative.com.au	0.0000	0.0000	{"photoFileId": 30, "photoObjectKey": "qii-hrms-documents/released/62e67aea-3903-47b3-a984-98a5ec7058f4", "onboardingChecklist": true}	t	2026-11-09 18:46:44.966358+00	2026-11-09 18:46:44.966358+00	2	6	4	\N	Designer	1	\N	FULL_TIME	2026-11-10	\N	\N	BDT	Active	\N	\N	LOCATION_DEFAULT	qii-hrms-documents/released/62e67aea-3903-47b3-a984-98a5ec7058f4	\N	\N	\N	\N	\N	\N	\N
23	QII-0041	Sazzad Khan	zohurulsaz@qiicreative.com.au	1000.0000	0.0000	{"address": "140-17 84TH DRIVE, APT - 4C", "payType": "MONTHLY", "dateOfBirth": "2026-12-04", "photoFileId": 29, "emergencyName": "Oni", "emergencyPhone": "0129438546", "photoObjectKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2", "emergencyRelation": "Friend", "onboardingChecklist": true, "emergencyRelationship": "Friend"}	t	2026-11-09 18:33:55.352064+00	2026-11-09 18:33:55.352064+00	7	6	14	Soikat	Developer	3	5	CONTRACT	2026-11-10	2026-12-12	2028-07-06	AUD	Active	0921384873	zohurulsazzad@gmail.com	LOCATION_DEFAULT	qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2	2026-12-04	140-17 84TH DRIVE, APT - 4C	Oni	Friend	0129438546	\N	\N
22	QII-0040	Sazzad Khan	zohurulsazza@qiicreative.com.au	1000.0000	0.0000	{"address": "140-17 84TH DRIVE, APT - 4C", "payType": "MONTHLY", "exitDate": "2026-11-10", "exitReason": "jknjsdbhjhfbe", "dateOfBirth": "2026-12-04", "photoFileId": 29, "emergencyName": "Oni", "emergencyPhone": "0129438546", "photoObjectKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2", "emergencyRelation": "Friend", "onboardingChecklist": true, "emergencyRelationship": "Friend", "offboardOverrideReason": "jhygyugytf"}	f	2026-11-09 18:32:20.899872+00	2026-11-09 19:23:47.801022+00	7	6	14	Soikat	Developer	3	5	CONTRACT	2026-11-10	2026-12-12	2028-07-06	AUD	Terminated	0921384873	zohurulsazzad@gmail.com	LOCATION_DEFAULT	qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2	2026-12-04	140-17 84TH DRIVE, APT - 4C	Oni	Friend	0129438546	2026-11-10	jknjsdbhjhfbe
24	QII-0042	Sazzad Khan	zohuruls@qiicreative.com.au	30000.0000	0.0000	{"address": "140-17 84TH DRIVE, APT - 4C", "payType": "MONTHLY", "dateOfBirth": "2026-12-04", "photoFileId": 29, "emergencyName": "Oni", "emergencyPhone": "0129438546", "photoObjectKey": "qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2", "emergencyRelation": "Friend", "onboardingChecklist": true, "emergencyRelationship": "Friend"}	t	2026-11-09 18:35:24.568303+00	2026-11-09 19:21:56.989171+00	7	6	14	Soikat	Developer2	3	5	CONTRACT	2026-11-10	2026-12-12	2028-07-22	AUD	Active	0921384873	zohurulsazzad@gmail.com	LOCATION_DEFAULT	qii-hrms-documents/released/11c10ee4-c5ab-42fd-afc2-8bb3dc787fa2	2026-12-04	140-17 84TH DRIVE, APT - 4C	Oni	Friend	0129438546	\N	\N
29	QII-0051	Tanvir Hasan	tanvir@qiicreative.local	0.0000	0.0000	{}	t	2026-11-10 10:49:56.46804+00	2026-11-10 10:49:56.46804+00	2	6	4	Tanvir	Junior Designer	1	1	FULL_TIME	2026-09-14	\N	\N	BDT	Active	\N	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
30	QII-0052	Rumana Akter	rumana@qiicreative.local	0.0000	0.0000	{}	f	2026-11-10 10:49:56.46804+00	2026-11-10 10:49:56.46804+00	2	6	3	Rumana	Copywriter	1	1	FULL_TIME	2023-05-01	\N	\N	BDT	Resigned	\N	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	2026-10-15	Resigned — relocating
31	QII-0050	Arpa Sen	arpa@qiicreative.local	0.0000	0.0000	{}	t	2026-11-10 10:49:56.46804+00	2026-11-10 10:49:56.46804+00	2	6	4	Arpa	Designer	1	1	FULL_TIME	2025-01-01	\N	\N	BDT	Active	\N	\N	LOCATION_DEFAULT	\N	\N	\N	\N	\N	\N	\N	\N
\.


ALTER TABLE public.employee ENABLE TRIGGER ALL;

--
-- Data for Name: employment_field_history; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.employment_field_history DISABLE TRIGGER ALL;

COPY public.employment_field_history (id, employee_id, field_name, old_value, new_value, changed_by, changed_at) FROM stdin;
1	2	workPattern	FIXED	DAYS_6	1	2026-10-30 06:55:03.589305+00
2	2	workPattern	DAYS_6	HOURLY	1	2026-10-30 06:55:03.999823+00
3	2	workPattern	HOURLY	LOCATION_DEFAULT	1	2026-10-30 06:55:04.350624+00
4	4	workPattern	FIXED	LOCATION_DEFAULT	1	2026-10-30 09:03:23.028078+00
5	3	workPattern	FIXED	DAYS_6	1	2026-10-30 09:03:23.584214+00
6	3	workPattern	DAYS_6	LOCATION_DEFAULT	1	2026-10-30 09:03:23.745091+00
7	12	departmentId	6	2	1	2026-10-30 09:04:26.799967+00
8	12	teamId	\N	1	1	2026-10-30 09:04:26.802718+00
9	12	workPattern	FIXED	LOCATION_DEFAULT	1	2026-10-30 09:04:26.805612+00
10	5	locationId	2	1	1	2026-10-30 09:04:27.251251+00
11	5	workPattern	FIXED	DAYS_6	1	2026-10-30 09:04:27.254977+00
12	5	workPattern	DAYS_6	LOCATION_DEFAULT	1	2026-10-30 09:04:27.423711+00
13	12	workPattern	LOCATION_DEFAULT	DAYS_6	1	2026-10-30 09:04:51.604303+00
14	12	workPattern	DAYS_6	LOCATION_DEFAULT	1	2026-10-30 09:04:52.387792+00
15	7	teamId	2	1	1	2026-10-30 09:10:54.85587+00
16	7	workPattern	FIXED	LOCATION_DEFAULT	1	2026-10-30 09:10:54.874027+00
17	6	workPattern	FIXED	DAYS_6	1	2026-10-30 09:10:55.693273+00
18	6	workPattern	DAYS_6	LOCATION_DEFAULT	1	2026-10-30 09:10:55.989183+00
19	10	workPattern	FIXED	DAYS_6	1	2026-10-30 09:10:57.064972+00
20	10	workPattern	DAYS_6	LOCATION_DEFAULT	1	2026-10-30 09:10:57.53343+00
21	11	departmentId	3	2	1	2026-10-30 09:12:11.713491+00
22	11	teamId	3	1	1	2026-10-30 09:12:11.721816+00
23	11	locationId	2	1	1	2026-10-30 09:12:11.727224+00
24	11	workPattern	HOURLY	LOCATION_DEFAULT	1	2026-10-30 09:12:11.733407+00
25	8	locationId	2	1	1	2026-10-30 09:12:12.500072+00
26	8	workPattern	FIXED	DAYS_6	1	2026-10-30 09:12:12.508242+00
27	8	teamId	3	\N	1	2026-10-30 09:12:12.670418+00
28	8	workPattern	DAYS_6	LOCATION_DEFAULT	1	2026-10-30 09:12:12.67529+00
29	9	teamId	4	\N	1	2026-10-30 09:12:13.456957+00
30	9	workPattern	FIXED	DAYS_6	1	2026-10-30 09:12:13.461401+00
31	9	workPattern	DAYS_6	LOCATION_DEFAULT	1	2026-10-30 09:12:13.707827+00
32	14	managerId	\N	1	1	2026-10-30 09:24:28.227072+00
33	14	baseSalary	0.0000	0	1	2026-10-30 09:24:28.234359+00
34	14	hourlyRate	200.0000	200	1	2026-10-30 09:24:28.238407+00
35	24	jobTitle	Developer	Developer2	1	2026-11-09 19:21:57.025146+00
36	24	contractEnd	2028-07-06	2028-07-22	1	2026-11-09 19:21:57.060635+00
37	24	baseSalary	1000.0000	30000	1	2026-11-09 19:21:57.064119+00
38	24	hourlyRate	0.0000	0	1	2026-11-09 19:21:57.071269+00
39	22	status	Active	Terminated	1	2026-11-09 19:23:47.786908+00
40	22	exitDate	\N	2026-11-10	1	2026-11-09 19:23:47.791836+00
41	22	exitReason	\N	jknjsdbhjhfbe	1	2026-11-09 19:23:47.796158+00
42	22	baseSalary	1000.0000	1000	1	2026-11-09 19:23:47.815292+00
43	22	hourlyRate	0.0000	0	1	2026-11-09 19:23:47.818667+00
44	2	managerId	3	14	1	2026-11-09 20:32:13.188+00
45	2	managerId	14	3	1	2026-11-09 20:32:13.908367+00
46	16	managerId	4	14	1	2026-11-09 20:32:34.991229+00
47	16	managerId	14	4	1	2026-11-09 20:32:35.853601+00
48	2	managerId	3	14	1	2026-11-09 20:32:47.335011+00
49	2	managerId	14	3	1	2026-11-09 20:32:48.189418+00
50	16	managerId	4	1	\N	2026-11-09 20:35:11.112989+00
51	2	managerId	3	14	1	2026-11-09 20:35:11.319507+00
52	2	managerId	14	3	\N	2026-11-09 20:35:12.295454+00
53	25	managerId	4	27	1	2026-11-09 20:40:02.959106+00
54	25	baseSalary	0.0000	0	1	2026-11-09 20:40:02.976822+00
55	25	hourlyRate	0.0000	0	1	2026-11-09 20:40:02.97986+00
56	4	baseSalary	110000.0000	110000	1	2026-11-09 20:42:28.34013+00
57	4	hourlyRate	0.0000	0	1	2026-11-09 20:42:28.344625+00
58	7	departmentId	2	7	1	2026-11-09 22:02:56.102805+00
59	7	teamId	1	5	1	2026-11-09 22:02:56.142701+00
60	7	baseSalary	60000.0000	60000	1	2026-11-09 22:02:56.148017+00
61	7	hourlyRate	0.0000	0	1	2026-11-09 22:02:56.152042+00
\.


ALTER TABLE public.employment_field_history ENABLE TRIGGER ALL;

--
-- Data for Name: ext_indexed_attribute; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.ext_indexed_attribute DISABLE TRIGGER ALL;

COPY public.ext_indexed_attribute (employee_id, attr_key, attr_value) FROM stdin;
\.


ALTER TABLE public.ext_indexed_attribute ENABLE TRIGGER ALL;

--
-- Data for Name: location; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.location DISABLE TRIGGER ALL;

COPY public.location (id, code, name, country, time_zone, default_currency) FROM stdin;
1	BD-DHK	Dhaka	Bangladesh	Asia/Dhaka	BDT
2	AU-DRW	Darwin	Australia	Australia/Darwin	AUD
3	AUS-SYD	Sydney	Australia	Australia/Sydney	AUD
\.


ALTER TABLE public.location ENABLE TRIGGER ALL;

--
-- Data for Name: people_audit_log; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.people_audit_log DISABLE TRIGGER ALL;

COPY public.people_audit_log (id, at, actor_employee_id, action, entity, entity_id, before_json, after_json, ip) FROM stdin;
1	2026-10-30 06:34:05.347182+00	2	CREATE	EmployeeDocument	13:12	\N	{"name": "Qii HRMS v1 Software Requirements Specification.pdf", "type": "Contract", "fileId": "12", "uploadedAt": "2026-10-30T06:34:04.944611900Z", "uploadedBy": 2}	\N
2	2026-10-30 06:34:10.263092+00	2	DOWNLOAD	EmployeeDocument	13:12	{"name": "Qii HRMS v1 Software Requirements Specification.pdf", "type": "Contract", "fileId": "12", "uploadedAt": "2026-10-30T06:34:04.944611900Z", "uploadedBy": 2, "documentType": "Contract"}	{"name": "Qii HRMS v1 Software Requirements Specification.pdf", "type": "Contract", "fileId": "12", "accessedAt": "2026-10-30T06:34:10.260821100Z", "uploadedAt": "2026-10-30T06:34:04.944611900Z", "uploadedBy": 2, "accessAction": "DOWNLOAD", "documentType": "Contract"}	\N
194	2026-11-08 07:08:47.883831+00	1	DOWNLOAD	EmployeeDocument	14:14	{"name": "Qii HRMS v1 Software Requirements Specification (1).pdf", "type": "Contract", "fileId": "14", "expiresAt": "2026-11-01", "uploadedAt": "2026-10-31T07:52:28.203805600Z", "uploadedBy": 1, "documentType": "Contract"}	{"name": "Qii HRMS v1 Software Requirements Specification (1).pdf", "type": "Contract", "fileId": "14", "expiresAt": "2026-11-01", "accessedAt": "2026-11-08T07:08:47.861420400Z", "uploadedAt": "2026-10-31T07:52:28.203805600Z", "uploadedBy": 1, "accessAction": "DOWNLOAD", "documentType": "Contract"}	\N
196	2026-11-08 10:59:31.082359+00	1	CREATE	EmployeeDocument	18:15	\N	{"name": "convocation_application_confiramtion.pdf", "type": "Contract", "fileId": "15", "uploadedAt": "2026-11-08T10:59:31.049385800Z", "uploadedBy": 1}	\N
198	2026-11-08 19:10:07.309809+00	1	CREATE	EmployeeDocument	11:16	\N	{"name": "Demo", "type": "Contract", "fileId": "16", "uploadedAt": "2026-11-08T19:10:07.280615700Z", "uploadedBy": 1}	\N
199	2026-11-08 19:10:26.66964+00	1	DOWNLOAD	EmployeeDocument	11:16	{"name": "Demo", "type": "Contract", "fileId": "16", "uploadedAt": "2026-11-08T19:10:07.280615700Z", "uploadedBy": 1, "documentType": "Contract"}	{"name": "Demo", "type": "Contract", "fileId": "16", "accessedAt": "2026-11-08T19:10:26.668021600Z", "uploadedAt": "2026-11-08T19:10:07.280615700Z", "uploadedBy": 1, "accessAction": "DOWNLOAD", "documentType": "Contract"}	\N
200	2026-11-09 09:14:09.283603+00	1	CREATE	EmployeeDocument	12:18	\N	{"name": "Dami PDF", "type": "Contract", "fileId": "18", "uploadedAt": "2026-11-09T09:14:09.259346300Z", "uploadedBy": 1}	\N
206	2026-11-09 18:40:12.903401+00	1	CREATE	Checklist	10	\N	{"type": "ONBOARDING", "employeeId": 25, "templateId": "1"}	\N
195	2026-11-08 07:08:57.555005+00	1	DOWNLOAD	EmployeeDocument	14:14	{"name": "Qii HRMS v1 Software Requirements Specification (1).pdf", "type": "Contract", "fileId": "14", "expiresAt": "2026-11-01", "uploadedAt": "2026-10-31T07:52:28.203805600Z", "uploadedBy": 1, "documentType": "Contract"}	{"name": "Qii HRMS v1 Software Requirements Specification (1).pdf", "type": "Contract", "fileId": "14", "expiresAt": "2026-11-01", "accessedAt": "2026-11-08T07:08:57.552705900Z", "uploadedAt": "2026-10-31T07:52:28.203805600Z", "uploadedBy": 1, "accessAction": "DOWNLOAD", "documentType": "Contract"}	\N
197	2026-11-08 10:59:37.761051+00	1	DOWNLOAD	EmployeeDocument	18:15	{"name": "convocation_application_confiramtion.pdf", "type": "Contract", "fileId": "15", "uploadedAt": "2026-11-08T10:59:31.049385800Z", "uploadedBy": 1, "documentType": "Contract"}	{"name": "convocation_application_confiramtion.pdf", "type": "Contract", "fileId": "15", "accessedAt": "2026-11-08T10:59:37.758979700Z", "uploadedAt": "2026-11-08T10:59:31.049385800Z", "uploadedBy": 1, "accessAction": "DOWNLOAD", "documentType": "Contract"}	\N
201	2026-11-09 09:14:24.775781+00	1	DOWNLOAD	EmployeeDocument	12:18	{"name": "Dami PDF", "type": "Contract", "fileId": "18", "uploadedAt": "2026-11-09T09:14:09.259346300Z", "uploadedBy": 1, "documentType": "Contract"}	{"name": "Dami PDF", "type": "Contract", "fileId": "18", "accessedAt": "2026-11-09T09:14:24.774609900Z", "uploadedAt": "2026-11-09T09:14:09.259346300Z", "uploadedBy": 1, "accessAction": "DOWNLOAD", "documentType": "Contract"}	\N
207	2026-11-09 18:46:45.194401+00	1	CREATE	Checklist	11	\N	{"type": "ONBOARDING", "employeeId": 28, "templateId": "1"}	\N
202	2026-11-09 12:11:01.83496+00	1	CREATE	EmployeeDocument	4:19	\N	{"name": "convocation_application_confiramtion.pdf", "type": "Contract", "fileId": "19", "uploadedAt": "2026-11-09T12:11:01.784566900Z", "uploadedBy": 1}	\N
208	2026-11-09 19:31:51.464419+00	1	CREATE	EmployeeDocument	15:31	\N	{"name": "Issues.docx", "type": "Contract", "fileId": "31", "uploadedAt": "2026-11-09T19:31:51.437387900Z", "uploadedBy": 1}	\N
203	2026-11-09 18:32:23.953931+00	1	CREATE	Checklist	7	\N	{"type": "ONBOARDING", "employeeId": 22, "templateId": "1"}	\N
104	2026-10-30 12:12:08.859508+00	1	CREATE	Checklist	3	\N	{"name": "E2E Onboard 1793362328796", "type": "ONBOARDING"}	\N
209	2026-11-09 19:36:20.72011+00	1	CREATE	EmployeeDocument	15:32	\N	{"name": "Asst_Software_Engineer_Project_Assignment.pdf", "type": "Contract", "fileId": "32", "scanStatus": "QUARANTINED", "uploadedAt": "2026-11-09T19:36:20.696099800Z", "uploadedBy": 1, "downloadable": false}	\N
210	2026-11-09 19:36:33.856744+00	1	DELETE	EmployeeDocument	15:31	{"name": "Issues.docx", "type": "Contract", "fileId": "31", "uploadedAt": "2026-11-09T19:31:51.437387900Z", "uploadedBy": 1, "documentType": "Contract"}	\N	\N
211	2026-11-09 19:36:44.326779+00	1	CREATE	EmployeeDocument	15:33	\N	{"name": "HRMS_Microservices_Developer_Guide.pdf", "type": "Contract", "fileId": "33", "scannedAt": "2026-11-09T19:36:44.202105Z", "releasedAt": "2026-11-09T19:36:44.246179Z", "scanDetail": "heuristic-clean", "scanStatus": "RELEASED", "uploadedAt": "2026-11-09T19:36:44.309697700Z", "uploadedBy": 1, "downloadable": true}	\N
212	2026-11-09 19:39:39.934407+00	1	CREATE	EmployeeDocument	15:34	\N	{"name": "convocation_application_confiramtion.pdf", "type": "Contract", "fileId": "34", "scannedAt": "2026-11-09T19:39:39.727909Z", "releasedAt": "2026-11-09T19:39:39.780215Z", "scanDetail": "heuristic-clean", "scanStatus": "RELEASED", "uploadedAt": "2026-11-09T19:39:39.913443100Z", "uploadedBy": 1, "downloadable": true}	\N
213	2026-11-09 19:39:53.678816+00	1	DELETE	EmployeeDocument	15:32	{"name": "Asst_Software_Engineer_Project_Assignment.pdf", "type": "Contract", "fileId": "32", "uploadedAt": "2026-11-09T19:36:20.696099800Z", "uploadedBy": 1, "documentType": "Contract"}	\N	\N
214	2026-11-09 19:40:07.843825+00	1	CREATE	EmployeeDocument	15:35	\N	{"name": "Qii HRMS v1 Software Requirements Specification.pdf", "type": "Contract", "fileId": "35", "scannedAt": "2026-11-09T19:40:07.668769Z", "releasedAt": "2026-11-09T19:40:07.719773Z", "scanDetail": "heuristic-clean", "scanStatus": "RELEASED", "uploadedAt": "2026-11-09T19:40:07.829190Z", "uploadedBy": 1, "downloadable": true}	\N
204	2026-11-09 18:33:55.978373+00	1	CREATE	Checklist	8	\N	{"type": "ONBOARDING", "employeeId": 23, "templateId": "1"}	\N
205	2026-11-09 18:35:25.375435+00	1	CREATE	Checklist	9	\N	{"type": "ONBOARDING", "employeeId": 24, "templateId": "1"}	\N
105	2026-10-30 12:12:09.171395+00	1	CREATE	Checklist	4	\N	{"type": "ONBOARDING", "employeeId": 6, "templateId": "3"}	\N
106	2026-10-30 12:12:09.42649+00	1	UPDATE	Checklist	4	{"completed": false, "itemIndex": 0}	{"completed": true, "itemIndex": 0}	\N
116	2026-10-30 12:12:33.563769+00	1	CREATE	Checklist	5	\N	{"name": "E2E Onboard 1793362353495", "type": "ONBOARDING"}	\N
117	2026-10-30 12:12:33.686436+00	1	CREATE	Checklist	6	\N	{"type": "ONBOARDING", "employeeId": 6, "templateId": "5"}	\N
118	2026-10-30 12:12:33.816203+00	1	UPDATE	Checklist	6	{"completed": false, "itemIndex": 0}	{"completed": true, "itemIndex": 0}	\N
139	2026-10-31 07:52:28.211811+00	1	CREATE	EmployeeDocument	14:14	\N	{"name": "Qii HRMS v1 Software Requirements Specification (1).pdf", "type": "Contract", "fileId": "14", "expiresAt": "2026-11-01", "uploadedAt": "2026-10-31T07:52:28.203805600Z", "uploadedBy": 1}	\N
\.


ALTER TABLE public.people_audit_log ENABLE TRIGGER ALL;

--
-- Data for Name: reporting_line_history; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.reporting_line_history DISABLE TRIGGER ALL;

COPY public.reporting_line_history (id, employee_id, manager_id, from_date, to_date, changed_by, created_at) FROM stdin;
2	9	3	2026-10-30	\N	3	2026-10-29 20:50:10.567112+00
1	9	2	2026-10-30	2026-10-30	3	2026-10-29 20:49:53.216774+00
3	12	10	2026-10-30	\N	3	2026-10-29 20:51:19.766171+00
4	2	1	2020-03-01	2026-10-10	1	2026-10-30 05:12:52.823067+00
9	3	8	2026-11-06	\N	1	2026-10-30 05:25:52.820248+00
8	3	1	2020-06-01	2026-11-05	1	2026-10-30 05:25:52.735061+00
10	13	3	2026-10-30	\N	2	2026-10-30 06:28:04.032823+00
11	14	1	2026-10-30	\N	1	2026-10-30 09:24:28.146973+00
13	17	4	2026-11-01	\N	1	2026-10-30 09:49:02.889349+00
14	18	4	2026-11-01	\N	1	2026-10-30 11:05:22.582727+00
15	19	4	2026-11-01	\N	1	2026-10-30 11:06:47.728272+00
16	20	4	2026-11-01	\N	1	2026-10-30 11:07:49.786792+00
17	21	4	2026-11-01	\N	1	2026-10-30 11:08:11.763236+00
18	22	14	2026-11-10	\N	1	2026-11-09 18:32:22.815885+00
19	23	14	2026-11-10	\N	1	2026-11-09 18:33:55.766941+00
20	24	14	2026-11-10	\N	1	2026-11-09 18:35:25.085597+00
22	26	4	2026-11-10	\N	1	2026-11-09 18:40:30.023644+00
23	27	4	2026-11-10	\N	1	2026-11-09 18:41:42.122273+00
24	28	4	2026-11-10	\N	1	2026-11-09 18:46:45.12983+00
7	2	3	2026-10-11	2026-11-09	1	2026-10-30 05:18:57.196445+00
12	16	4	2026-11-01	2026-11-09	1	2026-10-30 09:48:13.002392+00
30	16	4	2026-11-10	2026-11-10	1	2026-11-09 20:32:35.828716+00
35	16	1	2026-11-10	\N	1	2026-11-09 20:33:41.236565+00
37	2	14	2026-11-10	2026-11-09	1	2026-11-09 20:35:11.268384+00
38	2	3	2026-11-10	\N	1	2026-11-09 20:35:11.536979+00
39	25	27	2026-11-10	\N	1	2026-11-09 20:40:02.869545+00
\.


ALTER TABLE public.reporting_line_history ENABLE TRIGGER ALL;

--
-- Data for Name: team; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.team DISABLE TRIGGER ALL;

COPY public.team (id, code, name, department_id, lead_employee_id, max_on_leave, clash_action) FROM stdin;
1	DESIGN	Design Team	2	4	1	ALERT
2	MOTION	Motion Team	2	4	1	ALERT
4	ACCOUNTS	Client Accounts	4	4	1	ALERT
3	PROD	Production Pod	3	5	1	ALERT
5	DEV	Developers	7	7	2	ALERT
6	TMP-NOLEAD	Temp No Lead	1	\N	1	ALERT
7	TMP-EMPTYLEAD	Temp Empty Lead	1	\N	1	ALERT
8	TMP-NULLLEAD	Temp Null Lead	1	\N	1	ALERT
9	TMP-BADLEAD	Bad Lead	1	99999	1	ALERT
10	TMP-NODEPT	No Dept	\N	\N	1	ALERT
12	TMP-WITHLEAD	With Lead	1	2	1	ALERT
11	UI-NOLEAD	With Lead Cleared	1	\N	1	ALERT
14	OPT-LEAD	Optional Lead Team	2	\N	1	ALERT
15	OPT-LEAD2	Optional Lead 2	2	\N	1	ALERT
13	OK-TEAM	Ok Team	1	\N	1	ALERT
16	WITH-LEAD	With Lead Ok	1	1	1	ALERT
17	DEV2	Development	3	4	1	BLOCK
\.


ALTER TABLE public.team ENABLE TRIGGER ALL;

--
-- Data for Name: tenant_field_definition; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.tenant_field_definition DISABLE TRIGGER ALL;

COPY public.tenant_field_definition (id, field_name, storage_type, jsonb_path, extension_table, extension_column, filterable, indexed_flag, required_flag, unique_active, validation_regex) FROM stdin;
1	gender	JSONB	custom_fields.gender	\N	\N	t	f	f	f	\N
2	country	JSONB	custom_fields.country	\N	\N	t	f	f	f	\N
\.


ALTER TABLE public.tenant_field_definition ENABLE TRIGGER ALL;

--
-- Data for Name: tenant_settings; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.tenant_settings DISABLE TRIGGER ALL;

COPY public.tenant_settings (id, settings, updated_at) FROM stdin;
1	{"checklists": [{"id": "1", "name": "New Hire Onboarding", "type": "ONBOARDING", "items": [{"title": "Laptop / workstation ready", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 0}, {"title": "Email & SSO account created", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 1}, {"title": "HRMS profile + role assigned", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 2}, {"title": "Team introduction scheduled", "required": false, "completed": false, "ownerType": "MANAGER", "dueOffsetDays": 3}, {"title": "Policy acknowledgment signed", "required": true, "completed": false, "ownerType": "EMPLOYEE", "dueOffsetDays": 7}], "status": "OPEN", "totalItems": 5, "completedItems": 0}, {"id": "2", "name": "Employee Offboarding", "type": "OFFBOARDING", "items": [{"title": "Asset return checklist", "required": true, "completed": false, "ownerType": "EMPLOYEE", "dueOffsetDays": 0}, {"title": "Access revoked (email/SSO/VPN)", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 1}, {"title": "Final payroll / leave settlement", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 3}, {"title": "Exit interview completed", "required": false, "completed": false, "ownerType": "MANAGER", "dueOffsetDays": -7}], "status": "OPEN", "totalItems": 4, "completedItems": 0}, {"id": "3", "name": "E2E Onboard 1793362328796", "type": "ONBOARDING", "items": [{"title": "Laptop issued", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 1}, {"title": "Buddy intro", "required": false, "completed": false, "ownerType": "MANAGER", "dueOffsetDays": 3}, {"title": "Policies read", "required": true, "completed": false, "ownerType": "EMPLOYEE", "dueOffsetDays": 7}], "status": "OPEN", "totalItems": 3, "completedItems": 0}, {"id": "4", "name": "E2E Onboard 1793362328796", "type": "ONBOARDING", "items": [{"title": "Laptop issued", "dueDate": "2026-10-31", "required": true, "completed": true, "ownerType": "HR", "completedAt": "2026-10-30T12:12:09.425480400Z", "completedBy": 1, "dueOffsetDays": 1, "ownerEmployeeId": 1}, {"title": "Buddy intro", "dueDate": "2026-11-02", "required": false, "completed": false, "ownerType": "MANAGER", "dueOffsetDays": 3, "ownerEmployeeId": 4}, {"title": "Policies read", "dueDate": "2026-11-06", "required": true, "completed": false, "ownerType": "EMPLOYEE", "dueOffsetDays": 7, "ownerEmployeeId": 6}], "status": "OPEN", "baseDate": "2026-10-30", "employeeId": 6, "totalItems": 3, "completedItems": 1}, {"id": "5", "name": "E2E Onboard 1793362353495", "type": "ONBOARDING", "items": [{"title": "Laptop issued", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 1}, {"title": "Buddy intro", "required": false, "completed": false, "ownerType": "MANAGER", "dueOffsetDays": 3}, {"title": "Policies read", "required": true, "completed": false, "ownerType": "EMPLOYEE", "dueOffsetDays": 7}], "status": "OPEN", "totalItems": 3, "completedItems": 0}, {"id": "6", "name": "E2E Onboard 1793362353495", "type": "ONBOARDING", "items": [{"title": "Laptop issued", "dueDate": "2026-10-31", "required": true, "completed": true, "ownerType": "HR", "completedAt": "2026-10-30T12:12:33.815219800Z", "completedBy": 1, "dueOffsetDays": 1, "ownerEmployeeId": 1}, {"title": "Buddy intro", "dueDate": "2026-11-02", "required": false, "completed": false, "ownerType": "MANAGER", "dueOffsetDays": 3, "ownerEmployeeId": 4}, {"title": "Policies read", "dueDate": "2026-11-06", "required": true, "completed": false, "ownerType": "EMPLOYEE", "dueOffsetDays": 7, "ownerEmployeeId": 6}], "status": "OPEN", "baseDate": "2026-10-30", "employeeId": 6, "totalItems": 3, "completedItems": 1}, {"id": "7", "name": "New Hire Onboarding", "type": "ONBOARDING", "items": [{"title": "Laptop / workstation ready", "dueDate": "2026-11-10", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 0, "ownerEmployeeId": 1}, {"title": "Email & SSO account created", "dueDate": "2026-11-11", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 1, "ownerEmployeeId": 1}, {"title": "HRMS profile + role assigned", "dueDate": "2026-11-12", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 2, "ownerEmployeeId": 1}, {"title": "Team introduction scheduled", "dueDate": "2026-11-13", "required": false, "completed": false, "ownerType": "MANAGER", "dueOffsetDays": 3, "ownerEmployeeId": 14}, {"title": "Policy acknowledgment signed", "dueDate": "2026-11-17", "required": true, "completed": false, "ownerType": "EMPLOYEE", "dueOffsetDays": 7, "ownerEmployeeId": 22}], "status": "OPEN", "baseDate": "2026-11-10", "employeeId": 22, "totalItems": 5, "completedItems": 0}, {"id": "8", "name": "New Hire Onboarding", "type": "ONBOARDING", "items": [{"title": "Laptop / workstation ready", "dueDate": "2026-11-10", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 0, "ownerEmployeeId": 1}, {"title": "Email & SSO account created", "dueDate": "2026-11-11", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 1, "ownerEmployeeId": 1}, {"title": "HRMS profile + role assigned", "dueDate": "2026-11-12", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 2, "ownerEmployeeId": 1}, {"title": "Team introduction scheduled", "dueDate": "2026-11-13", "required": false, "completed": false, "ownerType": "MANAGER", "dueOffsetDays": 3, "ownerEmployeeId": 14}, {"title": "Policy acknowledgment signed", "dueDate": "2026-11-17", "required": true, "completed": false, "ownerType": "EMPLOYEE", "dueOffsetDays": 7, "ownerEmployeeId": 23}], "status": "OPEN", "baseDate": "2026-11-10", "employeeId": 23, "totalItems": 5, "completedItems": 0}, {"id": "9", "name": "New Hire Onboarding", "type": "ONBOARDING", "items": [{"title": "Laptop / workstation ready", "dueDate": "2026-11-10", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 0, "ownerEmployeeId": 1}, {"title": "Email & SSO account created", "dueDate": "2026-11-11", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 1, "ownerEmployeeId": 1}, {"title": "HRMS profile + role assigned", "dueDate": "2026-11-12", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 2, "ownerEmployeeId": 1}, {"title": "Team introduction scheduled", "dueDate": "2026-11-13", "required": false, "completed": false, "ownerType": "MANAGER", "dueOffsetDays": 3, "ownerEmployeeId": 14}, {"title": "Policy acknowledgment signed", "dueDate": "2026-11-17", "required": true, "completed": false, "ownerType": "EMPLOYEE", "dueOffsetDays": 7, "ownerEmployeeId": 24}], "status": "OPEN", "baseDate": "2026-11-10", "employeeId": 24, "totalItems": 5, "completedItems": 0}, {"id": "10", "name": "New Hire Onboarding", "type": "ONBOARDING", "items": [{"title": "Laptop / workstation ready", "dueDate": "2026-11-10", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 0, "ownerEmployeeId": 1}, {"title": "Email & SSO account created", "dueDate": "2026-11-11", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 1, "ownerEmployeeId": 1}, {"title": "HRMS profile + role assigned", "dueDate": "2026-11-12", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 2, "ownerEmployeeId": 1}, {"title": "Team introduction scheduled", "dueDate": "2026-11-13", "required": false, "completed": false, "ownerType": "MANAGER", "dueOffsetDays": 3, "ownerEmployeeId": 4}, {"title": "Policy acknowledgment signed", "dueDate": "2026-11-17", "required": true, "completed": false, "ownerType": "EMPLOYEE", "dueOffsetDays": 7, "ownerEmployeeId": 25}], "status": "OPEN", "baseDate": "2026-11-10", "employeeId": 25, "totalItems": 5, "completedItems": 0}, {"id": "11", "name": "New Hire Onboarding", "type": "ONBOARDING", "items": [{"title": "Laptop / workstation ready", "dueDate": "2026-11-10", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 0, "ownerEmployeeId": 1}, {"title": "Email & SSO account created", "dueDate": "2026-11-11", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 1, "ownerEmployeeId": 1}, {"title": "HRMS profile + role assigned", "dueDate": "2026-11-12", "required": true, "completed": false, "ownerType": "HR", "dueOffsetDays": 2, "ownerEmployeeId": 1}, {"title": "Team introduction scheduled", "dueDate": "2026-11-13", "required": false, "completed": false, "ownerType": "MANAGER", "dueOffsetDays": 3, "ownerEmployeeId": 4}, {"title": "Policy acknowledgment signed", "dueDate": "2026-11-17", "required": true, "completed": false, "ownerType": "EMPLOYEE", "dueOffsetDays": 7, "ownerEmployeeId": 28}], "status": "OPEN", "baseDate": "2026-11-10", "employeeId": 28, "totalItems": 5, "completedItems": 0}], "businessPack": "qii_creative", "employeeRules": {"workPatterns": ["LOCATION_DEFAULT", "DAYS_5", "DAYS_6", "HOURLY"], "documentTypes": ["Contract", "NDA", "Agreement", "ID copy", "Certificate", "Other"], "requireExitDate": true, "workEmailDomain": "qiicreative.com.au", "inactiveStatuses": ["Inactive", "Resigned", "Terminated"], "requireExitReason": true, "requiredForActive": ["fullName", "email", "departmentId", "locationId", "startDate", "managerId"], "employmentStatuses": ["Active", "On leave", "Inactive", "Resigned", "Terminated"], "selfEditableFields": ["phone", "personalEmail", "address", "emergencyName", "emergencyRelationship", "emergencyPhone"], "offboardingStatuses": ["Resigned", "Terminated"], "requireManagerOnCreate": true, "workEmailDomainAliases": ["qiicreative.local"], "employmentHistoryFields": ["fullName", "email", "preferredName", "jobTitle", "departmentId", "designationId", "managerId", "teamId", "locationId", "employmentType", "startDate", "probationEnd", "contractEnd", "payCurrency", "workPattern", "baseSalary", "hourlyRate", "status", "phone", "personalEmail", "address", "emergencyName", "emergencyRelationship", "emergencyPhone", "exitDate", "exitReason"], "contractTypesRequiringEnd": ["CONTRACT", "CONTRACTOR", "INTERN"], "requireReportReassignment": true, "requireOffboardingChecklist": true}, "reportingLine": {"maxPastDays": 0, "maxFutureDays": 3650, "requireEffectiveDate": true, "allowPastEffectiveDate": false, "allowFutureEffectiveDate": true}, "employeeCodeMode": "AUTO", "monthlyReportDay": 1, "employeeCodeDigits": 4, "employeeCodePrefix": "QII-", "notificationEvents": {"events": false}, "employeeCodePattern": "^QII-\\\\d{4}$", "teamLeaveMaxOnLeave": 1, "workLogEditDaysBack": 7, "teamLeaveClashAction": "ALERT", "defaultCreateRoleCodes": ["EMPLOYEE"], "notificationsPollSeconds": 30, "payrollAbsenceCoverOrder": ["CASUAL", "ANNUAL"]}	2026-11-09 18:46:45.163559+00
\.


ALTER TABLE public.tenant_settings ENABLE TRIGGER ALL;

--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.users DISABLE TRIGGER ALL;

COPY public.users (id, employee_id, google_sub, email, last_login_at, last_login_ip, last_login_location, last_user_agent, is_active, created_at, updated_at) FROM stdin;
5	19	\N	withmgr_1793358407597@qiicreative.local	\N	\N	\N	\N	t	2026-10-30 11:06:47.671737+00	2026-10-30 11:06:47.671737+00
9	21	\N	withmgr_1793358491642@qiicreative.local	\N	\N	\N	\N	t	2026-10-30 11:08:11.748971+00	2026-10-30 11:08:11.748971+00
10	17	\N	withmgr_1793353742811@qiicreative.local	\N	\N	\N	\N	t	2026-10-30 09:49:02.85163+00	2026-10-30 09:49:02.85163+00
11	20	\N	withmgr_1793358469688@qiicreative.local	\N	\N	\N	\N	t	2026-10-30 11:07:49.74267+00	2026-10-30 11:07:49.74267+00
12	9	\N	sazzad.impit@gmail.com	\N	\N	\N	\N	t	2026-10-30 06:24:07.91854+00	2026-10-31 06:49:15.685225+00
15	12	\N	farhana@qiicreative.local	\N	\N	\N	\N	t	2026-10-30 06:24:07.91854+00	2026-11-09 09:14:09.259346+00
18	11	\N	owen@qiicreative.local	\N	\N	\N	\N	t	2026-10-30 06:24:07.91854+00	2026-11-08 19:10:07.280616+00
19	13	\N	zohurulsazzad@qiicreative.com.au	\N	\N	\N	\N	t	2026-10-30 06:28:03.857691+00	2026-10-30 06:34:04.947631+00
7	6	\N	tania@qiicreative.local	2026-11-10 08:51:33.474038+00	127.0.0.1	\N	node	t	2026-10-30 06:24:07.91854+00	2026-11-10 08:51:33.474038+00
17	5	\N	marcus@qiicreative.local	2026-11-10 10:12:33.906117+00	127.0.0.1	\N	node	t	2026-10-30 06:24:07.91854+00	2026-11-10 10:12:33.906117+00
29	29	\N	tanvir@qiicreative.local	\N	\N	\N	\N	t	2026-11-10 10:49:56.46804+00	2026-11-10 10:49:56.46804+00
30	30	\N	rumana@qiicreative.local	\N	\N	\N	\N	f	2026-11-10 10:49:56.46804+00	2026-11-10 10:49:56.46804+00
26	26	\N	noct.1794249629@qiicreative.com.au	\N	\N	\N	\N	t	2026-11-09 18:40:30.014235+00	2026-11-09 18:40:30.014235+00
27	27	\N	dbg.1794249701@qiicreative.com.au	\N	\N	\N	\N	t	2026-11-09 18:41:42.103878+00	2026-11-09 18:41:42.103878+00
28	28	\N	photo.ok.1794250004@qiicreative.com.au	\N	\N	\N	\N	t	2026-11-09 18:46:45.117446+00	2026-11-09 18:46:45.117446+00
14	18	\N	withmgr_1793358322442@qiicreative.local	2026-11-09 23:37:40.331366+00	127.0.0.1	\N	node	t	2026-10-30 11:05:22.506385+00	2026-11-09 23:37:40.331366+00
13	3	\N	zohurulsazzad@gmail.com	2026-11-10 08:35:12.79119+00	127.0.0.1	\N	node	t	2026-10-30 06:24:07.91854+00	2026-11-10 08:35:12.79119+00
31	31	\N	arpa@qiicreative.local	2026-11-10 11:03:41.703441+00	127.0.0.1	\N	node	t	2026-11-10 10:49:56.46804+00	2026-11-10 11:03:41.703441+00
2	2	\N	vendor.brollex@gmail.com	2026-11-10 11:03:41.752977+00	127.0.0.1	\N	node	t	2026-10-30 06:24:07.91854+00	2026-11-10 11:03:41.752977+00
1	1	\N	sazzad53913@gmail.com	2026-11-10 11:04:01.587+00	127.0.0.1	Dhaka	curl/8.14.1	t	2026-10-30 06:24:07.91854+00	2026-11-10 11:04:01.587+00
24	24	\N	zohuruls@qiicreative.com.au	2026-11-09 19:20:08.631651+00	127.0.0.1	\N	node	t	2026-11-09 18:35:25.045353+00	2026-11-09 19:21:57.019624+00
22	22	\N	zohurulsazza@qiicreative.com.au	2026-11-09 19:10:12.221314+00	127.0.0.1	\N	node	f	2026-11-09 18:32:22.768785+00	2026-11-09 19:23:47.813673+00
23	23	\N	zohurulsaz@qiicreative.com.au	2026-11-09 19:24:10.996542+00	127.0.0.1	\N	node	t	2026-11-09 18:33:55.742545+00	2026-11-09 19:24:10.996542+00
16	8	\N	sophie@qiicreative.local	2026-11-10 08:41:32.882377+00	127.0.0.1	\N	node	t	2026-10-30 06:24:07.91854+00	2026-11-10 08:41:32.882377+00
21	14	\N	jeksdhre@qiicreative.com.au	2026-11-10 08:10:44.259206+00	127.0.0.1	\N	node	t	2026-10-30 09:20:07.548706+00	2026-11-10 08:10:44.259206+00
3	15	\N	nomgr_1793353692090@qiicreative.local	2026-11-10 08:41:48.027468+00	127.0.0.1	\N	node	t	2026-10-30 09:48:12.487365+00	2026-11-10 08:41:48.027468+00
20	4	\N	lina@qiicreative.local	2026-11-10 08:43:52.035069+00	127.0.0.1	\N	node	t	2026-10-30 06:24:07.91854+00	2026-11-10 08:43:52.035069+00
25	25	\N	hourly.test.1794249612@qiicreative.com.au	\N	\N	\N	\N	t	2026-11-09 18:40:12.763698+00	2026-11-09 20:40:02.950018+00
4	16	\N	withmgr_1793353692841@qiicreative.local	2026-11-10 08:44:02.736579+00	127.0.0.1	\N	node	t	2026-10-30 09:48:12.922573+00	2026-11-10 08:44:02.736579+00
6	7	\N	imran@qiicreative.local	\N	\N	\N	\N	t	2026-10-30 06:24:07.91854+00	2026-11-09 22:02:56.093767+00
8	10	\N	priya@qiicreative.local	2026-11-09 22:49:17.906453+00	127.0.0.1	\N	node	t	2026-10-30 06:24:07.91854+00	2026-11-09 22:49:17.906453+00
\.


ALTER TABLE public.users ENABLE TRIGGER ALL;

--
-- Name: app_permission_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.app_permission_id_seq', 69, true);


--
-- Name: app_role_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.app_role_id_seq', 5, true);


--
-- Name: app_role_permission_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.app_role_permission_id_seq', 235, true);


--
-- Name: app_user_role_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.app_user_role_id_seq', 35, true);


--
-- Name: audit_log_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.audit_log_id_seq', 214, true);


--
-- Name: department_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.department_id_seq', 9, true);


--
-- Name: designation_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.designation_id_seq', 9, true);


--
-- Name: employee_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.employee_id_seq', 31, true);


--
-- Name: employment_field_history_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.employment_field_history_id_seq', 61, true);


--
-- Name: location_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.location_id_seq', 3, true);


--
-- Name: reporting_line_history_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.reporting_line_history_id_seq', 39, true);


--
-- Name: team_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.team_id_seq', 17, true);


--
-- Name: tenant_field_definition_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.tenant_field_definition_id_seq', 2, true);


--
-- Name: tenant_settings_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.tenant_settings_id_seq', 1, true);


--
-- Name: users_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.users_id_seq', 31, true);


--
-- PostgreSQL database dump complete
--

\unrestrict lM1VTOUZFhYcV8mkF5dnT8FhAtYPgLYoQxRtV4vkA5AkWd6aQioCx0PMn14R0mo

