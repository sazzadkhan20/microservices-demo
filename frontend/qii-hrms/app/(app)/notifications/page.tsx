"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { PageHeader } from "@/components/ui/PageHeader";
import { EmptyState, ErrorNote, Loading } from "@/components/ui/QueryState";
import { Tabs } from "@/components/ui/Tabs";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import { fmtDateTime, humanize } from "@/lib/format";
import {
  notificationSchema,
  resolveNotificationHref,
  springPageSchema,
  type NotificationRow,
} from "@/lib/schemas/hrms";

const PAGE_SIZE = 20;
const pageSchema = springPageSchema(notificationSchema);

export default function NotificationsPage() {
  const qc = useQueryClient();
  const toast = useToast();
  const router = useRouter();
  const [page, setPage] = useState(0); // notification-service pages are 0-based
  const [filter, setFilter] = useState("all");

  const list = useQuery({
    queryKey: ["notifications", page],
    queryFn: ({ signal }) =>
      apiFetch(api.notifications(""), { query: { page, size: PAGE_SIZE }, schema: pageSchema, signal }),
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
  });
  const unread = useQuery({
    queryKey: ["notifications-unread"],
    queryFn: ({ signal }) => apiFetch<{ unreadCount: number }>(api.notifications("unread-count"), { signal }),
    refetchInterval: 30_000,
  });

  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ["notifications"] });
    void qc.invalidateQueries({ queryKey: ["notifications-unread"] });
    void qc.invalidateQueries({ queryKey: ["notifications", "home"] });
  };

  const markOne = useMutation({
    mutationFn: (id: string | number) => apiFetch(api.notifications(`${id}/read`), { method: "POST" }),
    onSuccess: refresh,
    onError: (e) => toast.error(e),
  });
  const markAll = useMutation({
    mutationFn: async (ids: (string | number)[]) => {
      await Promise.all(ids.map((id) => apiFetch(api.notifications(`${id}/read`), { method: "POST" })));
    },
    onSuccess: () => {
      toast.show("Marked as read");
      refresh();
    },
    onError: (e) => toast.error(e),
  });

  const openNotification = async (n: NotificationRow) => {
    const href = resolveNotificationHref(n);
    if (!n.readFlag) {
      try {
        await markOne.mutateAsync(n.id);
      } catch {
        // still navigate if mark-read fails
      }
    }
    if (href) router.push(href);
  };

  const rows: NotificationRow[] = list.data?.content ?? [];
  const unreadOnPage = rows.filter((n) => !n.readFlag);
  const shown = filter === "unread" ? unreadOnPage : rows;
  const totalPages = Math.max(list.data?.totalPages ?? 1, 1);
  const unreadTotal = unread.data?.unreadCount;

  return (
    <div className="stack">
      <PageHeader
        title="Notifications"
        subtitle="Approvals, leave decisions and system messages. Click a notification to open the related page."
        actions={
          <>
            <button type="button" className="btn" onClick={() => refresh()}>
              Refresh
            </button>
            <button
              type="button"
              className="btn primary"
              disabled={unreadOnPage.length === 0 || markAll.isPending}
              onClick={() => markAll.mutate(unreadOnPage.map((n) => n.id))}
            >
              Mark page as read
            </button>
          </>
        }
      />

      <Tabs
        tabs={[
          { id: "all", label: "All" },
          { id: "unread", label: unreadTotal != null ? `Unread (${unreadTotal})` : "Unread" },
        ]}
        value={filter}
        onChange={setFilter}
      />

      <ErrorNote error={list.error} />
      {list.isLoading ? <Loading /> : null}

      {!list.isLoading && !list.error && shown.length === 0 ? (
        <div className="card">
          <EmptyState>{filter === "unread" ? "You’re all caught up." : "No notifications yet."}</EmptyState>
        </div>
      ) : null}

      <div className="stack">
        {shown.map((n) => {
          const href = resolveNotificationHref(n);
          return (
            <div
              key={n.id}
              className="card"
              role={href ? "link" : undefined}
              tabIndex={href ? 0 : undefined}
              onClick={() => void openNotification(n)}
              onKeyDown={(e) => {
                if ((e.key === "Enter" || e.key === " ") && href) {
                  e.preventDefault();
                  void openNotification(n);
                }
              }}
              style={{
                padding: 14,
                borderLeft: n.readFlag ? undefined : "3px solid var(--accent)",
                opacity: n.readFlag ? 0.8 : 1,
                cursor: href ? "pointer" : "default",
              }}
            >
              <div className="row between" style={{ alignItems: "flex-start" }}>
                <div className="stack" style={{ gap: 4, flex: 1, minWidth: 220 }}>
                  <div className="row">
                    <b>{n.title || "Notification"}</b>
                    {n.category ? <span className="pill info">{humanize(n.category)}</span> : null}
                    {!n.readFlag ? <span className="pill acc">New</span> : null}
                    {href ? <span className="muted small">Open →</span> : null}
                  </div>
                  {n.body ? <p className="muted">{n.body}</p> : null}
                  <span className="muted small">{fmtDateTime(n.createdAt)}</span>
                </div>
                {!n.readFlag ? (
                  <button
                    type="button"
                    className="btn sm"
                    disabled={markOne.isPending}
                    onClick={(e) => {
                      e.stopPropagation();
                      markOne.mutate(n.id);
                    }}
                  >
                    Mark read
                  </button>
                ) : null}
              </div>
            </div>
          );
        })}
      </div>

      {totalPages > 1 ? (
        <div className="row between">
          <span className="muted small">
            Page {page + 1} of {totalPages}
            {list.data?.totalElements != null ? ` · ${list.data.totalElements} total` : ""}
          </span>
          <div className="row">
            <button type="button" className="btn sm" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
              Prev
            </button>
            <button
              type="button"
              className="btn sm"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
