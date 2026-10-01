# Future Windows App

Status: not started. This document is planning context, not a current architecture requirement.

If a Windows product is started, prefer a simple native desktop app over reproducing Android structure mechanically.

Likely direction:

- C# + WinUI 3;
- Microsoft.Data.Sqlite and Dapper;
- reuse genuinely canonical finance schema/SQL and focused golden rules;
- desktop-appropriate navigation, density, keyboard interaction, and large-screen layouts;
- CSV bank import may remain desktop-only.

Do not build Windows-specific seams in Android before Windows implementation starts. Re-evaluate this plan against the actual Android/data model at that time rather than treating old parity assumptions as fixed requirements.
