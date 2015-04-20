#!/bin/bash
#
# RPM build script
#

# Source all our reusable functionality, argument is the location of this script.
. ../../admin-scripts/rpm-functions.sh "$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

declare -A ARGS
while [ $# -gt 0 ]; do
    case "$1" in
        *) NAME="${1:2}"; shift; ARGS[$NAME]="$1" ;;
    esac
    shift
done

# Artifact settings
ARTIFACT_01_URL=${ARGS[artifact-base-url]}/geowave-deploy/target/geowave-accumulo.jar
ARTIFACT_02_URL=${ARGS[artifact-base-url]}/geowave-deploy/target/geowave-geoserver.jar
ARTIFACT_03_URL=${ARGS[artifact-base-url]}/geowave-types/target/geowave-ingest-tool.jar
ARTIFACT_04_URL=${ARGS[artifact-base-url]}/target/site.tar.gz
ARTIFACT_05_URL=${ARGS[artifact-base-url]}/geowave-deploy/target/puppet-scripts.tar.gz
ARTIFACT_06_URL=${ARGS[artifact-base-url]}/docs/target/manpages.tar.gz
ARTIFACT_07_URL=${ARGS[geoserver-url]}
RPM_ARCH=noarch

case ${ARGS[command]} in
    build) rpmbuild \
                --define "_topdir $(pwd)" \
                --define "_vendor ${ARGS[vendor]}" \
                --define "_version $(parseVersion)" \
                $(buildArg "${ARGS[buildarg]}") SPECS/*.spec ;;
    clean) clean ;;
   update)
        update_artifact $ARTIFACT_01_URL;
        update_artifact $ARTIFACT_02_URL;
        update_artifact $ARTIFACT_03_URL;
        update_artifact $ARTIFACT_04_URL;
        update_artifact $ARTIFACT_05_URL;
        update_artifact $ARTIFACT_06_URL;
        update_artifact $ARTIFACT_07_URL geoserver.zip; ;;
        *) about ;;
esac
