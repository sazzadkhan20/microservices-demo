import Link from "next/link";

export default function AccessPendingPage() {
  return (
    <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", padding: 24 }}>
      <div className="card stack" style={{ maxWidth: 480 }}>
        <h1>Access pending</h1>
        <p className="muted">
          Your account has no role assigned yet. Ask HR or an admin to grant a role, then sign in again.
        </p>
        <div>
          <Link href="/login" className="btn primary">
            Back to sign in
          </Link>
        </div>
      </div>
    </div>
  );
}
