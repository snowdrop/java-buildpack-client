#!/bin/bash

#
# Script requires `grab`. To install it:
# curl -s https://raw.githubusercontent.com/shellib/grab/master/install.sh | bash
#

source $(grab github.com/shellib/cli)
source $(grab github.com/shellib/maven as maven)

release_version=$(readopt --release-version $*)
if [ -z $release_version ]; then
  echo "Option --release_version is required!"
  exit 1
fi

maven::release $*

#
# Set release version in the samples & readme
#
find ./samples/ -iwholename "*/pack.java" | while read f; do
    sed -i "s/\/\/DEPS dev.snowdrop:buildpack-client:.*/\/\/DEPS dev.snowdrop:buildpack-client:${release_version}/g" $f
    git add $f
done
sed -i "s/\/\/DEPS dev.snowdrop:buildpack-client:.*/\/\/DEPS dev.snowdrop:buildpack-client:${release_version}/g" README.md
sed -i "s/<version>.*<\/version>/<version>${release_version}<\/version>/g" README.md
git add README.md
git commit -m "chore: set release version in samples and README.md"
