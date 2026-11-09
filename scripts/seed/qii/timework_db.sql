--
-- PostgreSQL database dump
--

\restrict WmS9KnUVYw3Yd5EUO6MbpuG6KDDSa7Mt5o4eHjre31v0bGhfyg3Vj5dQ83PXSJl

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
-- Data for Name: attendance_correction; Type: TABLE DATA; Schema: public; Owner: -
--

SET SESSION AUTHORIZATION DEFAULT;

ALTER TABLE public.attendance_correction DISABLE TRIGGER ALL;

COPY public.attendance_correction (id, employee_id, work_date, requested_check_in, requested_check_out, reason, status, reviewer_id, review_note, created_at, updated_at, original_check_in, original_check_out, original_status_code) FROM stdin;
1	12	2026-10-20	2026-10-20 04:00:00+00	2026-10-20 12:00:00+00	forgot punch	APPROVED	1	tyj	2026-10-29 09:23:47.96794+00	2026-10-29 11:57:11.818403+00	\N	\N	\N
3	11	2026-10-20	2026-10-20 03:00:00+00	2026-10-20 12:00:00+00	E2E correction 1793278197932	PENDING	\N	\N	2026-10-29 12:49:59.75966+00	2026-10-29 12:49:59.75966+00	\N	\N	\N
2	9	2026-10-20	2026-10-20 03:00:00+00	2026-10-20 12:00:00+00	E2E correction 1793278094009	APPROVED	1	jhashjr	2026-10-29 12:48:15.556774+00	2026-10-29 17:43:56.658106+00	\N	\N	\N
5	7	2026-10-20	2026-10-20 02:55:00+00	2026-10-20 11:05:00+00	Forgot exact times	APPROVED	4	OK	2026-10-30 10:04:58.542842+00	2026-10-30 10:04:58.62603+00	2026-10-20 03:00:00+00	2026-10-20 11:00:00+00	P
6	7	2026-10-20	2026-10-20 02:55:00+00	2026-10-20 11:05:00+00	Forgot exact times	APPROVED	4	OK	2026-10-30 10:06:02.994425+00	2026-10-30 10:06:03.057231+00	2026-10-20 03:00:00+00	2026-10-20 11:00:00+00	P
4	14	2026-10-30	2026-10-30 04:00:00+00	2026-10-30 12:00:00+00	kjnjhbhjdgbj	APPROVED	1	dsftghyty	2026-10-30 09:39:03.121075+00	2026-10-31 10:37:06.759319+00	\N	\N	\N
7	11	2026-10-31	2026-10-31 04:00:00+00	2026-10-31 12:00:00+00	sffgnhngm	APPROVED	1	fgbhjcffc	2026-10-31 10:39:29.686766+00	2026-10-31 10:40:36.592427+00	2026-10-31 09:24:57.706072+00	2026-10-31 09:25:00.313995+00	HD
8	1	2026-11-03	2026-11-03 04:00:00+00	2026-11-03 12:00:00+00	fgfnhj,klk	APPROVED	3	\N	2026-11-03 13:30:50.866318+00	2026-11-03 13:31:19.06606+00	2026-11-03 06:25:53.491364+00	\N	L
\.


ALTER TABLE public.attendance_correction ENABLE TRIGGER ALL;

--
-- Data for Name: attendance_record; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.attendance_record DISABLE TRIGGER ALL;

COPY public.attendance_record (id, employee_id, work_date, check_in_at, check_out_at, status_code, status_label, ip_address, device, location_label, note, created_at, updated_at, time_zone, latitude, longitude, device_type, employee_code_snapshot) FROM stdin;
51	15	2026-10-30	2026-10-30 10:30:11.337577+00	\N	L	Late	127.0.0.1	node	Location not shared	\N	2026-10-30 10:30:11.337577+00	2026-10-30 10:30:11.337577+00	Asia/Dhaka	\N	\N	Desktop	QII-0033
52	16	2026-10-30	2026-10-30 10:30:44.181992+00	2026-10-30 10:31:19.866796+00	HD	Half Day	127.0.0.1	node	Shared · 23.83449, 90.41593	\N	2026-10-30 10:30:44.181992+00	2026-10-30 10:31:19.866796+00	Asia/Dhaka	23.8344891	90.4159272	Desktop	QII-0034
1	2	2026-10-30	2026-10-30 04:05:00+00	2026-10-30 12:10:00+00	P	Present	103.120.10.20	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0002
2	2	2026-10-29	2026-10-29 04:05:00+00	2026-10-29 12:10:00+00	L	Late	103.120.11.21	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0002
3	2	2026-10-28	2026-10-28 04:05:00+00	2026-10-28 12:10:00+00	P	Present	103.120.12.22	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0002
4	2	2026-10-27	2026-10-27 04:05:00+00	2026-10-27 12:10:00+00	P	Present	103.120.13.23	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0002
5	3	2026-10-30	2026-10-30 04:05:00+00	2026-10-30 12:10:00+00	P	Present	103.120.10.20	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0003
6	3	2026-10-29	2026-10-29 04:05:00+00	2026-10-29 12:10:00+00	L	Late	103.120.11.21	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0003
7	3	2026-10-28	2026-10-28 04:05:00+00	2026-10-28 12:10:00+00	P	Present	103.120.12.22	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0003
8	3	2026-10-27	2026-10-27 04:05:00+00	2026-10-27 12:10:00+00	P	Present	103.120.13.23	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0003
9	4	2026-10-30	2026-10-30 04:05:00+00	2026-10-30 12:10:00+00	P	Present	103.120.10.20	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0010
10	4	2026-10-29	2026-10-29 04:05:00+00	2026-10-29 12:10:00+00	L	Late	103.120.11.21	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0010
11	4	2026-10-28	2026-10-28 04:05:00+00	2026-10-28 12:10:00+00	P	Present	103.120.12.22	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0010
12	4	2026-10-27	2026-10-27 04:05:00+00	2026-10-27 12:10:00+00	P	Present	103.120.13.23	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0010
13	5	2026-10-30	2026-10-30 04:05:00+00	2026-10-30 12:10:00+00	P	Present	103.120.10.20	Mozilla/5.0	Darwin office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Australia/Darwin	-12.4634000	130.8456000	Desktop	QII-0011
14	5	2026-10-29	2026-10-29 04:05:00+00	2026-10-29 12:10:00+00	L	Late	103.120.11.21	Mozilla/5.0	Darwin office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Australia/Darwin	-12.4634000	130.8456000	Desktop	QII-0011
15	5	2026-10-28	2026-10-28 04:05:00+00	2026-10-28 12:10:00+00	P	Present	103.120.12.22	Mozilla/5.0	Darwin office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Australia/Darwin	-12.4634000	130.8456000	Desktop	QII-0011
16	5	2026-10-27	2026-10-27 04:05:00+00	2026-10-27 12:10:00+00	P	Present	103.120.13.23	Mozilla/5.0	Darwin office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Australia/Darwin	-12.4634000	130.8456000	Desktop	QII-0011
17	12	2026-10-30	2026-10-30 04:05:00+00	2026-10-30 12:10:00+00	P	Present	103.120.10.20	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0030
18	12	2026-10-29	2026-10-29 04:05:00+00	2026-10-29 12:10:00+00	L	Late	103.120.11.21	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0030
19	12	2026-10-28	2026-10-28 04:05:00+00	2026-10-28 12:10:00+00	P	Present	103.120.12.22	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0030
20	12	2026-10-27	2026-10-27 04:05:00+00	2026-10-27 12:10:00+00	P	Present	103.120.13.23	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0030
21	6	2026-10-30	2026-10-30 04:05:00+00	2026-10-30 12:10:00+00	P	Present	103.120.10.20	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0020
22	6	2026-10-29	2026-10-29 04:05:00+00	2026-10-29 12:10:00+00	L	Late	103.120.11.21	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0020
23	6	2026-10-28	2026-10-28 04:05:00+00	2026-10-28 12:10:00+00	P	Present	103.120.12.22	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0020
24	6	2026-10-27	2026-10-27 04:05:00+00	2026-10-27 12:10:00+00	P	Present	103.120.13.23	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0020
25	7	2026-10-30	2026-10-30 04:05:00+00	2026-10-30 12:10:00+00	P	Present	103.120.10.20	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0021
26	7	2026-10-29	2026-10-29 04:05:00+00	2026-10-29 12:10:00+00	L	Late	103.120.11.21	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0021
27	7	2026-10-28	2026-10-28 04:05:00+00	2026-10-28 12:10:00+00	P	Present	103.120.12.22	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0021
28	7	2026-10-27	2026-10-27 04:05:00+00	2026-10-27 12:10:00+00	P	Present	103.120.13.23	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0021
29	10	2026-10-30	2026-10-30 04:05:00+00	2026-10-30 12:10:00+00	P	Present	103.120.10.20	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0024
30	10	2026-10-29	2026-10-29 04:05:00+00	2026-10-29 12:10:00+00	L	Late	103.120.11.21	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0024
31	10	2026-10-28	2026-10-28 04:05:00+00	2026-10-28 12:10:00+00	P	Present	103.120.12.22	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0024
32	10	2026-10-27	2026-10-27 04:05:00+00	2026-10-27 12:10:00+00	P	Present	103.120.13.23	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0024
33	8	2026-10-30	2026-10-30 04:05:00+00	2026-10-30 12:10:00+00	P	Present	103.120.10.20	Mozilla/5.0	Darwin office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Australia/Darwin	-12.4634000	130.8456000	Desktop	QII-0022
34	8	2026-10-29	2026-10-29 04:05:00+00	2026-10-29 12:10:00+00	L	Late	103.120.11.21	Mozilla/5.0	Darwin office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Australia/Darwin	-12.4634000	130.8456000	Desktop	QII-0022
35	8	2026-10-28	2026-10-28 04:05:00+00	2026-10-28 12:10:00+00	P	Present	103.120.12.22	Mozilla/5.0	Darwin office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Australia/Darwin	-12.4634000	130.8456000	Desktop	QII-0022
36	8	2026-10-27	2026-10-27 04:05:00+00	2026-10-27 12:10:00+00	P	Present	103.120.13.23	Mozilla/5.0	Darwin office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Australia/Darwin	-12.4634000	130.8456000	Desktop	QII-0022
38	11	2026-10-29	2026-10-29 04:05:00+00	2026-10-29 12:10:00+00	L	Late	103.120.11.21	Mozilla/5.0	Darwin office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Australia/Darwin	-12.4634000	130.8456000	Desktop	QII-0025
46	13	2026-10-30	2026-10-30 06:35:39.518609+00	\N	M	Missing check-out	127.0.0.1	node	Location not shared	\N	2026-10-30 06:35:39.518609+00	2026-10-31 09:27:25.310077+00	Asia/Dhaka	\N	\N	Desktop	QII-0031
48	14	2026-10-30	2026-10-30 04:00:00+00	2026-10-30 12:00:00+00	W	Worked (weekly off)	127.0.0.1	node	Location not shared	Correction #4: kjnjhbhjdgbj	2026-10-30 09:22:15.822464+00	2026-10-31 10:37:06.686253+00	Asia/Dhaka	\N	\N	Desktop	QII-0032
39	11	2026-10-28	2026-10-28 04:05:00+00	2026-10-28 12:10:00+00	P	Present	103.120.12.22	Mozilla/5.0	Darwin office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Australia/Darwin	-12.4634000	130.8456000	Desktop	QII-0025
40	11	2026-10-27	2026-10-27 04:05:00+00	2026-10-27 12:10:00+00	P	Present	103.120.13.23	Mozilla/5.0	Darwin office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Australia/Darwin	-12.4634000	130.8456000	Desktop	QII-0025
41	9	2026-10-30	2026-10-30 04:05:00+00	2026-10-30 12:10:00+00	P	Present	103.120.10.20	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0023
42	9	2026-10-29	2026-10-29 04:05:00+00	2026-10-29 12:10:00+00	L	Late	103.120.11.21	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0023
43	9	2026-10-28	2026-10-28 04:05:00+00	2026-10-28 12:10:00+00	P	Present	103.120.12.22	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0023
44	9	2026-10-27	2026-10-27 04:05:00+00	2026-10-27 12:10:00+00	P	Present	103.120.13.23	Mozilla/5.0	Dhaka office	\N	2026-10-30 06:24:07.91854+00	2026-10-30 06:24:07.91854+00	Asia/Dhaka	23.8103000	90.4125000	Desktop	QII-0023
45	1	2026-10-29	2026-10-29 04:12:00+00	\N	M	Missing check-out	103.120.1.1	Mozilla/5.0	Dhaka office	Still working late	2026-10-30 06:24:07.91854+00	2026-10-30 08:43:11.61716+00	Asia/Dhaka	\N	\N	Desktop	QII-0001
50	11	2026-10-30	2026-10-30 10:05:34.17711+00	2026-10-30 16:05:34.177+00	L	Late	127.0.0.1	iPhone	Location not shared	HR adjustment E2E	2026-10-30 10:05:34.17711+00	2026-10-30 10:05:34.510529+00	Asia/Dhaka	23.8100000	90.4100000	Mobile	QII-0025
49	7	2026-10-20	2026-10-20 02:55:00+00	2026-10-20 11:05:00+00	P	Present	\N	\N	\N	Correction #6: Forgot exact times	2026-10-30 10:04:58.374376+00	2026-10-30 10:06:03.051825+00	\N	\N	\N	\N	\N
47	1	2026-10-30	2026-10-30 08:56:27.431235+00	\N	M	Missing check-out	127.0.0.1	node	Location not shared	\N	2026-10-30 08:56:27.431235+00	2026-10-31 06:30:35.663198+00	Asia/Dhaka	\N	\N	Desktop	QII-0001
53	1	2026-10-31	2026-10-31 06:31:20.953293+00	\N	W	Worked (holiday)	127.0.0.1	node	Location not shared	\N	2026-10-31 06:31:20.953293+00	2026-10-31 06:31:20.953293+00	Asia/Dhaka	\N	\N	Desktop	QII-0001
55	18	2026-10-31	2026-10-31 06:34:39.956982+00	\N	W	Worked (holiday)	127.0.0.1	node	Location not shared	\N	2026-10-31 06:34:39.956982+00	2026-10-31 06:34:39.956982+00	Asia/Dhaka	\N	\N	Desktop	QII-0036
54	14	2026-10-31	2026-10-31 07:32:00+00	2026-10-31 12:00:00+00	W	Worked (holiday)	127.0.0.1	node	Shared · 23.81311, 90.42586	xcfghjhkfdhg	2026-10-31 06:31:30.553394+00	2026-10-31 06:45:03.031543+00	Asia/Dhaka	23.8131080	90.4258580	Desktop	QII-0032
56	12	2026-10-31	2026-10-31 07:55:27.53742+00	\N	W	Worked (holiday)	127.0.0.1	node	Shared · 23.81311, 90.42586	\N	2026-10-31 07:55:27.53742+00	2026-10-31 07:55:27.53742+00	Asia/Dhaka	23.8131080	90.4258580	Desktop	QII-0030
57	8	2026-10-31	2026-10-31 08:02:38.721238+00	\N	W	Worked (holiday)	127.0.0.1	node	Shared · 23.81311, 90.42586	\N	2026-10-31 08:02:38.721238+00	2026-10-31 08:02:38.721238+00	Asia/Dhaka	23.8131080	90.4258580	Desktop	QII-0022
59	13	2026-10-31	2026-10-31 09:55:38.854449+00	\N	W	Worked (holiday)	127.0.0.1	node	Location not shared	\N	2026-10-31 09:55:38.854449+00	2026-10-31 09:55:38.854449+00	Asia/Dhaka	\N	\N	Desktop	QII-0031
60	3	2026-10-31	2026-10-31 09:59:22.746981+00	\N	W	Worked (weekly off)	127.0.0.1	node	Location not shared	\N	2026-10-31 09:59:22.746981+00	2026-10-31 09:59:22.746981+00	Asia/Dhaka	\N	\N	Desktop	QII-0003
58	11	2026-10-31	2026-10-31 04:00:00+00	2026-10-31 12:00:00+00	W	Worked (weekly off)	127.0.0.1	node	Shared · 23.81311, 90.42586	Correction #7: sffgnhngm	2026-10-31 09:24:57.706072+00	2026-10-31 10:40:36.58228+00	Asia/Dhaka	23.8131080	90.4258580	Desktop	QII-0025
63	18	2026-11-03	2026-11-03 08:51:20.641608+00	\N	L	Late	127.0.0.1	node	Shared · 23.81292, 90.42540	\N	2026-11-03 08:51:20.641608+00	2026-11-03 08:51:20.641608+00	Asia/Dhaka	23.8129247	90.4254041	Desktop	QII-0036
64	8	2026-11-03	2026-11-03 09:23:41.47209+00	\N	L	Late	127.0.0.1	node	Location not shared	\N	2026-11-03 09:23:41.47209+00	2026-11-03 09:23:41.47209+00	Asia/Dhaka	\N	\N	Desktop	QII-0022
65	6	2026-11-03	2026-11-03 12:02:57.843757+00	\N	L	Late	127.0.0.1	node	Shared · 23.81268, 90.42568	\N	2026-11-03 12:02:57.843757+00	2026-11-03 12:02:57.843757+00	Asia/Dhaka	23.8126771	90.4256800	Desktop	QII-0020
61	1	2026-11-03	2026-11-03 04:00:00+00	2026-11-03 12:00:00+00	P	Present	127.0.0.1	node	Location not shared	Correction #8: fgfnhj,klk	2026-11-03 06:25:53.491364+00	2026-11-03 13:31:19.053787+00	Asia/Dhaka	\N	\N	Desktop	QII-0001
66	1	2026-11-04	2026-11-04 08:38:24.399932+00	\N	L	Late	127.0.0.1	node	Location not shared	\N	2026-11-04 08:38:24.398933+00	2026-11-04 08:38:24.399932+00	Asia/Dhaka	\N	\N	Desktop	QII-0001
67	3	2026-11-04	2026-11-04 09:59:32.928205+00	\N	L	Late	127.0.0.1	node	Location not shared	\N	2026-11-04 09:59:32.928205+00	2026-11-04 09:59:32.928205+00	Asia/Dhaka	\N	\N	Desktop	QII-0003
62	11	2026-11-03	2026-11-03 08:50:53.038767+00	\N	M	Missing check-out	127.0.0.1	node	Location not shared	\N	2026-11-03 08:50:53.038767+00	2026-11-04 09:59:52.043866+00	Asia/Dhaka	\N	\N	Desktop	QII-0025
\.


ALTER TABLE public.attendance_record ENABLE TRIGGER ALL;

--
-- Data for Name: holiday; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.holiday DISABLE TRIGGER ALL;

COPY public.holiday (id, location_id, name, start_date, end_date, holiday_type, optional_flag) FROM stdin;
1	1	Pohela Boishakh	2026-04-14	2026-04-14	PUBLIC	f
2	1	Victory Day	2026-12-16	2026-12-16	PUBLIC	f
3	2	Australia Day	2026-01-26	2026-01-26	PUBLIC	f
4	2	ANZAC Day	2026-04-25	2026-04-25	PUBLIC	f
6	1	Pohela Boishakh	2031-04-14	2031-04-14	PUBLIC	f
7	1	Victory Day	2031-12-16	2031-12-16	PUBLIC	f
8	1	E2E Victory Day 2026	2031-12-16	2031-12-16	PUBLIC	f
9	1	Pohela Boishakh	2032-04-14	2032-04-14	PUBLIC	f
10	1	Victory Day	2032-12-16	2032-12-16	PUBLIC	f
12	1	Pohela Boishakh	2031-04-14	2031-04-14	PUBLIC	f
13	1	Victory Day	2031-12-16	2031-12-16	PUBLIC	f
14	1	E2E Victory Day 2026	2031-12-16	2031-12-16	PUBLIC	f
17	1	Pohela Boishakh	2027-04-14	2027-04-14	PUBLIC	f
18	1	Victory Day	2027-12-16	2027-12-16	PUBLIC	f
19	2	Australia Day	2027-01-26	2027-01-26	PUBLIC	f
20	2	ANZAC Day	2027-04-25	2027-04-25	PUBLIC	f
21	1	hyffytfyt	2027-10-31	2027-11-07	COMPANY	f
22	1	jkenge	2027-10-31	2027-11-06	PUBLIC	f
\.


ALTER TABLE public.holiday ENABLE TRIGGER ALL;

--
-- Data for Name: timework_audit_log; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.timework_audit_log DISABLE TRIGGER ALL;

COPY public.timework_audit_log (id, at, actor_employee_id, action, entity, entity_id, before_json, after_json, ip) FROM stdin;
3	2026-10-30 06:35:39.586675+00	13	ATTENDANCE_CHECK_IN	attendance_record	46	\N	"L"	\N
4	2026-10-30 06:53:59.4726+00	1	CREATE	WorkSchedule	3	\N	{"id": 3, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4, "checkoutCutoff": "23:59"}	\N
5	2026-10-30 06:55:03.580703+00	1	CREATE	WorkSchedule	4	\N	{"id": 4, "mode": "FIXED", "endTime": "18:00", "scopeId": 2, "scopeType": "EMPLOYEE", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	\N
6	2026-10-30 06:55:03.972992+00	1	UPDATE	WorkSchedule	4	{"id": 4, "mode": "FIXED", "endTime": "18:00", "scopeId": 2, "scopeType": "EMPLOYEE", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	{"id": 4, "mode": "HOURLY", "endTime": null, "scopeId": 2, "scopeType": "EMPLOYEE", "startTime": null, "weeklyHours": 40.0, "workingDays": [], "graceMinutes": 0, "halfDayHours": null, "checkoutCutoff": null}	\N
7	2026-10-30 06:55:04.342391+00	1	DELETE	WorkSchedule	4	{"id": 4, "mode": "HOURLY", "endTime": null, "scopeId": 2, "scopeType": "EMPLOYEE", "startTime": null, "weeklyHours": 40.00, "workingDays": [], "graceMinutes": 0, "halfDayHours": null, "checkoutCutoff": null}	{"reason": "workPattern=LOCATION_DEFAULT", "employeeId": 2}	\N
8	2026-10-30 08:43:11.640185+00	1	ATTENDANCE_MISSING_CHECKOUT	attendance_record	45	{"status": "P"}	{"status": "M", "workDate": "2026-10-29", "checkoutCutoff": "23:59"}	\N
9	2026-10-30 08:54:24.779084+00	3	CREATE	WorkSchedule	5	\N	{"id": 5, "mode": "FIXED", "endTime": "18:00", "scopeId": 4, "scopeType": "EMPLOYEE", "startTime": "14:55", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 0, "halfDayHours": 4, "checkoutCutoff": "23:59"}	\N
10	2026-10-30 08:56:06.696174+00	1	CREATE	WorkSchedule	6	\N	{"id": 6, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "EMPLOYEE", "startTime": "14:57", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU"], "graceMinutes": 0, "halfDayHours": 4, "checkoutCutoff": "23:59"}	\N
11	2026-10-30 08:56:18.823662+00	1	UPDATE	WorkSchedule	6	{"id": 6, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "EMPLOYEE", "startTime": "14:57", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU"], "graceMinutes": 0, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	{"id": 6, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "EMPLOYEE", "startTime": "14:57", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 0, "halfDayHours": 4, "checkoutCutoff": "23:59"}	\N
12	2026-10-30 08:56:27.451328+00	1	ATTENDANCE_CHECK_IN	attendance_record	47	\N	"P"	\N
13	2026-10-30 09:03:22.262303+00	1	DELETE	WorkSchedule	3	{"id": 3, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	\N	\N
14	2026-10-30 09:03:22.401744+00	1	DELETE	WorkSchedule	5	{"id": 5, "mode": "FIXED", "endTime": "18:00", "scopeId": 4, "scopeType": "EMPLOYEE", "startTime": "14:55", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 0, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	\N	\N
15	2026-10-30 09:03:22.642306+00	1	CREATE	WorkSchedule	7	\N	{"id": 7, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4, "checkoutCutoff": "23:59"}	\N
16	2026-10-30 09:03:22.707297+00	1	CREATE	WorkSchedule	8	\N	{"id": 8, "mode": "HOURLY", "endTime": null, "scopeId": 3, "scopeType": "EMPLOYEE", "startTime": null, "weeklyHours": 35, "workingDays": [], "graceMinutes": 15, "halfDayHours": null, "checkoutCutoff": "23:59"}	\N
17	2026-10-30 09:03:22.788196+00	1	UPDATE	WorkSchedule	7	{"id": 7, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	{"id": 7, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 20, "halfDayHours": 3.5, "checkoutCutoff": "23:59"}	\N
18	2026-10-30 09:03:23.559019+00	1	UPDATE	WorkSchedule	8	{"id": 8, "mode": "HOURLY", "endTime": null, "scopeId": 3, "scopeType": "EMPLOYEE", "startTime": null, "weeklyHours": 35.00, "workingDays": [], "graceMinutes": 15, "halfDayHours": null, "checkoutCutoff": "23:59"}	{"id": 8, "mode": "FIXED", "endTime": "18:00", "scopeId": 3, "scopeType": "EMPLOYEE", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	\N
19	2026-10-30 09:03:23.728276+00	1	DELETE	WorkSchedule	8	{"id": 8, "mode": "FIXED", "endTime": "18:00", "scopeId": 3, "scopeType": "EMPLOYEE", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	{"reason": "workPattern=LOCATION_DEFAULT", "employeeId": 3}	\N
20	2026-10-30 09:03:24.271427+00	1	DELETE	WorkSchedule	7	{"id": 7, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 20, "halfDayHours": 3.50, "checkoutCutoff": "23:59"}	\N	\N
21	2026-10-30 09:03:24.411442+00	3	CREATE	WorkSchedule	9	\N	{"id": 9, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["MON"], "graceMinutes": 15, "halfDayHours": 4.0, "checkoutCutoff": "23:59"}	\N
22	2026-10-30 09:04:05.175434+00	1	DELETE	WorkSchedule	6	{"id": 6, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "EMPLOYEE", "startTime": "14:57", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 0, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	\N	\N
23	2026-10-30 09:04:05.229525+00	1	DELETE	WorkSchedule	9	{"id": 9, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["MON"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	\N	\N
24	2026-10-30 09:04:26.653335+00	1	CREATE	WorkSchedule	10	\N	{"id": 10, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4, "checkoutCutoff": "23:59"}	\N
25	2026-10-30 09:04:26.691565+00	1	CREATE	WorkSchedule	11	\N	{"id": 11, "mode": "HOURLY", "endTime": null, "scopeId": 5, "scopeType": "EMPLOYEE", "startTime": null, "weeklyHours": 35, "workingDays": [], "graceMinutes": 15, "halfDayHours": null, "checkoutCutoff": "23:59"}	\N
26	2026-10-30 09:04:26.725031+00	1	UPDATE	WorkSchedule	10	{"id": 10, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	{"id": 10, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 20, "halfDayHours": 3.5, "checkoutCutoff": "23:59"}	\N
27	2026-10-30 09:04:27.232602+00	1	UPDATE	WorkSchedule	11	{"id": 11, "mode": "HOURLY", "endTime": null, "scopeId": 5, "scopeType": "EMPLOYEE", "startTime": null, "weeklyHours": 35.00, "workingDays": [], "graceMinutes": 15, "halfDayHours": null, "checkoutCutoff": "23:59"}	{"id": 11, "mode": "FIXED", "endTime": "18:00", "scopeId": 5, "scopeType": "EMPLOYEE", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	\N
28	2026-10-30 09:04:27.394817+00	1	DELETE	WorkSchedule	11	{"id": 11, "mode": "FIXED", "endTime": "18:00", "scopeId": 5, "scopeType": "EMPLOYEE", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	{"reason": "workPattern=LOCATION_DEFAULT", "employeeId": 5}	\N
29	2026-10-30 09:04:28.102523+00	1	DELETE	WorkSchedule	10	{"id": 10, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 20, "halfDayHours": 3.50, "checkoutCutoff": "23:59"}	\N	\N
30	2026-10-30 09:04:51.585839+00	1	CREATE	WorkSchedule	12	\N	{"id": 12, "mode": "FIXED", "endTime": "18:00", "scopeId": 12, "scopeType": "EMPLOYEE", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	\N
32	2026-10-30 09:04:52.371638+00	1	DELETE	WorkSchedule	12	{"id": 12, "mode": "FIXED", "endTime": "18:00", "scopeId": 12, "scopeType": "EMPLOYEE", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	{"reason": "workPattern=LOCATION_DEFAULT", "employeeId": 12}	\N
33	2026-10-30 09:10:53.931237+00	1	CREATE	WorkSchedule	13	\N	{"id": 13, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4, "checkoutCutoff": "23:59"}	\N
34	2026-10-30 09:10:54.169708+00	1	CREATE	WorkSchedule	14	\N	{"id": 14, "mode": "HOURLY", "endTime": null, "scopeId": 6, "scopeType": "EMPLOYEE", "startTime": null, "weeklyHours": 35, "workingDays": [], "graceMinutes": 15, "halfDayHours": null, "checkoutCutoff": "23:59"}	\N
35	2026-10-30 09:10:54.462974+00	1	UPDATE	WorkSchedule	13	{"id": 13, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	{"id": 13, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 20, "halfDayHours": 3.5, "checkoutCutoff": "23:59"}	\N
36	2026-10-30 09:10:55.681184+00	1	UPDATE	WorkSchedule	14	{"id": 14, "mode": "HOURLY", "endTime": null, "scopeId": 6, "scopeType": "EMPLOYEE", "startTime": null, "weeklyHours": 35.00, "workingDays": [], "graceMinutes": 15, "halfDayHours": null, "checkoutCutoff": "23:59"}	{"id": 14, "mode": "FIXED", "endTime": "18:00", "scopeId": 6, "scopeType": "EMPLOYEE", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	\N
37	2026-10-30 09:10:55.970887+00	1	DELETE	WorkSchedule	14	{"id": 14, "mode": "FIXED", "endTime": "18:00", "scopeId": 6, "scopeType": "EMPLOYEE", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	{"reason": "workPattern=LOCATION_DEFAULT", "employeeId": 6}	\N
38	2026-10-30 09:10:57.053621+00	1	CREATE	WorkSchedule	15	\N	{"id": 15, "mode": "FIXED", "endTime": "18:00", "scopeId": 10, "scopeType": "EMPLOYEE", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	\N
39	2026-10-30 09:10:57.495243+00	1	DELETE	WorkSchedule	15	{"id": 15, "mode": "FIXED", "endTime": "18:00", "scopeId": 10, "scopeType": "EMPLOYEE", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	{"reason": "workPattern=LOCATION_DEFAULT", "employeeId": 10}	\N
40	2026-10-30 09:10:57.7387+00	1	DELETE	WorkSchedule	13	{"id": 13, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 20, "halfDayHours": 3.50, "checkoutCutoff": "23:59"}	\N	\N
41	2026-10-30 09:12:11.282944+00	1	CREATE	WorkSchedule	16	\N	{"id": 16, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4, "checkoutCutoff": "23:59"}	\N
42	2026-10-30 09:12:11.359448+00	1	CREATE	WorkSchedule	17	\N	{"id": 17, "mode": "HOURLY", "endTime": null, "scopeId": 8, "scopeType": "EMPLOYEE", "startTime": null, "weeklyHours": 35, "workingDays": [], "graceMinutes": 15, "halfDayHours": null, "checkoutCutoff": "23:59"}	\N
43	2026-10-30 09:12:11.442385+00	1	UPDATE	WorkSchedule	16	{"id": 16, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	{"id": 16, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 20, "halfDayHours": 3.5, "checkoutCutoff": "23:59"}	\N
44	2026-10-30 09:12:12.48297+00	1	UPDATE	WorkSchedule	17	{"id": 17, "mode": "HOURLY", "endTime": null, "scopeId": 8, "scopeType": "EMPLOYEE", "startTime": null, "weeklyHours": 35.00, "workingDays": [], "graceMinutes": 15, "halfDayHours": null, "checkoutCutoff": "23:59"}	{"id": 17, "mode": "FIXED", "endTime": "18:00", "scopeId": 8, "scopeType": "EMPLOYEE", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	\N
45	2026-10-30 09:12:12.660082+00	1	DELETE	WorkSchedule	17	{"id": 17, "mode": "FIXED", "endTime": "18:00", "scopeId": 8, "scopeType": "EMPLOYEE", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	{"reason": "workPattern=LOCATION_DEFAULT", "employeeId": 8}	\N
46	2026-10-30 09:12:13.445218+00	1	CREATE	WorkSchedule	18	\N	{"id": 18, "mode": "FIXED", "endTime": "18:00", "scopeId": 9, "scopeType": "EMPLOYEE", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	\N
47	2026-10-30 09:12:13.694525+00	1	DELETE	WorkSchedule	18	{"id": 18, "mode": "FIXED", "endTime": "18:00", "scopeId": 9, "scopeType": "EMPLOYEE", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 15, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	{"reason": "workPattern=LOCATION_DEFAULT", "employeeId": 9}	\N
48	2026-10-30 09:12:13.815435+00	1	DELETE	WorkSchedule	16	{"id": 16, "mode": "FIXED", "endTime": "18:00", "scopeId": 1, "scopeType": "TEAM", "startTime": "10:00", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI"], "graceMinutes": 20, "halfDayHours": 3.50, "checkoutCutoff": "23:59"}	\N	\N
49	2026-10-30 09:21:35.712108+00	1	CREATE	WorkSchedule	19	\N	{"id": 19, "mode": "FIXED", "endTime": "23:00", "scopeId": 14, "scopeType": "EMPLOYEE", "startTime": "15:20", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"], "graceMinutes": 1, "halfDayHours": 4, "checkoutCutoff": "23:59"}	\N
50	2026-10-30 09:22:15.961857+00	14	ATTENDANCE_CHECK_IN	attendance_record	48	\N	"L"	\N
51	2026-10-30 09:24:28.212709+00	1	DELETE	WorkSchedule	19	{"id": 19, "mode": "FIXED", "endTime": "23:00", "scopeId": 14, "scopeType": "EMPLOYEE", "startTime": "15:20", "weeklyHours": null, "workingDays": ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"], "graceMinutes": 1, "halfDayHours": 4.00, "checkoutCutoff": "23:59"}	{"reason": "workPattern=LOCATION_DEFAULT", "employeeId": 14}	\N
53	2026-10-30 09:29:27.163489+00	1	CREATE	WorkSchedule	20	\N	{"id": 20, "mode": "HOURLY", "endTime": null, "scopeId": 14, "scopeType": "EMPLOYEE", "startTime": null, "weeklyHours": 40, "workingDays": [], "graceMinutes": 0, "halfDayHours": null, "checkoutCutoff": null}	\N
54	2026-10-30 09:39:03.135937+00	14	ATTENDANCE_CORRECTION_REQUEST	attendance_correction	4	\N	{"id": 4, "reason": "kjnjhbhjdgbj", "status": "PENDING", "workDate": "2026-10-30", "createdAt": "2026-10-30T09:39:03.121074800Z", "updatedAt": "2026-10-30T09:39:03.121074800Z", "employeeId": 14, "reviewNote": null, "reviewerId": null, "employeeName": "Anindya Basak", "requestedCheckIn": "2026-10-30T04:00:00Z", "requestedCheckOut": "2026-10-30T12:00:00Z"}	\N
101	2026-10-30 12:12:08.672042+00	1	CREATE	Holiday	6	\N	{"name": "Pohela Boishakh", "toYear": 2031, "startDate": "2031-04-14", "copiedFromYear": 2026}	\N
102	2026-10-30 12:12:08.734561+00	1	CREATE	Holiday	7	\N	{"name": "Victory Day", "toYear": 2031, "startDate": "2031-12-16", "copiedFromYear": 2026}	\N
103	2026-10-30 12:12:08.773878+00	1	CREATE	Holiday	8	\N	{"name": "E2E Victory Day 2026", "toYear": 2031, "startDate": "2031-12-16", "copiedFromYear": 2026}	\N
57	2026-10-30 10:04:58.461638+00	2	ATTENDANCE_CORRECT	attendance_record	49	{"id": "null", "ip": null, "date": "2026-10-20", "note": null, "device": null, "status": null, "checkIn": null, "checkOut": null, "latitude": null, "location": null, "timeZone": "Asia/Dhaka", "workDate": "2026-10-20", "hoursLive": false, "ipAddress": null, "longitude": null, "deviceType": null, "employeeId": "7", "statusCode": null, "totalHours": 0.0, "employeeCode": "QII-0021", "workingHours": "0h 0m", "locationLabel": null, "workedMinutes": 0}	{"id": "49", "ip": null, "date": "2026-10-20", "note": "E2E seed day for correction", "device": null, "status": "Present", "checkIn": "2026-10-20T03:00:00Z", "checkOut": "2026-10-20T11:00:00Z", "latitude": null, "location": null, "timeZone": "Asia/Dhaka", "workDate": "2026-10-20", "hoursLive": false, "ipAddress": null, "longitude": null, "deviceType": null, "employeeId": "7", "statusCode": "P", "totalHours": 8.0, "checkInLocal": "09:00", "employeeCode": "QII-0021", "workingHours": "8h 0m", "checkOutLocal": "17:00", "locationLabel": null, "workedMinutes": 480}	\N
58	2026-10-30 10:04:58.555643+00	7	ATTENDANCE_CORRECTION_REQUEST	attendance_correction	5	\N	{"id": 5, "reason": "Forgot exact times", "status": "PENDING", "workDate": "2026-10-20", "createdAt": "2026-10-30T10:04:58.542842Z", "updatedAt": "2026-10-30T10:04:58.542842Z", "employeeId": 7, "reviewNote": null, "reviewerId": null, "employeeName": "Imran Hossain", "originalCheckIn": "2026-10-20T03:00:00Z", "originalCheckOut": "2026-10-20T11:00:00Z", "requestedCheckIn": "2026-10-20T02:55:00Z", "requestedCheckOut": "2026-10-20T11:05:00Z", "originalStatusCode": "P"}	\N
59	2026-10-30 10:04:58.619448+00	4	ATTENDANCE_CORRECT	attendance_record	49	{"id": "49", "ip": null, "date": "2026-10-20", "note": "E2E seed day for correction", "device": null, "status": "Present", "checkIn": "2026-10-20T03:00:00Z", "checkOut": "2026-10-20T11:00:00Z", "latitude": null, "location": null, "timeZone": "Asia/Dhaka", "workDate": "2026-10-20", "hoursLive": false, "ipAddress": null, "longitude": null, "deviceType": null, "employeeId": "7", "statusCode": "P", "totalHours": 8.0, "checkInLocal": "09:00", "employeeCode": "QII-0021", "workingHours": "8h 0m", "checkOutLocal": "17:00", "locationLabel": null, "workedMinutes": 480}	{"id": "49", "ip": null, "date": "2026-10-20", "note": "Correction #5: Forgot exact times", "device": null, "status": "Present", "checkIn": "2026-10-20T02:55:00Z", "checkOut": "2026-10-20T11:05:00Z", "latitude": null, "location": null, "timeZone": "Asia/Dhaka", "workDate": "2026-10-20", "hoursLive": false, "ipAddress": null, "longitude": null, "deviceType": null, "employeeId": "7", "statusCode": "P", "totalHours": 8.17, "checkInLocal": "08:55", "employeeCode": "QII-0021", "workingHours": "8h 10m", "checkOutLocal": "17:05", "locationLabel": null, "workedMinutes": 490}	\N
60	2026-10-30 10:04:58.6267+00	4	ATTENDANCE_CORRECTION_APPROVE	attendance_correction	5	{"id": 5, "reason": "Forgot exact times", "status": "PENDING", "workDate": "2026-10-20", "createdAt": "2026-10-30T10:04:58.542842Z", "updatedAt": "2026-10-30T10:04:58.542842Z", "employeeId": 7, "reviewNote": null, "reviewerId": null, "employeeName": "Imran Hossain", "originalCheckIn": "2026-10-20T03:00:00Z", "originalCheckOut": "2026-10-20T11:00:00Z", "requestedCheckIn": "2026-10-20T02:55:00Z", "requestedCheckOut": "2026-10-20T11:05:00Z", "originalStatusCode": "P"}	{"id": 5, "reason": "Forgot exact times", "status": "APPROVED", "workDate": "2026-10-20", "createdAt": "2026-10-30T10:04:58.542842Z", "updatedAt": "2026-10-30T10:04:58.626029700Z", "employeeId": 7, "reviewNote": "OK", "reviewerId": 4, "employeeName": "Imran Hossain", "originalCheckIn": "2026-10-20T03:00:00Z", "originalCheckOut": "2026-10-20T11:00:00Z", "requestedCheckIn": "2026-10-20T02:55:00Z", "requestedCheckOut": "2026-10-20T11:05:00Z", "originalStatusCode": "P"}	\N
61	2026-10-30 10:05:34.194358+00	11	ATTENDANCE_CHECK_IN	attendance_record	50	\N	"L"	\N
62	2026-10-30 10:05:34.324145+00	11	ATTENDANCE_CHECK_OUT	attendance_record	50	\N	{"status": "HD", "workDate": "2026-10-30", "lateNightYesterday": false}	\N
63	2026-10-30 10:05:34.511056+00	2	ATTENDANCE_CORRECT	attendance_record	50	{"id": "50", "ip": "127.0.0.1", "date": "2026-10-30", "note": "done", "device": "iPhone", "status": "Half Day", "checkIn": "2026-10-30T10:05:34.177110Z", "checkOut": "2026-10-30T10:05:34.315115Z", "latitude": 23.8100000, "location": "Location not shared", "timeZone": "Asia/Dhaka", "workDate": "2026-10-30", "hoursLive": false, "ipAddress": "127.0.0.1", "longitude": 90.4100000, "deviceType": "Mobile", "employeeId": "11", "statusCode": "HD", "totalHours": 0.0, "checkInLocal": "16:05:34.177110", "employeeCode": "QII-0025", "workingHours": "0h 0m", "checkOutLocal": "16:05:34.315115", "locationLabel": "Location not shared", "workedMinutes": 0}	{"id": "50", "ip": "127.0.0.1", "date": "2026-10-30", "note": "HR adjustment E2E", "device": "iPhone", "status": "Late", "checkIn": "2026-10-30T10:05:34.177110300Z", "checkOut": "2026-10-30T16:05:34.177Z", "latitude": 23.8100000, "location": "Location not shared", "timeZone": "Asia/Dhaka", "workDate": "2026-10-30", "hoursLive": false, "ipAddress": "127.0.0.1", "longitude": 90.4100000, "deviceType": "Mobile", "employeeId": "11", "statusCode": "L", "totalHours": 5.98, "checkInLocal": "16:05:34.177110300", "employeeCode": "QII-0025", "workingHours": "5h 59m", "checkOutLocal": "22:05:34.177", "locationLabel": "Location not shared", "workedMinutes": 359}	\N
64	2026-10-30 10:06:02.959022+00	2	ATTENDANCE_CORRECT	attendance_record	49	{"id": "49", "ip": null, "date": "2026-10-20", "note": "Correction #5: Forgot exact times", "device": null, "status": "Present", "checkIn": "2026-10-20T02:55:00Z", "checkOut": "2026-10-20T11:05:00Z", "latitude": null, "location": null, "timeZone": "Asia/Dhaka", "workDate": "2026-10-20", "hoursLive": false, "ipAddress": null, "longitude": null, "deviceType": null, "employeeId": "7", "statusCode": "P", "totalHours": 8.17, "checkInLocal": "08:55", "employeeCode": "QII-0021", "workingHours": "8h 10m", "checkOutLocal": "17:05", "locationLabel": null, "workedMinutes": 490}	{"id": "49", "ip": null, "date": "2026-10-20", "note": "E2E seed day for correction", "device": null, "status": "Present", "checkIn": "2026-10-20T03:00:00Z", "checkOut": "2026-10-20T11:00:00Z", "latitude": null, "location": null, "timeZone": "Asia/Dhaka", "workDate": "2026-10-20", "hoursLive": false, "ipAddress": null, "longitude": null, "deviceType": null, "employeeId": "7", "statusCode": "P", "totalHours": 8.0, "checkInLocal": "09:00", "employeeCode": "QII-0021", "workingHours": "8h 0m", "checkOutLocal": "17:00", "locationLabel": null, "workedMinutes": 480}	\N
65	2026-10-30 10:06:02.997889+00	7	ATTENDANCE_CORRECTION_REQUEST	attendance_correction	6	\N	{"id": 6, "reason": "Forgot exact times", "status": "PENDING", "workDate": "2026-10-20", "createdAt": "2026-10-30T10:06:02.994424900Z", "updatedAt": "2026-10-30T10:06:02.994424900Z", "employeeId": 7, "reviewNote": null, "reviewerId": null, "employeeName": "Imran Hossain", "originalCheckIn": "2026-10-20T03:00:00Z", "originalCheckOut": "2026-10-20T11:00:00Z", "requestedCheckIn": "2026-10-20T02:55:00Z", "requestedCheckOut": "2026-10-20T11:05:00Z", "originalStatusCode": "P"}	\N
66	2026-10-30 10:06:03.052389+00	4	ATTENDANCE_CORRECT	attendance_record	49	{"id": "49", "ip": null, "date": "2026-10-20", "note": "E2E seed day for correction", "device": null, "status": "Present", "checkIn": "2026-10-20T03:00:00Z", "checkOut": "2026-10-20T11:00:00Z", "latitude": null, "location": null, "timeZone": "Asia/Dhaka", "workDate": "2026-10-20", "hoursLive": false, "ipAddress": null, "longitude": null, "deviceType": null, "employeeId": "7", "statusCode": "P", "totalHours": 8.0, "checkInLocal": "09:00", "employeeCode": "QII-0021", "workingHours": "8h 0m", "checkOutLocal": "17:00", "locationLabel": null, "workedMinutes": 480}	{"id": "49", "ip": null, "date": "2026-10-20", "note": "Correction #6: Forgot exact times", "device": null, "status": "Present", "checkIn": "2026-10-20T02:55:00Z", "checkOut": "2026-10-20T11:05:00Z", "latitude": null, "location": null, "timeZone": "Asia/Dhaka", "workDate": "2026-10-20", "hoursLive": false, "ipAddress": null, "longitude": null, "deviceType": null, "employeeId": "7", "statusCode": "P", "totalHours": 8.17, "checkInLocal": "08:55", "employeeCode": "QII-0021", "workingHours": "8h 10m", "checkOutLocal": "17:05", "locationLabel": null, "workedMinutes": 490}	\N
67	2026-10-30 10:06:03.057803+00	4	ATTENDANCE_CORRECTION_APPROVE	attendance_correction	6	{"id": 6, "reason": "Forgot exact times", "status": "PENDING", "workDate": "2026-10-20", "createdAt": "2026-10-30T10:06:02.994425Z", "updatedAt": "2026-10-30T10:06:02.994425Z", "employeeId": 7, "reviewNote": null, "reviewerId": null, "employeeName": "Imran Hossain", "originalCheckIn": "2026-10-20T03:00:00Z", "originalCheckOut": "2026-10-20T11:00:00Z", "requestedCheckIn": "2026-10-20T02:55:00Z", "requestedCheckOut": "2026-10-20T11:05:00Z", "originalStatusCode": "P"}	{"id": 6, "reason": "Forgot exact times", "status": "APPROVED", "workDate": "2026-10-20", "createdAt": "2026-10-30T10:06:02.994425Z", "updatedAt": "2026-10-30T10:06:03.057231Z", "employeeId": 7, "reviewNote": "OK", "reviewerId": 4, "employeeName": "Imran Hossain", "originalCheckIn": "2026-10-20T03:00:00Z", "originalCheckOut": "2026-10-20T11:00:00Z", "requestedCheckIn": "2026-10-20T02:55:00Z", "requestedCheckOut": "2026-10-20T11:05:00Z", "originalStatusCode": "P"}	\N
68	2026-10-30 10:30:11.366801+00	15	ATTENDANCE_CHECK_IN	attendance_record	51	\N	"L"	\N
69	2026-10-30 10:30:44.204936+00	16	ATTENDANCE_CHECK_IN	attendance_record	52	\N	"L"	\N
70	2026-10-30 10:31:19.880918+00	16	ATTENDANCE_CHECK_OUT	attendance_record	52	\N	{"status": "HD", "workDate": "2026-10-30", "lateNightYesterday": false}	\N
110	2026-10-30 12:12:17.109537+00	1	EXPORT	LateAbsence	\N	\N	{"to": "2026-10-30", "from": "2026-10-01", "rows": 15}	\N
111	2026-10-30 12:12:26.331368+00	1	CREATE	Holiday	9	\N	{"name": "Pohela Boishakh", "toYear": 2032, "startDate": "2032-04-14", "copiedFromYear": 2026}	\N
112	2026-10-30 12:12:26.35394+00	1	CREATE	Holiday	10	\N	{"name": "Victory Day", "toYear": 2032, "startDate": "2032-12-16", "copiedFromYear": 2026}	\N
113	2026-10-30 12:12:33.419453+00	1	CREATE	Holiday	12	\N	{"name": "Pohela Boishakh", "toYear": 2031, "startDate": "2031-04-14", "copiedFromYear": 2026}	\N
114	2026-10-30 12:12:33.434968+00	1	CREATE	Holiday	13	\N	{"name": "Victory Day", "toYear": 2031, "startDate": "2031-12-16", "copiedFromYear": 2026}	\N
115	2026-10-30 12:12:33.462533+00	1	CREATE	Holiday	14	\N	{"name": "E2E Victory Day 2026", "toYear": 2031, "startDate": "2031-12-16", "copiedFromYear": 2026}	\N
122	2026-10-30 12:12:35.231394+00	1	EXPORT	LateAbsence	\N	\N	{"to": "2026-10-30", "from": "2026-10-01", "rows": 15}	\N
127	2026-10-31 06:30:35.67192+00	1	ATTENDANCE_MISSING_CHECKOUT	attendance_record	47	{"status": "P"}	{"status": "M", "workDate": "2026-10-30", "checkoutCutoff": "23:59"}	\N
128	2026-10-31 06:31:20.962348+00	1	ATTENDANCE_CHECK_IN	attendance_record	53	\N	"W"	\N
129	2026-10-31 06:31:30.575016+00	14	ATTENDANCE_CHECK_IN	attendance_record	54	\N	"W"	\N
130	2026-10-31 06:34:39.965578+00	18	ATTENDANCE_CHECK_IN	attendance_record	55	\N	"W"	\N
131	2026-10-31 06:45:03.033056+00	1	ATTENDANCE_CORRECT	attendance_record	54	{"id": "54", "ip": "127.0.0.1", "date": "2026-10-31", "note": null, "device": "node", "status": "Worked (holiday)", "checkIn": "2026-10-31T06:31:30.553394Z", "checkOut": null, "latitude": 23.8131080, "location": "Shared · 23.81311, 90.42586", "timeZone": "Asia/Dhaka", "workDate": "2026-10-31", "hoursLive": true, "ipAddress": "127.0.0.1", "longitude": 90.4258580, "deviceType": "Desktop", "employeeId": "14", "statusCode": "W", "totalHours": 0.22, "checkInLocal": "12:31:30.553394", "employeeCode": "QII-0032", "workingHours": "0h 13m", "locationLabel": "Shared · 23.81311, 90.42586", "workedMinutes": 13}	{"id": "54", "ip": "127.0.0.1", "date": "2026-10-31", "note": "xcfghjhkfdhg", "device": "node", "status": "Worked (holiday)", "checkIn": "2026-10-31T07:32:00Z", "checkOut": "2026-10-31T12:00:00Z", "latitude": 23.8131080, "location": "Shared · 23.81311, 90.42586", "timeZone": "Asia/Dhaka", "workDate": "2026-10-31", "hoursLive": false, "ipAddress": "127.0.0.1", "longitude": 90.4258580, "deviceType": "Desktop", "employeeId": "14", "statusCode": "W", "totalHours": 4.47, "checkInLocal": "13:32", "employeeCode": "QII-0032", "workingHours": "4h 28m", "checkOutLocal": "18:00", "locationLabel": "Shared · 23.81311, 90.42586", "workedMinutes": 268}	\N
132	2026-10-31 06:50:05.758753+00	1	CREATE	Holiday	17	\N	{"name": "Pohela Boishakh", "toYear": 2027, "startDate": "2027-04-14", "copiedFromYear": 2026}	\N
133	2026-10-31 06:50:05.785319+00	1	CREATE	Holiday	18	\N	{"name": "Victory Day", "toYear": 2027, "startDate": "2027-12-16", "copiedFromYear": 2026}	\N
134	2026-10-31 06:50:05.826132+00	1	CREATE	Holiday	19	\N	{"name": "Australia Day", "toYear": 2027, "startDate": "2027-01-26", "copiedFromYear": 2026}	\N
135	2026-10-31 06:50:05.854608+00	1	CREATE	Holiday	20	\N	{"name": "ANZAC Day", "toYear": 2027, "startDate": "2027-04-25", "copiedFromYear": 2026}	\N
136	2026-10-31 06:50:05.889725+00	1	CREATE	Holiday	21	\N	{"name": "hyffytfyt", "toYear": 2027, "startDate": "2027-10-31", "copiedFromYear": 2026}	\N
137	2026-10-31 06:50:05.927692+00	1	CREATE	Holiday	22	\N	{"name": "jkenge", "toYear": 2027, "startDate": "2027-10-31", "copiedFromYear": 2026}	\N
140	2026-10-31 07:55:27.570003+00	12	ATTENDANCE_CHECK_IN	attendance_record	56	\N	"W"	\N
141	2026-10-31 08:02:38.753374+00	8	ATTENDANCE_CHECK_IN	attendance_record	57	\N	"W"	\N
142	2026-10-31 09:24:57.741216+00	11	ATTENDANCE_CHECK_IN	attendance_record	58	\N	"W"	\N
143	2026-10-31 09:25:00.355161+00	11	ATTENDANCE_CHECK_OUT	attendance_record	58	\N	{"status": "HD", "workDate": "2026-10-31", "lateNightYesterday": false}	\N
144	2026-10-31 09:27:25.312518+00	13	ATTENDANCE_MISSING_CHECKOUT	attendance_record	46	{"status": "L"}	{"status": "M", "workDate": "2026-10-30", "checkoutCutoff": "23:59"}	\N
145	2026-10-31 09:55:38.881947+00	13	ATTENDANCE_CHECK_IN	attendance_record	59	\N	"W"	\N
146	2026-10-31 09:59:22.780798+00	3	ATTENDANCE_CHECK_IN	attendance_record	60	\N	"W"	\N
147	2026-10-31 10:37:06.695689+00	1	ATTENDANCE_CORRECT	attendance_record	48	{"id": "48", "ip": "127.0.0.1", "date": "2026-10-30", "note": null, "device": "node", "status": "Late", "checkIn": "2026-10-30T09:22:15.822464Z", "checkOut": null, "latitude": null, "location": "Location not shared", "timeZone": "Asia/Dhaka", "workDate": "2026-10-30", "hoursLive": true, "ipAddress": "127.0.0.1", "longitude": null, "deviceType": "Desktop", "employeeId": "14", "statusCode": "L", "totalHours": 0.0, "checkInLocal": "15:22:15.822464", "employeeCode": "QII-0032", "workingHours": "0h 0m", "locationLabel": "Location not shared", "workedMinutes": 0}	{"id": "48", "ip": "127.0.0.1", "date": "2026-10-30", "note": "Correction #4: kjnjhbhjdgbj", "device": "node", "status": "Worked (weekly off)", "checkIn": "2026-10-30T04:00:00Z", "checkOut": "2026-10-30T12:00:00Z", "latitude": null, "location": "Location not shared", "timeZone": "Asia/Dhaka", "workDate": "2026-10-30", "hoursLive": false, "ipAddress": "127.0.0.1", "longitude": null, "deviceType": "Desktop", "employeeId": "14", "statusCode": "W", "totalHours": 8.0, "checkInLocal": "10:00", "employeeCode": "QII-0032", "workingHours": "8h 0m", "checkOutLocal": "18:00", "locationLabel": "Location not shared", "workedMinutes": 480}	\N
148	2026-10-31 10:37:06.767601+00	1	ATTENDANCE_CORRECTION_APPROVE	attendance_correction	4	{"id": 4, "reason": "kjnjhbhjdgbj", "status": "PENDING", "workDate": "2026-10-30", "createdAt": "2026-10-30T09:39:03.121075Z", "updatedAt": "2026-10-30T09:39:03.121075Z", "employeeId": 14, "reviewNote": null, "reviewerId": null, "employeeName": "Anindya Basak", "originalCheckIn": null, "originalCheckOut": null, "requestedCheckIn": "2026-10-30T04:00:00Z", "requestedCheckOut": "2026-10-30T12:00:00Z", "originalStatusCode": null}	{"id": 4, "reason": "kjnjhbhjdgbj", "status": "APPROVED", "workDate": "2026-10-30", "createdAt": "2026-10-30T09:39:03.121075Z", "updatedAt": "2026-10-31T10:37:06.759318500Z", "employeeId": 14, "reviewNote": "dsftghyty", "reviewerId": 1, "employeeName": "Anindya Basak", "originalCheckIn": null, "originalCheckOut": null, "requestedCheckIn": "2026-10-30T04:00:00Z", "requestedCheckOut": "2026-10-30T12:00:00Z", "originalStatusCode": null}	\N
149	2026-10-31 10:39:29.711107+00	11	ATTENDANCE_CORRECTION_REQUEST	attendance_correction	7	\N	{"id": 7, "reason": "sffgnhngm", "status": "PENDING", "workDate": "2026-10-31", "createdAt": "2026-10-31T10:39:29.686765600Z", "updatedAt": "2026-10-31T10:39:29.686765600Z", "employeeId": 11, "reviewNote": null, "reviewerId": null, "employeeName": "Owen Fraser", "originalCheckIn": "2026-10-31T09:24:57.706072Z", "originalCheckOut": "2026-10-31T09:25:00.313995Z", "requestedCheckIn": "2026-10-31T04:00:00Z", "requestedCheckOut": "2026-10-31T12:00:00Z", "originalStatusCode": "HD"}	\N
150	2026-10-31 10:40:36.583968+00	1	ATTENDANCE_CORRECT	attendance_record	58	{"id": "58", "ip": "127.0.0.1", "date": "2026-10-31", "note": null, "device": "node", "status": "Half Day", "checkIn": "2026-10-31T09:24:57.706072Z", "checkOut": "2026-10-31T09:25:00.313995Z", "latitude": 23.8131080, "location": "Shared · 23.81311, 90.42586", "timeZone": "Asia/Dhaka", "workDate": "2026-10-31", "hoursLive": false, "ipAddress": "127.0.0.1", "longitude": 90.4258580, "deviceType": "Desktop", "employeeId": "11", "statusCode": "HD", "totalHours": 0.0, "checkInLocal": "15:24:57.706072", "employeeCode": "QII-0025", "workingHours": "0h 0m", "checkOutLocal": "15:25:00.313995", "locationLabel": "Shared · 23.81311, 90.42586", "workedMinutes": 0}	{"id": "58", "ip": "127.0.0.1", "date": "2026-10-31", "note": "Correction #7: sffgnhngm", "device": "node", "status": "Worked (weekly off)", "checkIn": "2026-10-31T04:00:00Z", "checkOut": "2026-10-31T12:00:00Z", "latitude": 23.8131080, "location": "Shared · 23.81311, 90.42586", "timeZone": "Asia/Dhaka", "workDate": "2026-10-31", "hoursLive": false, "ipAddress": "127.0.0.1", "longitude": 90.4258580, "deviceType": "Desktop", "employeeId": "11", "statusCode": "W", "totalHours": 8.0, "checkInLocal": "10:00", "employeeCode": "QII-0025", "workingHours": "8h 0m", "checkOutLocal": "18:00", "locationLabel": "Shared · 23.81311, 90.42586", "workedMinutes": 480}	\N
151	2026-10-31 10:40:36.592952+00	1	ATTENDANCE_CORRECTION_APPROVE	attendance_correction	7	{"id": 7, "reason": "sffgnhngm", "status": "PENDING", "workDate": "2026-10-31", "createdAt": "2026-10-31T10:39:29.686766Z", "updatedAt": "2026-10-31T10:39:29.686766Z", "employeeId": 11, "reviewNote": null, "reviewerId": null, "employeeName": "Owen Fraser", "originalCheckIn": "2026-10-31T09:24:57.706072Z", "originalCheckOut": "2026-10-31T09:25:00.313995Z", "requestedCheckIn": "2026-10-31T04:00:00Z", "requestedCheckOut": "2026-10-31T12:00:00Z", "originalStatusCode": "HD"}	{"id": 7, "reason": "sffgnhngm", "status": "APPROVED", "workDate": "2026-10-31", "createdAt": "2026-10-31T10:39:29.686766Z", "updatedAt": "2026-10-31T10:40:36.592427100Z", "employeeId": 11, "reviewNote": "fgbhjcffc", "reviewerId": 1, "employeeName": "Owen Fraser", "originalCheckIn": "2026-10-31T09:24:57.706072Z", "originalCheckOut": "2026-10-31T09:25:00.313995Z", "requestedCheckIn": "2026-10-31T04:00:00Z", "requestedCheckOut": "2026-10-31T12:00:00Z", "originalStatusCode": "HD"}	\N
152	2026-11-03 06:25:53.743574+00	1	ATTENDANCE_CHECK_IN	attendance_record	61	\N	"L"	\N
154	2026-11-03 08:50:53.152806+00	11	ATTENDANCE_CHECK_IN	attendance_record	62	\N	"L"	\N
155	2026-11-03 08:51:20.690988+00	18	ATTENDANCE_CHECK_IN	attendance_record	63	\N	"L"	\N
156	2026-11-03 09:23:41.485794+00	8	ATTENDANCE_CHECK_IN	attendance_record	64	\N	"L"	\N
187	2026-11-03 12:02:57.898946+00	6	ATTENDANCE_CHECK_IN	attendance_record	65	\N	"L"	\N
188	2026-11-03 13:30:50.953801+00	1	ATTENDANCE_CORRECTION_REQUEST	attendance_correction	8	\N	{"id": 8, "reason": "fgfnhj,klk", "status": "PENDING", "workDate": "2026-11-03", "createdAt": "2026-11-03T13:30:50.866318400Z", "updatedAt": "2026-11-03T13:30:50.866318400Z", "employeeId": 1, "reviewNote": null, "reviewerId": null, "employeeName": "Asman Rahman", "originalCheckIn": "2026-11-03T06:25:53.491364Z", "originalCheckOut": null, "requestedCheckIn": "2026-11-03T04:00:00Z", "requestedCheckOut": "2026-11-03T12:00:00Z", "originalStatusCode": "L"}	\N
189	2026-11-03 13:31:19.059719+00	3	ATTENDANCE_CORRECT	attendance_record	61	{"id": "61", "ip": "127.0.0.1", "date": "2026-11-03", "note": null, "device": "node", "status": "Late", "checkIn": "2026-11-03T06:25:53.491364Z", "checkOut": null, "latitude": null, "location": "Location not shared", "timeZone": "Asia/Dhaka", "workDate": "2026-11-03", "hoursLive": true, "ipAddress": "127.0.0.1", "longitude": null, "deviceType": "Desktop", "employeeId": "1", "statusCode": "L", "totalHours": 7.08, "checkInLocal": "12:25:53.491364", "employeeCode": "QII-0001", "workingHours": "7h 5m", "locationLabel": "Location not shared", "workedMinutes": 425}	{"id": "61", "ip": "127.0.0.1", "date": "2026-11-03", "note": "Correction #8: fgfnhj,klk", "device": "node", "status": "Present", "checkIn": "2026-11-03T04:00:00Z", "checkOut": "2026-11-03T12:00:00Z", "latitude": null, "location": "Location not shared", "timeZone": "Asia/Dhaka", "workDate": "2026-11-03", "hoursLive": false, "ipAddress": "127.0.0.1", "longitude": null, "deviceType": "Desktop", "employeeId": "1", "statusCode": "P", "totalHours": 8.0, "checkInLocal": "10:00", "employeeCode": "QII-0001", "workingHours": "8h 0m", "checkOutLocal": "18:00", "locationLabel": "Location not shared", "workedMinutes": 480}	\N
190	2026-11-03 13:31:19.06606+00	3	ATTENDANCE_CORRECTION_APPROVE	attendance_correction	8	{"id": 8, "reason": "fgfnhj,klk", "status": "PENDING", "workDate": "2026-11-03", "createdAt": "2026-11-03T13:30:50.866318Z", "updatedAt": "2026-11-03T13:30:50.866318Z", "employeeId": 1, "reviewNote": null, "reviewerId": null, "employeeName": "Asman Rahman", "originalCheckIn": "2026-11-03T06:25:53.491364Z", "originalCheckOut": null, "requestedCheckIn": "2026-11-03T04:00:00Z", "requestedCheckOut": "2026-11-03T12:00:00Z", "originalStatusCode": "L"}	{"id": 8, "reason": "fgfnhj,klk", "status": "APPROVED", "workDate": "2026-11-03", "createdAt": "2026-11-03T13:30:50.866318Z", "updatedAt": "2026-11-03T13:31:19.066060100Z", "employeeId": 1, "reviewNote": null, "reviewerId": 3, "employeeName": "Asman Rahman", "originalCheckIn": "2026-11-03T06:25:53.491364Z", "originalCheckOut": null, "requestedCheckIn": "2026-11-03T04:00:00Z", "requestedCheckOut": "2026-11-03T12:00:00Z", "originalStatusCode": "L"}	\N
191	2026-11-04 08:38:24.704976+00	1	ATTENDANCE_CHECK_IN	attendance_record	66	\N	"L"	\N
192	2026-11-04 09:59:32.975127+00	3	ATTENDANCE_CHECK_IN	attendance_record	67	\N	"L"	\N
193	2026-11-04 09:59:52.044401+00	11	ATTENDANCE_MISSING_CHECKOUT	attendance_record	62	{"status": "L"}	{"status": "M", "workDate": "2026-11-03", "checkoutCutoff": "23:59"}	\N
\.


ALTER TABLE public.timework_audit_log ENABLE TRIGGER ALL;

--
-- Data for Name: timework_settings; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.timework_settings DISABLE TRIGGER ALL;

COPY public.timework_settings (id, settings, updated_at) FROM stdin;
1	{"businessPack": "qii_creative"}	2026-11-09 02:49:05.088135+00
\.


ALTER TABLE public.timework_settings ENABLE TRIGGER ALL;

--
-- Data for Name: work_log; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.work_log DISABLE TRIGGER ALL;

COPY public.work_log (id, employee_id, work_date, task_id, minutes, note, created_at, flagged, flag_reason) FROM stdin;
1	6	2026-10-30	1	120	Seeded work log	2026-10-30 06:24:07.91854+00	f	\N
2	6	2026-10-29	1	135	Seeded work log	2026-10-30 06:24:07.91854+00	f	\N
3	6	2026-10-28	1	150	Seeded work log	2026-10-30 06:24:07.91854+00	f	\N
4	7	2026-10-30	1	120	Seeded work log	2026-10-30 06:24:07.91854+00	f	\N
5	7	2026-10-29	1	135	Seeded work log	2026-10-30 06:24:07.91854+00	f	\N
6	7	2026-10-28	1	150	Seeded work log	2026-10-30 06:24:07.91854+00	f	\N
7	10	2026-10-30	1	120	Seeded work log	2026-10-30 06:24:07.91854+00	f	\N
8	10	2026-10-29	1	135	Seeded work log	2026-10-30 06:24:07.91854+00	f	\N
9	10	2026-10-28	1	150	Seeded work log	2026-10-30 06:24:07.91854+00	f	\N
10	8	2026-10-30	1	120	Seeded work log	2026-10-30 06:24:07.91854+00	f	\N
11	8	2026-10-29	1	135	Seeded work log	2026-10-30 06:24:07.91854+00	f	\N
12	8	2026-10-28	1	150	Seeded work log	2026-10-30 06:24:07.91854+00	f	\N
14	11	2026-10-29	1	135	Seeded work log	2026-10-30 06:24:07.91854+00	f	\N
15	11	2026-10-28	1	150	Seeded work log	2026-10-30 06:24:07.91854+00	f	\N
16	9	2026-10-30	1	120	Seeded work log	2026-10-30 06:24:07.91854+00	f	\N
17	9	2026-10-29	1	135	Seeded work log	2026-10-30 06:24:07.91854+00	f	\N
18	9	2026-10-28	1	150	Seeded work log	2026-10-30 06:24:07.91854+00	f	\N
19	11	2026-10-30	1	480	\N	2026-10-30 10:05:34.26407+00	f	\N
24	10	2026-10-30	16	240	lkdvmkjtnh	2026-10-30 10:28:04.151092+00	f	\N
25	16	2026-10-30	16	165	hgfyyjyujvfyfytfytftccccccccccccccccc	2026-10-30 10:31:13.504468+00	f	\N
26	11	2026-10-31	3	60	hgfgcgv	2026-10-31 09:24:36.039506+00	f	\N
27	13	2026-10-31	1	165	hghfthghgvhgv	2026-10-31 09:33:52.618167+00	f	\N
28	13	2026-10-31	1	165	jhughugygy	2026-10-31 09:35:18.045758+00	f	\N
\.


ALTER TABLE public.work_log ENABLE TRIGGER ALL;

--
-- Data for Name: work_schedule; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.work_schedule DISABLE TRIGGER ALL;

COPY public.work_schedule (id, scope_type, scope_id, mode, working_days, start_time, end_time, grace_minutes, half_day_hours, checkout_cutoff, weekly_hours) FROM stdin;
1	LOCATION	1	FIXED	["SUN", "MON", "TUE", "WED", "THU"]	10:00	18:00	15	4.00	23:59	\N
2	LOCATION	2	FIXED	["MON", "TUE", "WED", "THU", "FRI"]	09:00	17:00	15	4.00	23:59	\N
20	EMPLOYEE	14	HOURLY	[]	\N	\N	0	\N	\N	40.00
\.


ALTER TABLE public.work_schedule ENABLE TRIGGER ALL;

--
-- Data for Name: work_task; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.work_task DISABLE TRIGGER ALL;

COPY public.work_task (id, title, client_name, status, due_date, created_by, external_ref, created_at, priority) FROM stdin;
2	Website landing page	Qii Internal	OPEN	\N	1	\N	2026-10-30 06:24:07.91854+00	\N
3	Social campaign pack	Blue Ocean Co	OPEN	\N	4	\N	2026-10-30 06:24:07.91854+00	\N
6	Mgr only 1793355265167	Solo	OPEN	\N	4	\N	2026-10-30 10:14:25.205457+00	\N
4	E2E Task 1793355264282	Acme Client	DONE	2026-11-02	4	PM-E2E-1793355264282	2026-10-30 10:14:24.336253+00	HIGH
8	Bad scope task	X	OPEN	\N	4	\N	2026-10-30 10:15:03.067208+00	\N
9	Mgr only 1793355303293	Solo	OPEN	\N	4	\N	2026-10-30 10:15:03.31637+00	\N
7	E2E Task 1793355302831	Acme Client	DONE	2026-11-02	4	PM-E2E-1793355302831	2026-10-30 10:15:02.891608+00	HIGH
11	Bad scope task	X	OPEN	\N	4	\N	2026-10-30 10:15:47.387265+00	\N
12	Mgr only 1793355347590	Solo	OPEN	\N	4	\N	2026-10-30 10:15:47.6074+00	\N
10	E2E Task 1793355347221	Acme Client	DONE	2026-11-02	4	PM-E2E-1793355347221	2026-10-30 10:15:47.263717+00	HIGH
15	Mgr only 1793355362671	Solo	OPEN	\N	4	\N	2026-10-30 10:16:02.686684+00	\N
13	E2E Task 1793355362469	Acme Client	DONE	2026-11-02	4	PM-E2E-1793355362469	2026-10-30 10:16:02.482614+00	HIGH
16	ABC	fkjvjd	IN_PROGRESS	2026-11-02	4	\N	2026-10-30 10:25:13.504549+00	HIGH
1	Brand identity refresh	Northwind Media	OPEN	\N	1	\N	2026-10-30 06:24:07.91854+00	MEDIUM
17	Figma Design	\N	IN_PROGRESS	2026-11-06	1	\N	2026-11-03 06:27:56.294017+00	MEDIUM
\.


ALTER TABLE public.work_task ENABLE TRIGGER ALL;

--
-- Data for Name: work_task_assignee; Type: TABLE DATA; Schema: public; Owner: -
--

ALTER TABLE public.work_task_assignee DISABLE TRIGGER ALL;

COPY public.work_task_assignee (task_id, employee_id) FROM stdin;
2	11
2	4
2	5
2	7
2	6
3	11
3	4
3	5
3	7
3	6
6	4
4	6
8	12
9	4
7	6
11	12
12	4
10	6
15	4
13	6
16	16
16	7
16	6
16	10
16	17
1	11
1	4
1	5
1	7
1	6
1	13
17	11
17	2
17	8
17	12
\.


ALTER TABLE public.work_task_assignee ENABLE TRIGGER ALL;

--
-- Name: attendance_correction_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.attendance_correction_id_seq', 8, true);


--
-- Name: attendance_record_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.attendance_record_id_seq', 67, true);


--
-- Name: audit_log_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.audit_log_id_seq', 193, true);


--
-- Name: holiday_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.holiday_id_seq', 22, true);


--
-- Name: tenant_settings_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.tenant_settings_id_seq', 1, true);


--
-- Name: work_log_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.work_log_id_seq', 28, true);


--
-- Name: work_schedule_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.work_schedule_id_seq', 20, true);


--
-- Name: work_task_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.work_task_id_seq', 17, true);


--
-- PostgreSQL database dump complete
--

\unrestrict WmS9KnUVYw3Yd5EUO6MbpuG6KDDSa7Mt5o4eHjre31v0bGhfyg3Vj5dQ83PXSJl

