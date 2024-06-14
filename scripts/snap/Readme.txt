The purposes of these scripts is to merge some subfolder to jb-main branch from other branch (usually it is "integration" or "integration-release/something").

The HEAD should be on the commit which you want to merge.

It creates 2 branches:
- integration-snap/to-jb-main/$hash - should be merged to jb-main. It is created from the merging currentCommit to merge-base(currentCommit, jb-main)
- integration-snap/to-integration/$hash - should be merged to integration, to avoid conflicts in future merges of jb-main. It is createad as "empty" merge of "to-jb-main" to merge-base(currentCommit, integration)
