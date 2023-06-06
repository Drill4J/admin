@echo off
for /f "eol=# delims== tokens=2" %%r in ('findstr sharedLibsRef gradle.properties') do set sharedLibsRef=%%r
set sharedLibsRef=%sharedLibsRef: =%

echo Removing lib-jvm-shared directory
rmdir /s /q lib-jvm-shared

echo Cloning https://github.com/Drill4J/lib-jvm-shared repository with branch %sharedLibsRef%
git clone https://github.com/Drill4J/lib-jvm-shared lib-jvm-shared --branch %sharedLibsRef%
