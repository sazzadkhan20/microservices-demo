import { GW, req, token, A, check, summary } from "./lib.mjs";
const H = `${GW}/hrms/api`;
const IMRAN = 7, LINA = 4, TANIA = 6;
const tA = await token(1), tI = await token(IMRAN), tL = await token(LINA), tT = await token(TANIA);

let r = await req("GET", `${H}/attendance/today`, { headers: A(tI) });
console.log("today:", r.text.slice(0, 300));
const already = !!(r.json?.checkIn || r.json?.record?.checkIn);

r = await req("POST", `${H}/attendance/check-in`, { headers: { ...A(tI), "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)", "X-Forwarded-For": "203.0.113.7" }, body: { latitude: 23.78, longitude: 90.41, note: "QA check-in" } });
check("ATT-01 check-in", r.status === 200 || r.status === 201 || already, `status=${r.status} ${r.text.slice(0, 300)}`);
const rec = r.json || {};
check("ATT-02 stores IP + device + location", (rec.ip || rec.ipAddress) && (rec.device || rec.deviceType) && (rec.latitude != null || rec.locationLabel), `ip=${rec.ip ?? rec.ipAddress} device=${rec.device ?? rec.deviceType} lat=${rec.latitude} label=${rec.locationLabel}`, "Medium");
check("ATT-02 X-Forwarded-For spoofable?", (rec.ip || rec.ipAddress) !== "203.0.113.7", `stored ip=${rec.ip ?? rec.ipAddress}`, "Medium");
check("ATT workDate in Asia/Dhaka", (rec.workDate || rec.date) === new Date(Date.now() + 6 * 3600e3).toISOString().slice(0, 10), `workDate=${rec.workDate || rec.date}`);

r = await req("POST", `${H}/attendance/check-in`, { headers: A(tI), body: {} });
check("ATT-01 second check-in refused", r.status === 409 || r.status === 400, `status=${r.status}`);
const dbl = await Promise.all([1, 2, 3].map(() => req("POST", `${H}/attendance/check-in`, { headers: A(tI), body: {} })));
check("ATT race no 500", dbl.every((x) => x.status < 500), dbl.map((x) => x.status).join(","), "Medium");

r = await req("POST", `${H}/attendance/check-out`, { headers: A(tI), body: {} });
check("WL-05/AT-07 checkout blocked without work log", r.status >= 400, `status=${r.status} ${r.text.slice(0, 200)}`, "High");

// tasks
const tasks = (await req("GET", `${H}/tasks/assigned`, { headers: A(tI) })).json;
const list = tasks?.content || tasks || [];
console.log("Imran assigned tasks:", list.map((t) => `${t.id}:${t.status}`).join(","));
const all = (await req("GET", `${H}/tasks?page=0&size=100`, { headers: A(tA) })).json?.content || [];
const notMine = all.find((t) => !(t.assigneeIds || []).map(Number).includes(IMRAN));
let mine = list.find((t) => t.status !== "DONE");
if (!mine) {
  r = await req("POST", `${H}/tasks`, { headers: A(tL), body: { title: "QA task", client: "QA", assigneeIds: [IMRAN], dueDate: "2026-11-30", status: "OPEN" } });
  console.log("create task:", r.status, r.text.slice(0, 200));
  mine = r.json;
}
const today = new Date(Date.now() + 6 * 3600e3).toISOString().slice(0, 10);
if (notMine) {
  r = await req("POST", `${H}/work-logs`, { headers: A(tI), body: { taskId: notMine.id, workDate: today, minutes: 30, note: "x" } });
  check("WL-02/AT-08 log only against own tasks", r.status >= 400, `status=${r.status} ${r.text.slice(0, 140)}`, "High");
}
r = await req("POST", `${H}/work-logs`, { headers: A(tI), body: { taskId: mine?.id, workDate: today, minutes: 10, note: "x" } });
check("WL-01 <15 min refused", r.status >= 400, `status=${r.status} ${r.text.slice(0, 140)}`);
r = await req("POST", `${H}/work-logs`, { headers: A(tI), body: { taskId: mine?.id, workDate: today, minutes: 20, note: "x" } });
check("WL-01 non-15-step refused", r.status >= 400, `status=${r.status} ${r.text.slice(0, 140)}`);
r = await req("POST", `${H}/work-logs`, { headers: A(tI), body: { taskId: mine?.id, workDate: today, minutes: 17 * 60, note: "x" } });
check("WL-04 >16h/day refused", r.status >= 400, `status=${r.status} ${r.text.slice(0, 140)}`);
r = await req("POST", `${H}/work-logs`, { headers: A(tI), body: { taskId: mine?.id, workDate: "2026-10-01", minutes: 30, note: "x" } });
check("WL-03 older than 7 days refused", r.status >= 400, `status=${r.status} ${r.text.slice(0, 140)}`);
r = await req("POST", `${H}/work-logs`, { headers: A(tI), body: { taskId: mine?.id, workDate: "2026-11-05", minutes: 30, note: "future" } });
check("WL future-dated log refused", r.status >= 400, `status=${r.status} ${r.text.slice(0, 140)}`, "Medium");
r = await req("POST", `${H}/work-logs`, { headers: A(tI), body: { taskId: mine?.id, workDate: today, minutes: 30, note: "QA log" } });
check("WL-01 valid log", r.status === 200 || r.status === 201, `status=${r.status} ${r.text.slice(0, 200)}`);
const wlId = r.json?.id;
r = await req("DELETE", `${H}/work-logs/${wlId}`, { headers: A(tT) });
check("WL delete other's log refused", r.status === 403 || r.status === 404, `status=${r.status}`, "High");

r = await req("POST", `${H}/attendance/check-out`, { headers: A(tI), body: {} });
check("ATT checkout after log (80% min of worked)", r.status === 200, `status=${r.status} ${r.text.slice(0, 250)}`);

// correction: self approve
r = await req("POST", `${H}/attendance/corrections`, { headers: A(tI), body: { attendanceDate: "2026-10-28", requestedIn: "2026-10-28T10:00:00", requestedOut: "2026-10-28T18:00:00", reason: "forgot" } });
console.log("correction:", r.status, r.text.slice(0, 200));
const cid = r.json?.id;
if (cid) {
  r = await req("POST", `${H}/attendance/corrections/${cid}/approve`, { headers: A(tI), body: {} });
  check("BR-01 self-approve correction refused", r.status === 403, `status=${r.status}`, "Critical");
  const tM = await token(5);
  r = await req("POST", `${H}/attendance/corrections/${cid}/approve`, { headers: A(tM), body: {} });
  check("ATT-07 non-manager cannot approve correction", r.status === 403, `status=${r.status} ${r.text.slice(0, 150)}`, "High");
  r = await req("POST", `${H}/attendance/corrections/${cid}/approve`, { headers: A(tL), body: {} });
  check("ATT-07 manager approves correction", r.status === 200, `status=${r.status} ${r.text.slice(0, 250)}`);
}
// HR direct edit of own attendance
const tHr = await token(2);
r = await req("POST", `${H}/attendance/correct`, { headers: A(tHr), body: { employeeId: 2, workDate: "2026-10-27", checkIn: "2026-10-27T04:00:00Z", checkOut: "2026-10-27T12:00:00Z", reason: "self edit" } });
check("BR-01 HR cannot edit own attendance", r.status === 403 || r.status === 409, `status=${r.status} ${r.text.slice(0, 160)}`, "Critical");

// statuses via my attendance month
r = await req("GET", `${H}/attendance/my?from=2026-10-01&to=2026-10-31&size=100`, { headers: A(tI) });
const st = (r.json?.content || r.json || []).map((x) => `${x.workDate || x.date}:${x.status}`);
console.log("Imran Oct statuses:", st.join(" "));
r = await req("GET", `${H}/attendance/hours-summary`, { headers: A(tI) });
console.log("hours-summary:", r.text.slice(0, 400));
summary("attendance");
