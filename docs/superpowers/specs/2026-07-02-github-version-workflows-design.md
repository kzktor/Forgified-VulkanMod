# GitHub Version Workflows

## Goal

Maintain Minecraft 1.21.1 and 1.20.1 as separate branches with separate GitHub Actions workflows.

Keep the repository organized like the upstream `xCollateral/VulkanMod` project: Gradle and source files stay in their existing locations, while GitHub configuration stays under `.github`.

## Branches

- Rename `1.21.x` to `1.21.1`.
- Rename `local/neoforge-1.20.1-backport` to `1.20.1`.
- Push both version branches to `origin` and set their upstream tracking branches.

No source changes or unrelated working-tree changes will be included in the workflow commits.

## Workflows

Each branch will keep its own `.github/workflows/build.yml`, based on the existing workflow from `main`.

No reusable-workflow layer, generated configuration, or additional automation directory will be introduced.

Each workflow will:

- run for pushes to its version branch;
- run for pull requests targeting its version branch;
- validate the Gradle wrapper;
- install the Java version used by that branch;
- run `./gradlew build` on Ubuntu;
- upload the contents of `build/libs` under a version-specific artifact name;
- fail early if `minecraft_version` in `gradle.properties` does not match the branch version.

The 1.21.1 branch uses Java 21. The 1.20.1 branch uses Java 17, matching their Gradle toolchains.

The workflows will not create tags or GitHub Releases.

## Verification

Before committing each workflow:

- confirm the YAML structure and branch filters;
- confirm the declared Minecraft and Java versions;
- run the relevant local Gradle build when the current working tree permits it;
- inspect the staged diff to ensure the commit contains only intended files.

Commit messages will be short and direct.
