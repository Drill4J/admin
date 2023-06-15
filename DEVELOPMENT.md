# Development of Drill4J

See README.md for high-level information about Drill4J component.

This document describes building and the development of Drill4J.

## Branches

There are following branch types:
- main branch
  - name pattern: **main**
  - description: main branch, direct pushes are forbidden, only pull-request allowed 
- release branch
  - name pattern: **release/\<version\>**
  - description: release branches automatically created during release publishing, used for hot-fixe releases development and publishing
- merge branch
  - name pattern: **merge/\<version\>**
  - description: temporary branches, may be created to simplify merge of feature branches into main branch
- adoption branch
  - name pattern: **adoption/\<adoption-name\>**
  - description: branches with adoption-specific changes that cannot be integrated into main branch
- feature branches
  - name pattern: **feature/\<branch-description\>**
  - name pattern: **bugfix/\<branch-description\>**
  - name pattern: **docs/\<branch-description\>**
    - **\<branch-description\>**: should be in format **\<description\>-\<optional jira ticket\>** (like feature/do-something-cool-epmdj-12356 or feature/do-something-cool)
  - description: branches for feature (bugfix, documentation) development, should be merged into main/adoption branch via pull-request and removed at feature completion

TODO: add visualization

## Versions

There are two types of versioning:
- version created from **main** branch
  - format: **MAJOR.MINOR.PATCH** (like 0.8.3)
  - increment: **PATCH** version auto-incremented after each release
  - tags: new tag automatically created for release in **main** branch
  - branches: new release branch automatically created for release forked from **main** branch
- versions created from **adoption** branches
  - format: **MAJOR.MINOR.PATCH-suffix.PRERELEASE** (like 0.8.3-alpine.2)
  - increment: **PRERELEASE** version auto-incremented after each release
  - tags: new tag automatically created for release in **adoption** branch
  - branches: new release branch automatically created for release forked from **adoption** branch
- versions created from **release** branches
  - format: **MAJOR.MINOR.PATCH-suffix.PRERELEASE** (like 0.8.3-hf.0)
  - increment: **PRERELEASE** version auto-incremented after each release
  - tags: new tag automatically created for release in **release** branch
  - branches: no release branches automatically created from **release** branches

See also: https://semver.org/

## Commits

Commit messages should be descriptive and use following format:
```
<type>(<optional scope>): <ticket description>

<changes description>

Refs: <jira ticket>
```

Where:
- **\<type\>**: commit type, one of following values:
  - **\<feat\>**: feature implementation
  - **\<fix\>**: bug-fix implementation
  - **\<docs\>**: add/update documentation
  - **\<refactor\>**: code refactoring
  - **\<test\>**: tests implementation
  - **\<build\>**: build-scripts implementation
- **\<optional scope\>**: affected modules of application
- **\<ticket description\>**: description of ticket in which scope changes was made
- **\<changes description\>**: brief description of changes itself
- **\<jira ticket\>**: JIRA ticket number

See also: https://www.conventionalcommits.org/

## Merges and pull-requests

There are two types of merges:
- Merges from **main**/**merge**/**adoption**/**feature** branches: merges should be done using **merge-commits**, please **avoid cherry-picks and rebase**
- Changes from **release** branches: merges should be done using **cherry-pick-commits**, please **avoid merge-commits**

Merges to **main** branch should be performed via **pull-requests only** with code review and automatic code checks:
- Tip of the main must always work
- Each merge request must have a result in working state

To merge changes in main branch please perform following steps: 
- Create **merge/\<version\>** branch from the tip of the **main** branch
- Create pull-requests from **feature** branches to **merge/\<version\>** branch
- Accept pull-requests for each feature branch as merge-commit
- Merge **merge/\<version\>** into **main** branch
- Remove **merge/\<version\>** branch and **feature** branches

Keep feature branches up to date:
- Merge **main** branch into **feature** branches as frequently as possible
- Before merge **feature** branch into **main**/**merge** branch ensure that **feature** branch is updated by last changes from **main** (see above)

## Release publishing

New releases should be published using manual run of "**Release**" GitHub workflow in corresponding component repository.

Version of lib-jvm-shared used by component is freezing during "**Release**" GitHub workflow execution:
- New tag in lib-jvm-shared repository is creating in format: **<component-name>-<version>**
- Variable **libJvmSharedRef** is changing to newly created tag, changed **gradle.properties** file is committing to **release** branch

Following files are included in release (per component):
- admin: zip archive with application .jar files (admin-\<version\>.zip, admin-shadow-\<version\>.zip), docker image in GitHub container registry (https://github.com/drill4j/admin/pkgs/container/admin)
- test2code-plugin: zip archive with admin-part.jar, agent-part.jar and plugin_config.json files (test2code-plugin-\<version\>.zip)
- java-agent: zip archive with drillRuntime.jar file and libdrill_agent.so/libdrill_agent.dll library for supported platforms (agent-linuxX64-\<version\>.zip, agent-mingwX64-\<version\>.zip)

## Local development environment

To set up environment for local development please perform following steps:
1. Install tools (git, gradle, java)
2. Clone component repository, checkout corresponding branch
3. Run script to set up lib-jvm-shared repository from Git: setup-shared-libs.bat/setup-shared-libs.sh

### Moving lib-jvm-shared to custom directory

There is a possibility to use **lib-jvm-shared** libraries from custom directory.
To change **lib-jvm-shared** location please use `sharedLibsLocalPath` variable in **gradle.properties** file (both absolute and relative paths are supported).

## Making changes

There is following workflow to make changes to component code:
- Create **feature** branch and switch to it
- Update **lib-jvm-shared** libraries to actual state using **updateSharedLibs** gradle task
- If specific changes in lib-jvm-shared libraries required do following:
  - create separate branch of **lib-jvm-shared** 
  - update variable **libJvmSharedRef** in **gradle.properties** file to corresponding tag name 
- Implement code changes
- Ensure that component successfully assembled and tested using **assemble**+**check** or **build** gradle tasks
- Commit and push changes
- Create pull-request and merge changes in corresponding branch (**main**, **adoption** or **release**)
- Run "**Release**" GitHub workflow to publish new release
