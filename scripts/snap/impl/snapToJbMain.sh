#!/bin/bash

set -e

if [ -z "$1" ]; then
echo "Specify the snapping subfolder. For example: ./snapToJbMain.sh androidx/compose-ui/1.6.0-alpha02 compose ':(exclude)compose/material3'"
exit 1
fi

DIR=$(dirname "$0")
ALL_FOLDERS=${@:1}
CURRENT_COMMIT=$(git rev-parse --short @)
BRANCH_TO_RESTORE_IN_THE_END=$(git branch --show-current)


TO_JB_MAIN_BRANCH=integration-snap/to-jb-main/$CURRENT_COMMIT
git checkout --quiet $(git merge-base $CURRENT_COMMIT origin/jb-main) -B $TO_JB_MAIN_BRANCH
$DIR/snapSubfolder.sh $CURRENT_COMMIT $ALL_FOLDERS
echo "Created $TO_JB_MAIN_BRANCH"

TO_INTEGRATION_BRANCH=integration-snap/to-integration/$CURRENT_COMMIT
git checkout --quiet $(git merge-base $CURRENT_COMMIT origin/integration) -B $TO_INTEGRATION_BRANCH
$DIR/mergeEmpty.sh $TO_JB_MAIN_BRANCH
echo "Created $TO_INTEGRATION_BRANCH"


git checkout --quiet $BRANCH_TO_RESTORE_IN_THE_END