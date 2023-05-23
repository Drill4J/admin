#! /bin/bash

export `grep sharedLibsRef gradle.properties | tr -d [:space:]`

echo 'Removing lib-jvm-shared directory'
rm -rf lib-jvm-shared

echo 'Cloning https://github.com/Drill4J/lib-jvm-shared repository with branch' $sharedLibsRef
git clone https://github.com/Drill4J/lib-jvm-shared lib-jvm-shared --branch $sharedLibsRef
