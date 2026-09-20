<#
.SYNOPSIS
  Reports (and with -Fix removes) data that outlived the room or account it belonged to.

.DESCRIPTION
  Normal deletions are cleaned up by Kafka events (room.deleted, auth.user.deleted). This script finds what
  older deletions, or a lost event, left behind, across the separate databases and Redis. Run it any time:
      .\cleanup-orphans.ps1          report only, changes nothing
      .\cleanup-orphans.ps1 -Fix     also delete what it found

  Media rows are report-only: their files live on disk, so they must be removed through media-service
  (delete the room / user, or the file) rather than by SQL.
  Needs MySQL running and the MYSQL_ROOT_PASSWORD from .env. Edit the paths below if your installs differ.
#>
param([switch]$Fix)

$MysqlExe = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
$RedisCli = "D:\redis\redis-cli.exe"

$root = $PSScriptRoot
$rootPassword = (Get-Content (Join-Path $root ".env") | Where-Object { $_ -match '^MYSQL_ROOT_PASSWORD=' } | Select-Object -First 1) -replace '^MYSQL_ROOT_PASSWORD=', ''
if (-not $rootPassword) { throw "MYSQL_ROOT_PASSWORD not found in .env" }

function Invoke-Sql([string]$sql) {
    # -D: multi-table DELETE with aliases needs a default database selected (MySQL error 1046 otherwise)
    $out = & $MysqlExe -u root "-p$rootPassword" -D connecthub_auth -N -B -e $sql 2>$null
    if ($LASTEXITCODE -ne 0) { throw "MySQL query failed: $sql" }
    return $out
}

# name, count query, delete statement (or $null = report only)
$checks = @(
    @("messages in deleted rooms",
      "SELECT COUNT(*) FROM connecthub_message.messages m WHERE NOT EXISTS (SELECT 1 FROM connecthub_room.rooms r WHERE r.room_id = m.room_id)",
      "DELETE m FROM connecthub_message.messages m WHERE NOT EXISTS (SELECT 1 FROM connecthub_room.rooms r WHERE r.room_id = m.room_id)"),
    @("messages by deleted users",
      "SELECT COUNT(*) FROM connecthub_message.messages m WHERE NOT EXISTS (SELECT 1 FROM connecthub_auth.users u WHERE u.user_id = m.sender_id)",
      "DELETE m FROM connecthub_message.messages m WHERE NOT EXISTS (SELECT 1 FROM connecthub_auth.users u WHERE u.user_id = m.sender_id)"),
    @("reactions on missing messages",
      "SELECT COUNT(*) FROM connecthub_message.message_reactions x WHERE NOT EXISTS (SELECT 1 FROM connecthub_message.messages m WHERE m.message_id = x.message_id)",
      "DELETE x FROM connecthub_message.message_reactions x WHERE NOT EXISTS (SELECT 1 FROM connecthub_message.messages m WHERE m.message_id = x.message_id)"),
    @("reactions by deleted users",
      "SELECT COUNT(*) FROM connecthub_message.message_reactions x WHERE NOT EXISTS (SELECT 1 FROM connecthub_auth.users u WHERE u.user_id = x.user_id)",
      "DELETE x FROM connecthub_message.message_reactions x WHERE NOT EXISTS (SELECT 1 FROM connecthub_auth.users u WHERE u.user_id = x.user_id)"),
    @("room members of deleted rooms",
      "SELECT COUNT(*) FROM connecthub_room.room_members x WHERE NOT EXISTS (SELECT 1 FROM connecthub_room.rooms r WHERE r.room_id = x.room_id)",
      "DELETE x FROM connecthub_room.room_members x WHERE NOT EXISTS (SELECT 1 FROM connecthub_room.rooms r WHERE r.room_id = x.room_id)"),
    @("room members that are deleted users",
      "SELECT COUNT(*) FROM connecthub_room.room_members x WHERE NOT EXISTS (SELECT 1 FROM connecthub_auth.users u WHERE u.user_id = x.user_id)",
      "DELETE x FROM connecthub_room.room_members x WHERE NOT EXISTS (SELECT 1 FROM connecthub_auth.users u WHERE u.user_id = x.user_id)"),
    @("rooms with no members",
      "SELECT COUNT(*) FROM connecthub_room.rooms r WHERE NOT EXISTS (SELECT 1 FROM connecthub_room.room_members x WHERE x.room_id = r.room_id)",
      "DELETE r FROM connecthub_room.rooms r WHERE NOT EXISTS (SELECT 1 FROM connecthub_room.room_members x WHERE x.room_id = r.room_id)"),
    @("rooms whose creator is gone (but which still have members)",
      "SELECT COUNT(*) FROM connecthub_room.rooms r WHERE NOT EXISTS (SELECT 1 FROM connecthub_auth.users u WHERE u.user_id = r.created_by_id) AND EXISTS (SELECT 1 FROM connecthub_room.room_members x WHERE x.room_id = r.room_id)",
      "UPDATE connecthub_room.rooms r SET r.created_by_id = (SELECT x.user_id FROM connecthub_room.room_members x WHERE x.room_id = r.room_id ORDER BY (x.role = 'ADMIN') DESC, x.joined_at ASC LIMIT 1) WHERE NOT EXISTS (SELECT 1 FROM connecthub_auth.users u WHERE u.user_id = r.created_by_id) AND EXISTS (SELECT 1 FROM connecthub_room.room_members x WHERE x.room_id = r.room_id)"),
    @("notifications for deleted users",
      "SELECT COUNT(*) FROM connecthub_notification.notifications n WHERE NOT EXISTS (SELECT 1 FROM connecthub_auth.users u WHERE u.user_id = n.recipient_id)",
      "DELETE n FROM connecthub_notification.notifications n WHERE NOT EXISTS (SELECT 1 FROM connecthub_auth.users u WHERE u.user_id = n.recipient_id)"),
    @("notifications about deleted rooms",
      "SELECT COUNT(*) FROM connecthub_notification.notifications n WHERE n.room_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM connecthub_room.rooms r WHERE r.room_id = n.room_id)",
      "DELETE n FROM connecthub_notification.notifications n WHERE n.room_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM connecthub_room.rooms r WHERE r.room_id = n.room_id)"),
    @("push device tokens of deleted users",
      "SELECT COUNT(*) FROM connecthub_notification.device_tokens d WHERE NOT EXISTS (SELECT 1 FROM connecthub_auth.users u WHERE u.user_id = d.user_id)",
      "DELETE d FROM connecthub_notification.device_tokens d WHERE NOT EXISTS (SELECT 1 FROM connecthub_auth.users u WHERE u.user_id = d.user_id)"),
    @("email preferences of deleted users",
      "SELECT COUNT(*) FROM connecthub_notification.user_email_preferences p WHERE NOT EXISTS (SELECT 1 FROM connecthub_auth.users u WHERE u.user_id = p.user_id)",
      "DELETE p FROM connecthub_notification.user_email_preferences p WHERE NOT EXISTS (SELECT 1 FROM connecthub_auth.users u WHERE u.user_id = p.user_id)"),
    @("subscriptions of deleted users",
      "SELECT COUNT(*) FROM connecthub_payment.subscriptions s WHERE NOT EXISTS (SELECT 1 FROM connecthub_auth.users u WHERE u.user_id = s.user_id)",
      $null),
    @("media files in deleted rooms (files on disk: remove via media-service)",
      "SELECT COUNT(*) FROM connecthub_media.media_files f WHERE f.room_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM connecthub_room.rooms r WHERE r.room_id = f.room_id)",
      $null),
    @("media files of deleted users (files on disk: remove via media-service)",
      "SELECT COUNT(*) FROM connecthub_media.media_files f WHERE NOT EXISTS (SELECT 1 FROM connecthub_auth.users u WHERE u.user_id = f.uploader_id)",
      $null)
)

$total = 0
Write-Host "Database leftovers:"
foreach ($c in $checks) {
    $n = [int](Invoke-Sql $c[1])
    $total += $n
    $mark = if ($n -eq 0) { "OK  " } else { "FOUND" }
    Write-Host ("  {0} {1,5}  {2}" -f $mark, $n, $c[0])
    if ($Fix -and $n -gt 0 -and $c[2]) { Invoke-Sql $c[2] | Out-Null; Write-Host "        -> fixed" }
}

# Redis: unread counters of deleted users/rooms, session-tracking keys of deleted users
Write-Host "Redis leftovers:"
$userIds = @{}; Invoke-Sql "SELECT user_id FROM connecthub_auth.users" | ForEach-Object { $userIds["$_"] = $true }
$roomIds = @{}; Invoke-Sql "SELECT room_id FROM connecthub_room.rooms" | ForEach-Object { $roomIds["$_"] = $true }
$stale = @()
foreach ($key in (& $RedisCli -h localhost --scan --pattern "unread:*")) {
    $parts = $key -split ":", 3
    if (-not $userIds[$parts[1]] -or -not $roomIds[$parts[2]]) { $stale += $key }
}
$unread = $stale.Count
foreach ($key in (& $RedisCli -h localhost --scan --pattern "session:*")) {
    $parts = $key -split ":", 3
    if (-not $userIds[$parts[1]]) { $stale += $key }
}
$sessions = $stale.Count - $unread
Write-Host ("  {0} {1,5}  unread counters of deleted users/rooms" -f $(if ($unread -eq 0) { "OK  " } else { "FOUND" }), $unread)
Write-Host ("  {0} {1,5}  session keys of deleted users" -f $(if ($sessions -eq 0) { "OK  " } else { "FOUND" }), $sessions)
$total += $stale.Count
if ($Fix -and $stale.Count -gt 0) {
    foreach ($key in $stale) { & $RedisCli -h localhost del $key | Out-Null }
    Write-Host "        -> fixed"
}

if ($total -eq 0) { Write-Host "No orphaned data found." }
elseif (-not $Fix) { Write-Host "`n$total leftover item(s). Run with -Fix to remove them (media files are report-only)." }
