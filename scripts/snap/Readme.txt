The purposes of these scripts is to merge some subfolder (aka "snap") to jb-main branch from "integration" or "integration-release/*".

1. Checkout the commit you want to snap ("integration" or "integration-release/*")

2. Merge jb-main to integration, pick "jb-main" state in a case of conflicts in other folders

3. Call ./snapCompose.sh (use another script for another folder)

It creates 2 branches:
- integration-snap/$hash/to-jb-main - should be merged to "jb-main". It is created from the merging currentCommit to merge-base(currentCommit, jb-main)
- integration-snap/$hash/to-integration - should be merged to "integration", to avoid conflicts in future merges of jb-main. It is created as "empty" merge of "to-jb-main" to merge-base(currentCommit, integration)
