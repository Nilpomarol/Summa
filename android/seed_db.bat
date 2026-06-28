@echo off
set PKG=com.gestorfinances.app
set DB_NAME=gestor-finances.db
set SQL_FILE=seed_db.sql
set ADB="C:\Users\nilpo\AppData\Local\Android\Sdk\platform-tools\adb.exe"

echo Pushing SQL script to device...
%ADB% push %SQL_FILE% /data/local/tmp/%SQL_FILE%

echo Executing SQL script...
%ADB% shell "run-as %PKG% sqlite3 /data/data/%PKG%/databases/%DB_NAME% < /data/local/tmp/%SQL_FILE%"

echo Done! Refresh your app.
pause
