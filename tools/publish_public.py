#!/usr/bin/env python3
"""Publish a branch's current files to the public repo as one parentless snapshot commit.

The public repo only ever holds a single commit: the tree of the chosen branch, written as a new
root commit force-pushed over `public/main`. Its timestamp is always after 17:00 local time: now,
or, before 17:00, a random evening time the day before. No private commit, hash, message, or
timestamp is reachable from it. Runs as a dry run unless `--push` is given.

    python tools/publish_public.py            # show what would be published
    python tools/publish_public.py --push     # publish main
"""

from __future__ import annotations

import argparse
import os
import random
import subprocess
import sys
from datetime import datetime, timedelta

PUBLIC_REMOTE = "public"
PRIVATE_REMOTE = "origin"
PUBLIC_BRANCH = "main"
EARLIEST_HOUR = 17


def snapshot_time(now: datetime) -> datetime:
    """Now if it is already evening, otherwise a random evening time the day before."""
    if now.hour >= EARLIEST_HOUR:
        return now.replace(microsecond=0)
    return (now - timedelta(days=1)).replace(
        hour=random.randint(EARLIEST_HOUR, 22), minute=random.randint(0, 59), second=random.randint(0, 59), microsecond=0,
    )


def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout.strip()


def fail(message: str) -> None:
    sys.exit(f"refusing to publish: {message}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ref", default="main", help="private branch whose files are published (default: main)")
    parser.add_argument("--message", help="commit message (default: 'Summa snapshot <date>')")
    parser.add_argument("--push", action="store_true", help="actually force-push; otherwise only report")
    args = parser.parse_args()

    remotes = git("remote").split()
    if PUBLIC_REMOTE not in remotes or PRIVATE_REMOTE not in remotes:
        fail(f"expected remotes '{PRIVATE_REMOTE}' and '{PUBLIC_REMOTE}', found {remotes}")
    if "Summa-private" not in git("remote", "get-url", PRIVATE_REMOTE):
        fail(f"'{PRIVATE_REMOTE}' does not point at the private repo")

    # Only publish what the private repo already has, so nothing public is missing from private.
    local = git("rev-parse", "--verify", f"{args.ref}^{{commit}}")
    try:
        backed_up = git("rev-parse", "--verify", f"{PRIVATE_REMOTE}/{args.ref}^{{commit}}")
    except subprocess.CalledProcessError:
        backed_up = None
    if local != backed_up:
        fail(f"'{args.ref}' differs from '{PRIVATE_REMOTE}/{args.ref}'; push it to the private repo first")

    email = git("config", "user.email")
    if not email.endswith("@users.noreply.github.com"):
        fail(f"author email '{email}' is not a GitHub noreply address")

    when = snapshot_time(datetime.now().astimezone())
    if when.hour < EARLIEST_HOUR:
        fail(f"snapshot time {when} is before {EARLIEST_HOUR}:00")
    message = args.message or f"Summa snapshot {when.date().isoformat()}"
    os.environ["GIT_AUTHOR_DATE"] = os.environ["GIT_COMMITTER_DATE"] = when.isoformat()

    tree = git("rev-parse", f"{args.ref}^{{tree}}")
    # No -p: the snapshot is a root commit, so no private history is reachable from it.
    snapshot = git("commit-tree", tree, "-m", message)
    if git("rev-list", "--count", snapshot) != "1" or git("rev-list", "--parents", "-n", "1", snapshot) != snapshot:
        fail("snapshot commit unexpectedly has parents")

    files = git("ls-tree", "-r", "--name-only", tree).splitlines()
    print(f"source:   {args.ref} ({local[:7]}), {len(files)} files")
    print(f"snapshot: {snapshot[:7]} '{message}' by {git('log', '-1', '--format=%an <%ae>, %ad', snapshot)}")
    print(f"target:   {PUBLIC_REMOTE} {git('remote', 'get-url', PUBLIC_REMOTE)} -> {PUBLIC_BRANCH} (force, replaces everything)")

    if not args.push:
        print("dry run: nothing pushed. Re-run with --push to publish.")
        return

    # An explicit single refspec: no other branch, tag, or private ref goes with it.
    subprocess.run(["git", "push", "--force", PUBLIC_REMOTE, f"{snapshot}:refs/heads/{PUBLIC_BRANCH}"], check=True)
    refs = git("ls-remote", PUBLIC_REMOTE).splitlines()
    heads = [line for line in refs if "\trefs/heads/" in line or "\trefs/tags/" in line]
    if heads != [f"{snapshot}\trefs/heads/{PUBLIC_BRANCH}"]:
        print("warning: the public repo has other refs; review them:", *heads, sep="\n  ")
    print(f"published {snapshot[:7]} to {PUBLIC_REMOTE}/{PUBLIC_BRANCH}")


if __name__ == "__main__":
    main()
