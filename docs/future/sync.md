# Future Synchronization

Status: not implemented and not selected current work. This document preserves intent only; it is not a current runtime contract.

Principles to reconsider when synchronization work actually starts:

- keep the product local-first;
- preserve atomic finance operations;
- never silently resolve conflicting financial edits with last-write-wins;
- make read-only/offline/conflict states explicit to the user;
- validate incoming data before replacing or applying it;
- design against the two real clients that exist at that time.

Previous snapshot/token and cloud-linked designs are intentionally not treated as binding architecture. Choose the simplest synchronization model that fits the actual Windows/Android products when this work begins.
