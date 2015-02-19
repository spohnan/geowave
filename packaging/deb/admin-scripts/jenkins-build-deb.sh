#!/bin/bash
#
# For use by deb building jenkins jobs
#
# Requires the following packages: build-essential, debhelper and devscripts
#
set -x

echo '###### Build Variables'

declare -A ARGS
while [ $# -gt 0 ]; do
    # Trim the first two chars off of the arg name ex: --foo
    case "$1" in
        *) NAME="${1:2}"; shift; ARGS[$NAME]="$1" ;;
    esac
    shift
done

echo '###### Clean up workspace'

cd "${WORKSPACE}/${ARGS[buildroot]}"
./deb.sh --command clean

echo '###### Update artifact(s)'

./deb.sh \
    --command update \
    --job ${ARGS[job]} \
    --geoserver ${ARGS[geoserver]}

echo '###### Build deb'

./deb.sh \
    --command build
