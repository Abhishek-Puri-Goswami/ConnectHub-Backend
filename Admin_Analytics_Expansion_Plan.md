# Admin Analytics — Expansion Plan (documented, not built)

This is a reference plan, not a build queue. The original architecture
review's call still stands: don't invest further here without a reason to
check the dashboard more than you already do. If that reason ever shows up,
this is where to start — tiered by how much new plumbing each item needs.

## What already exists (more than the original audit gave credit for)

`AnalyticsService` (auth-service) snapshots 7 fields every 15 minutes,
kept for 7 days: `onlineCount`, `totalUsers`, `activeUsers`,
`suspendedUsers`, `messagesToday`, `activeRooms`, `storageMb`. The frontend
(`AdminDashboard.jsx`) already charts **every one of these** — online users
(area chart), messages (bar chart), active rooms (area chart), user
breakdown (multi-line chart: total/active/suspended), plus stat cards with
delta badges (up/down vs. 1h and 24h ago) and a 6h/12h/24h timeframe
picker. There's also a sortable/filterable user management table with
suspend/reactivate/delete/promote/demote, and an audit log tab. This is a
reasonably complete admin dashboard already — the "expansion" ideas below
are genuinely new dimensions, not filling gaps in what's collected.

## Tier 1 — reuses data already collected, no new plumbing

Lowest cost, because the data already sits in `AnalyticsSnapshot` rows and
just isn't rendered yet.

- **Storage growth trend chart.** `storageMb` is snapshotted every 15
  minutes but only ever shown as a single "latest value" stat card, never
  charted over time like the other 6 fields are. Same shape as the existing
  charts — a `<Line dataKey="storageMb">` next to the others.
- **Tier distribution donut** (FREE vs PRO users). `stats.pro` is already
  computed client-side from the loaded user list (`AdminDashboard.jsx` line
  ~211) — it's just not visualized as its own chart, only folded into a
  stat count.
- **Growth rate annotations** on existing charts (e.g. "+12% vs yesterday"
  next to the total-users line) — the delta-badge pattern already exists
  for stat cards, extending it to chart titles is the same computation.

## Tier 2 — needs small, additive backend changes

Still reuses `AnalyticsService`'s existing scheduled-snapshot pattern, just
adds a field or two rather than a new subsystem.

- **New signups per day** (vs. cumulative `totalUsers`, which the chart
  already has). Needs one new counter derived from `users.createdAt` in the
  existing snapshot query — same shape as `activeUsers`/`suspendedUsers`.
- **Room type breakdown** (GROUP vs DM active rooms). `activeRooms` is
  currently a single number from room-service's `countActiveRooms()` Feign
  call — would need that endpoint to return a breakdown instead of a total,
  a small room-service change, not a new service.
- **Average messages per active room** — pure arithmetic on two numbers
  already snapshotted (`messagesToday` / `activeRooms`), no new data
  collection at all, just a derived stat card.

## Tier 3 — real new subsystems, tied to other paused decisions

Not recommended without a concrete reason — each of these either needs a
new cross-service data path or directly depends on Razorpay, which this
project has already decided to pause further investment in.

- **Revenue / MRR tile.** `payment-service`'s `Payment`/`Subscription`
  entities already have everything needed (`amount`, `status`, `plan`,
  `createdAt`) — technically cheap. Explicitly **not recommended right
  now**: building admin revenue reporting for a payment system with no
  real transactions yet duplicates the exact "impressive but no real value
  behind it" pattern the original audit flagged. Revisit only alongside
  actually resuming Razorpay work.
- **Retention cohorts** (e.g. "% of users still active 7/30 days after
  signup"). Needs a real analytical query pattern (cohort-by-signup-week),
  not achievable by extending the existing 15-minute snapshot table —
  would need its own query against `users.createdAt` +
  `users.lastSeenAt` directly, run on demand rather than snapshotted.
- **Per-room engagement metrics** (most active rooms, messages per room
  over time). Needs message-service to expose per-room aggregates it
  doesn't currently compute (today it only exposes a platform-wide
  `countToday()`), plus a new Feign call from auth-service or a dedicated
  read path — real new work, not a config value.
- **CSV/PDF export of the dashboard.** Pure frontend work (no backend
  change — the data's already loaded client-side), but still a real
  feature to build and maintain (formatting, pagination edge cases,
  browser download handling) for a "nice to have" that no one has asked
  for yet.

## Recommendation if picking something up

Tier 1 is genuinely nearly-free (reuses existing chart components and
existing data) and would be reasonable to do opportunistically if ever
back in `AdminDashboard.jsx` for another reason. Tier 2 is fine background
work but not worth a dedicated session on its own. Tier 3 should stay
parked until there's a real trigger (Razorpay resuming, or the platform
having enough real usage that retention/engagement questions are actually
being asked by someone, not just "available to build").
