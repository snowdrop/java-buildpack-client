#!/bin/bash

# Check if the correct number of arguments is provided
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 DIRECTORY NEW_VERSION"
    exit 1
fi

DIRECTORY=$1
NEW_VERSION=$2

# Find all files named pack.java under the given directory and its subdirectories
# Then use xargs and sed to update the buildpack-client version in each file
find "$DIRECTORY" -type f -name 'pack.java' -print0 | xargs -0 sed -i'' -e "s/buildpack-client:[0-9].[0-9].[0-9]/buildpack-client:$NEW_VERSION/g"
