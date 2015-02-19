#!/bin/bash

# Source all our reusable functionality, argument is the location of this script.
. ../../../admin-scripts/deb-functions.sh "$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

declare -A ARGS
while [ $# -gt 0 ]; do
    case "$1" in
        *) NAME="${1:2}"; shift; ARGS[$NAME]="$1" ;;
    esac
    shift
done

# Artifact settings
ARTIFACT_01_URL=$LOCAL_JENKINS/job/${ARGS[job]}/lastSuccessfulBuild/artifact/geowave-deploy/target/geowave-accumulo.jar
ARTIFACT_02_URL=$LOCAL_JENKINS/job/${ARGS[job]}/lastSuccessfulBuild/artifact/geowave-deploy/target/geowave-geoserver.jar
ARTIFACT_03_URL=$LOCAL_JENKINS/job/${ARGS[job]}/lastSuccessfulBuild/artifact/geowave-types/target/geowave-ingest-tool.jar
ARTIFACT_04_URL=$LOCAL_JENKINS/job/${ARGS[job]}/lastSuccessfulBuild/artifact/target/site.tar.gz
ARTIFACT_05_URL=$LOCAL_JENKINS/userContent/geoserver/${ARGS[geoserver]}

case ${ARGS[command]} in
  build) build;;
  clean) clean;;
 update)
        update_artifact $ARTIFACT_01_URL;
        update_artifact $ARTIFACT_02_URL;
        update_artifact $ARTIFACT_03_URL;
        update_artifact $ARTIFACT_04_URL;
        update_artifact $ARTIFACT_05_URL geoserver.zip; ;;
      *) build; clean;;
esac
