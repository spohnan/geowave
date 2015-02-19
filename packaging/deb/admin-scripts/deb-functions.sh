#!/bin/bash
#
# The reusable functionality needed to update, build and deploy .deb files.
# Should be sourced by individual projects which then only need to override
# any unique behavior
#

# Absolute path to the directory containing admin scripts
ADMIN_SCRIPTS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# When sourcing this script the directory of the calling script is passed
CALLING_SCRIPT_DIR=$1

if [ -z $BUILD_TIMESTAMP ]; then
  export BUILD_TIMESTAMP=$(date +%Y%m%d%H%M)
fi

# Our artifacts will each have a build.properties file
parseVersion() {
	# We're actually just going to examine a single artifact but they should match
	# Extract the metadata file and remove any SNAPSHOT identifiers, we'll add a timestamp to our RPM name
    echo $(unzip -p debian/sources/geowave-accumulo.jar build.properties | grep "project.version=" | sed -e 's/"//g' -e 's/-SNAPSHOT//g' -e 's/project.version=//g')
}

build() {
  export GEOWAVE_VERSION=$(parseVersion)
  construct_source_trees
  dpkg-buildpackage -b -tc -us
}

clean() {
  debuild clean
  rm -f $CALLING_SCRIPT_DIR/debian/files
  rm -f $CALLING_SCRIPT_DIR/debian/*.log
  rm -f $CALLING_SCRIPT_DIR/../*.changes
  rm -f $CALLING_SCRIPT_DIR/../*.deb
}

# Just grabbed off the Interwebs, looks to give sane results in the
# couple of tests I've written. Add more and tweak if found to be defective
isValidUrl() {
	VALID_URL_REGEX='(https?|ftp|file)://[-A-Za-z0-9\+&@#/%?=~_|!:,.;]*[-A-Za-z0-9\+&@#/%=~_|]'
	[[ $1 =~ $VALID_URL_REGEX ]] && return 0 || return 1
}

update_artifact() {
	ARTIFACT_URL="$1"	# Required, url to fetch asset
	DOWNLOAD_NAME="$2"	# Optional, rename file upon download

	# Sanity check the URL argument
	if ( ! isValidUrl "$ARTIFACT_URL" ); then
		echo >&2 "Artifact URL $ARTIFACT_URL does not appear to be valid.  Aborting."
		if [ $TEST_ENV ]; then
			return 1
		else
			exit 1
		fi
	fi

    # Construct the download command
	if [ $DOWNLOAD_NAME ]; then
		CMD="curl $CURL_ARGS -o "$DOWNLOAD_NAME" $ARTIFACT_URL"
 	else
 		CMD="curl $CURL_ARGS -O $ARTIFACT_URL"
 	fi

    # CD to the desired directory in a subshell so as not to affect
 	# the rest of the script PWD
 	if [ ! $TEST_ENV ]; then
 		( cd "$CALLING_SCRIPT_DIR/debian/sources" > /dev/null ; `$CMD` )
 	else
 		echo $CMD # If under test environment
 	fi
}

construct_source_trees() {
    SOURCES_DIR=$CALLING_SCRIPT_DIR/debian/sources

    # geowave-accumulo
    unzip -p $SOURCES_DIR/geowave-accumulo.jar build.properties > $SOURCES_DIR/geowave-accumulo-build.properties

    # geowave-docs
    tar -xzf $SOURCES_DIR/site.tar.gz -C $SOURCES_DIR

    # Compile man pages and prepare documentation files
    mkdir $SOURCES_DIR/manpages
    for file in `ls $SOURCES_DIR/site/manpages/*.adoc`; do
      a2x -f manpage $file -D $SOURCES_DIR/manpages
    done
    rm -rf $SOURCES_DIR/manpages/*.adoc
    rm -rf $SOURCES_DIR/site/manpages/
    rm -f $SOURCES_DIR/site/*.pdfmarks

    # geowave-ingest
    unzip -p $SOURCES_DIR/geowave-ingest-tool.jar build.properties > $SOURCES_DIR/geowave-ingest-build.properties
    unzip -p $SOURCES_DIR/geowave-ingest-tool.jar geowave-ingest.sh > $SOURCES_DIR/geowave-ingest.sh
    unzip -p $SOURCES_DIR/geowave-ingest-tool.jar geowave-ingest-cmd-completion.sh > $SOURCES_DIR/geowave-ingest-cmd-completion.sh
}

source_props() {
	. $ADMIN_SCRIPTS_DIR/../../default-props.sh
	if [ -f $ADMIN_SCRIPTS_DIR/../../local-props.sh ]; then
	  . $ADMIN_SCRIPTS_DIR/../../local-props.sh
	fi
}

source_props