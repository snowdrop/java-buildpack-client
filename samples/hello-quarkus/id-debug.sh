#!/bin/bash

# A simple script to dump uid/gid & /workspace permissions during a build. 
#
# This can be very useful when implementing a platform, to determine if /workspace
# has permissions for the executing uid/gid. 

id
ls -aln /workspace

exit 0
