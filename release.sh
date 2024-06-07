#!/bin/bash

#
# Script requires `grab`. To install it:
# curl -s https://raw.githubusercontent.com/shellib/grab/master/install.sh | bash
#

source $(grab github.com/shellib/cli)
source $(grab github.com/shellib/maven as maven)

release_version=$(readopt --release-version $*)
dev_version=$(readopt --dev-version $*)
if [ -z $release_version ]; then
  echo "Option --release_version is required!"
  exit 1
fi

#
# Set release version in the samples & readme
#
find ./samples/ -iwholename "*/pack.java" | while read f; do
    sed -i "s/dev.snowdrop:buildpack-client:.*/dev.snowdrop:buildpack-client:${release_version}\}/g" $f
    git add $f
done
sed -i "s/\/\/DEPS dev.snowdrop:buildpack-client:.*/\/\/DEPS dev.snowdrop:buildpack-client:${release_version}/g" README.md
sed -i "s/<version>.*<\/version>/<version>${release_version}<\/version>/g" README.md
git add README.md
git commit -m "chore: set release version: ${release_version} in samples and README.md"

#
# release
#

maven::release $*

#
# Set dev-release version in the samples (needed for CI)
#
find ./samples/ -iwholename "*/pack.java" | while read f; do
    sed -i "s/\/\/DEPS dev.snowdrop:buildpack-client:.*/\/\/DEPS dev.snowdrop:buildpack-client:${dev_version}/g" $f
    git add $f
done
git commit -m "chore: set dev version: ${dev_version} in samples and README.md"
