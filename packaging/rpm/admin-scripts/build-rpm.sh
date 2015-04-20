#!/bin/bash
#
# Build the GeoWave RPMs
#

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ALL_ARGS="$@"

# Transform build variables passed to this script into an array of arguments
declare -A ARGS
while [ $# -gt 0 ]; do
    case "$1" in
        *) NAME="${1:2}"; shift; ARGS[$NAME]="$1" ;;
    esac
    shift
done

# Clean workspace
cd "${SCRIPT_DIR}/../${ARGS[os]}"
./rpm.sh --command clean

# Update artifact(s)
./rpm.sh --command update $ALL_ARGS

# Build RPMs
./rpm.sh $ALL_ARGS --command build --buildarg ba # ba = build all (binary and source rpms)
