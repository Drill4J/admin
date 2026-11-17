# Quality gates, release tracks, milestones

## Contents

- [Status values](#status-values)
- [How a milestone is evaluated](#how-a-milestone-is-evaluated)
- [Database](#database)
  - [`raw_data.release_tracks`](#rawdatareleasetracks)
  - [`raw_data.release_track_apps`](#rawdatareleasetrackapps)
  - [`raw_data.milestones`](#rawdatamilestones)
  - [`raw_data.milestone_apps`](#rawdatamilestoneapps)
  - [`raw_data.milestone_quality_gates`](#rawdatamilestonequalitygates)
  - [`raw_data.app_quality_gates`](#rawdataappqualitygates)
- [API](#api)
  - [Lightweight lists](#lightweight-lists)
  - [Release tracks](#release-tracks)
  - [Milestones](#milestones)
  - [Quality gate targets](#quality-gate-targets)
  - [Milestone report](#milestone-report)
  - [App quality gates](#app-quality-gates)
  - [Apps table](#apps-table)
  - [Test projects table](#test-projects-table)
  - [Charts](#charts)
  - [Build report chart](#build-report-chart)
- [Challenges](#challenges)
- [Same branch, separate from quality gates](#same-branch-separate-from-quality-gates)

Configuration for release tracks and the two quality gates (overall testing, impact testing). Report numbers are computed when the API is called, from the builds pinned on a milestone and the existing `metrics` tables. They are not stored.

The UI for this lives on `drill4j-ui` branch `feature/release-tracks-and-qg-mock`. That branch is mock data only: nothing in `api-metrics.js` calls these routes yet. Paths below are the contract that mock has to be replaced with.

All routes are under `/api/metrics`, same auth as the other metrics routes (`jwt` or `api-key`). Reads are `USER` and `ADMIN`. Writes are `ADMIN`.

`groupId` is always in the path. A few calls in the original notes omitted it (`POST /release-tracks`); those are normalized here.

Paged responses use the existing envelope:

```json
{
  "data": [],
  "paging": { "page": 1, "pageSize": 20, "total": 0 }
}
```

Query params on paged routes: `page`, `pageSize`, `sortBy`, `sortOrder` (`ASC` or `DESC`). Default page size is 20. The UI also offers 10, 50, and 100.

## Status values

One set of strings everywhere (list rows, report headers, per-check `isPassed` is separate):

| Value | Meaning |
|---|---|
| `passing` | Every check in that gate is met |
| `failing` | At least one check is missed |
| `calculating` | A pinned build is not ready in metrics yet |
| `not_set` | Targets for that gate have not been saved |
| `select_baseline` | Impact gate only. No baseline milestone is selected, so impact numbers are not computed |

`select_baseline` is what the milestone page shows on the impact chip before a baseline is chosen. The original notes listed `passing` / `failing` / `in progress` / `not set`, and the report sketch used `passed` for the same idea as `passing`. This doc uses the five values above.

## How a milestone is evaluated

A milestone is a named set of `(appId, buildId)` pairs on one track. Overall numbers sum those builds. Impact numbers diff each app's pinned build against that app's build on the baseline milestone.

The baseline defaults to the previous milestone on the same track (the one with the greatest `created_at` that is still older than this one). A report can name another milestone on that same track with `baselineMilestoneId`. Nothing is stored: the track page does not show a baseline or a gate status.

Aggregation is a sum, then a ratio. It is not the average of per-app percentages.

| Check | Value | Passes when |
|---|---|---|
| Overall code | `probesCovered / probesTotal` across pinned builds | `>=` target. Total `0` fails the check |
| Overall methods | `methodsCovered / methodsTotal` | same |
| Overall passed tests | `testsPassed / testsExecuted` | `>=` target, or there are no executed tests |
| Impact code | `(probesCovered + probesOther) / (probesCovered + probesOther + probesGaps)` on the diff | `>=` target. Total `0` fails the check |
| Impact methods | covered `(new + modified)` methods / `(new + modified)` | same. Deleted methods are outside the ratio |
| Impact passed tests | passed impacted tests / impacted tests | `>=` target, or there are no impacted tests |

Impacted tests are distinct tests. A test that touches two apps counts once at milestone and test-project level, and once on each app row.

Overall gate status is `passing` only when code, methods, and passed tests all pass. Impact gate status is `passing` only when changed code, changed methods, and passed impacted tests all pass. `select_baseline` replaces the impact status when no baseline is in effect.

The mock still carries extra target fields (`serviceCoveragePercentage`, `serviceChangeCoveragePercentage`, `minExecutedPercentage`). The gate function computes them and then drops them from the verdict, and the milestone page does not render inputs for them. They are not stored.

## Database

Configuration tables go in `raw_data`, next to `group_settings`. They are user-authored. `metrics.*` stays the ETL output the reports read.

`build_id` is the existing metrics build key `(group_id, app_id, build_id)`. There is no cross-schema foreign key. A missing build is reported as `calculating` until metrics has it, or as an empty app row if the build was removed (see open questions).

### `raw_data.release_tracks`

| Column | | 
|---|---|
| `group_id` | PK |
| `track_id` | PK. The name the user typed (`main`, `special-for-customer-X`). Unique per group, case-insensitive |
| `created_at` | |

The UI mock also slugifies the name into a separate `id`. The API uses the typed name as `trackId` and does not store a second slug.

Milestone count and the latest milestone are derived. Latest means `max(created_at)` on that track.

### `raw_data.release_track_apps`

Every app that can be included in a milestone on this track. A new app in the group is not on the track until someone adds it here. Only then can a new milestone include it.

| Column | |
|---|---|
| `group_id` | PK |
| `track_id` | PK, FK to `release_tracks` |
| `app_id` | PK |

### `raw_data.milestones`

| Column | |
|---|---|
| `group_id` | PK |
| `track_id` | PK, FK to `release_tracks` |
| `milestone_id` | PK. The version string (`release-0.12.0`), unique per track |
| `created_at` | |

The UI mock uses a surrogate (`ms-main-012`) plus a separate `version`. The API uses `milestoneId` as the version. Links and breadcrumbs key off that string. The baseline dropdown lists other milestones on this same track.

### `raw_data.milestone_apps`

Snapshot of what was included when the milestone was saved. A row exists only for an included app.

| Column | |
|---|---|
| `group_id` | PK |
| `track_id` | PK. The milestone's own track |
| `milestone_id` | PK |
| `app_id` | PK |
| `build_id` | required |

`releaseTrackId` in the original sketch is this `track_id`. The baseline build is not a column. For an app, `baselineBuildId` is the `build_id` of the same `app_id` on the baseline milestone. An app that was not on the baseline milestone has no baseline build and no impact block.

`app_id` must already be a row in `release_track_apps` for this track. The create-milestone screen lists that membership, not every app in the group. Saving a milestone writes `milestone_apps` only. It does not add apps to the track.

### `raw_data.milestone_quality_gates`

One row per milestone. Null targets mean that gate is `not_set`.

| Column | |
|---|---|
| `group_id`, `track_id`, `milestone_id` | PK |
| `overall_coverage_target_percentage` | |
| `overall_methods_coverage_target_percentage` | |
| `overall_passed_tests_target_percentage` | |
| `impact_coverage_target_percentage` | |
| `impact_methods_coverage_target_percentage` | |
| `impact_passed_tests_target_percentage` | |

Percentages are `0..100`.

### `raw_data.app_quality_gates`

Per app, independent of any track. The milestone page's "App's quality gates" count uses these targets, and so does the build report header. The help text on the milestone page says these gates do not use the release-track targets.

| Column | |
|---|---|
| `group_id`, `app_id` | PK |
| `code_coverage_target_percentage` | overall |
| `method_coverage_target_percentage` | overall |
| `tests_passed_target_percentage` | overall. Passed / executed |
| `change_coverage_target_percentage` | impact |
| `change_methods_coverage_target_percentage` | impact |
| `impacted_tests_passed_target_percentage` | impact. Passed impacted tests / impacted tests |

Defaults when a row is first saved from the build page, matching the mock: code 70, methods 70, change code 75, change methods 70, both test ratios 80.

The mock also carries `minExecutedPercentage`. The impact section status does not read it (the checks that count are change code, change methods, and passed impacted tests). It is not a column.

Null targets mean that gate is not set, the same rule as `milestone_quality_gates`. No row means every target is null. An app with a null overall or impact side is `unset` for that side: excluded from `passing`, still included in `total`.

## API

### Lightweight lists

For dropdowns and agents. No coverage, no gate status.

```http
GET /api/metrics/groups/{groupId}/release-tracks/list
```

```json
[{ "trackId": "main", "createdAt": "2026-08-12T10:04:00Z" }]
```

```http
GET /api/metrics/groups/{groupId}/release-tracks/{trackId}/milestones/list
```

Ids for this track only. The milestone report's baseline dropdown uses this list and omits the milestone being viewed.

```json
[{
  "milestoneId": "release-0.12.0",
  "createdAt": "2026-09-21T14:30:00Z"
}]
```

Apps and builds already have list endpoints. The create-milestone screen uses those for the include switch and the build picker.

```http
GET /api/metrics/applications?groupId=
GET /api/metrics/builds
```

### Release tracks

```http
POST /api/metrics/groups/{groupId}/release-tracks
```

```json
{ "trackId": "main" }
```

`409` when `trackId` already exists in the group, case-insensitive.

```http
GET /api/metrics/groups/{groupId}/release-tracks
```

The tracks table. `milestoneCount` and `latest` are derived. `latest` is the milestone with the greatest `created_at`. Its impact gate uses the previous milestone on the same track, the one with the next greatest `created_at`. The first milestone has no baseline, so `impact` is omitted.

`overall` and `impact` use the same metric objects as the milestone report.

```json
[{
  "trackId": "main",
  "createdAt": "2026-08-12T10:04:00Z",
  "milestoneCount": 6,
  "latest": {
    "milestoneId": "release-0.16.0",
    "createdAt": "2026-11-16T13:15:00Z",
    "baselineMilestoneId": "release-0.15.0",
    "overall": {
      "status": "passing",
      "codeCoveragePercentage": { "valuePercentage": 88.2, "targetPercentage": 70, "isPassed": true },
      "methodsCoverage": {
        "valuePercentage": 81.1,
        "targetPercentage": 70,
        "isPassed": true,
        "coveredCount": 2584,
        "totalCount": 3188
      },
      "passedTests": {
        "valuePercentage": 96.0,
        "targetPercentage": 80,
        "isPassed": true,
        "passedCount": 480,
        "failedCount": 20,
        "totalCount": 500
      }
    },
    "impact": {
      "status": "failing",
      "codeCoveragePercentage": { "valuePercentage": 78.0, "targetPercentage": 70, "isPassed": true },
      "methodsCoverage": {
        "valuePercentage": 61.0,
        "targetPercentage": 70,
        "isPassed": false,
        "coveredCount": 139,
        "totalCount": 228,
        "missedCount": 89
      },
      "passedTests": {
        "valuePercentage": 70.0,
        "targetPercentage": 80,
        "isPassed": false,
        "passedCount": 45,
        "failedCount": 12,
        "missedCount": 7,
        "totalCount": 64
      }
    }
  }
}]
```

`latest` is `null` when the track has no milestones. `baselineMilestoneId` and `impact` are `null` when that milestone is the first on the track.

### Milestones

```http
PUT /api/metrics/groups/{groupId}/release-tracks/{trackId}/milestones
```

Creates or replaces a milestone. The client sends the full included-app list. Every `appId` must already be in `release_track_apps` for this track. Apps omitted from `apps` are removed from this milestone only. Older milestones stay as they were saved.

```json
{
  "milestoneId": "release-0.13.0",
  "apps": [
    { "appId": "checkout", "buildId": "shop:checkout:0.12.0" },
    { "appId": "inventory", "buildId": "shop:inventory:2.4.1" }
  ]
}
```

At least one app is required.

```http
GET /api/metrics/groups/{groupId}/release-tracks/{trackId}/milestones
```

The track page. Ordered by `created_at` descending.

```json
[{
  "milestoneId": "release-0.12.0",
  "createdAt": "2026-09-21T14:30:00Z"
}]
```

The track page is this list: milestone name and created time. Gate status is on the milestone report, not here.

### Quality gate targets

```http
PUT /api/metrics/groups/{groupId}/release-tracks/{trackId}/milestones/{milestoneId}/quality-gates/overall
```

```json
{
  "coverageTargetPercentage": 70,
  "methodsCoverageTargetPercentage": 70,
  "passedTestsTargetPercentage": 80
}
```

```http
PUT /api/metrics/groups/{groupId}/release-tracks/{trackId}/milestones/{milestoneId}/quality-gates/impact
```

Same body. The impact "passed tests" target applies to passed impacted tests / impacted tests, not to all executed tests.

### Milestone report

Both report routes share one metric object:

```json
{
  "valuePercentage": 83.6,
  "targetPercentage": 70,
  "isPassed": true
}
```

`valuePercentage` and `targetPercentage` are percentages `0..100`. `targetPercentage` and `isPassed` are `null` when the gate is `not_set` or `calculating`. `valuePercentage` is still returned when it can be computed.

```http
GET /api/metrics/groups/{groupId}/release-tracks/{trackId}/milestones/{milestoneId}/quality-gates/overall
```

```json
{
  "milestoneId": "release-0.12.0",
  "status": "passing",
  "codeCoveragePercentage": { "valuePercentage": 83.6, "targetPercentage": 70, "isPassed": true },
  "methodsCoverage": {
    "valuePercentage": 84.3,
    "targetPercentage": 70,
    "isPassed": true,
    "coveredCount": 2688,
    "totalCount": 3188
  },
  "passedTests": {
    "valuePercentage": 96.0,
    "targetPercentage": 80,
    "isPassed": true,
    "passedCount": 480,
    "failedCount": 20,
    "totalCount": 500
  }
}
```

```http
GET /api/metrics/groups/{groupId}/release-tracks/{trackId}/milestones/{milestoneId}/quality-gates/impact
    ?baselineMilestoneId=
```

`baselineMilestoneId` is optional and must be another milestone on this track. When it is omitted, the previous milestone on the track is used. The first milestone has no baseline.

```json
{
  "milestoneId": "release-0.12.0",
  "baselineMilestoneId": "release-0.11.0",
  "status": "failing",
  "codeCoveragePercentage": { "valuePercentage": 78.0, "targetPercentage": 70, "isPassed": true },
  "methodsCoverage": {
    "valuePercentage": 61.0,
    "targetPercentage": 70,
    "isPassed": false,
    "coveredCount": 139,
    "totalCount": 228,
    "missedCount": 89
  },
  "passedTests": {
    "valuePercentage": 70.0,
    "targetPercentage": 80,
    "isPassed": false,
    "passedCount": 45,
    "failedCount": 12,
    "missedCount": 7,
    "totalCount": 64
  }
}
```

`missedCount` on impact methods is `totalCount - coveredCount` (the "N missed" label). `missedCount` on impact tests is tests that were impacted and never executed. `totalCount` on impact tests is the impacted total.

When the effective baseline is null, `status` is `select_baseline` and the three metric objects are omitted.

### App quality gates

App gates are per app. They are not part of a milestone, so these routes are not under `release-tracks`.

```http
GET /api/metrics/groups/{groupId}/apps/quality-gates/overall-count
GET /api/metrics/groups/{groupId}/apps/quality-gates/impact-count
```

```json
{ "passing": 40, "unset": 3, "total": 48 }
```

`total` is the number of apps in the group. `unset` is how many have null targets for that side. `passing` counts apps whose targets for that side are saved and whose checks all pass.

```http
GET /api/metrics/groups/{groupId}/apps/{appId}/quality-gates/overall
PUT /api/metrics/groups/{groupId}/apps/{appId}/quality-gates/overall
```

```json
{
  "codeCoveragePercentage": 70,
  "methodCoveragePercentage": 70,
  "testsPassedPercentage": 80
}
```

```http
GET /api/metrics/groups/{groupId}/apps/{appId}/quality-gates/impact
PUT /api/metrics/groups/{groupId}/apps/{appId}/quality-gates/impact
```

```json
{
  "changeCoveragePercentage": 75,
  "changeMethodsCoveragePercentage": 70,
  "impactedTestsPassedPercentage": 80
}
```

The build report reads and saves the side it is showing. `PUT` writes that side's targets. A null field clears that target. `GET` with no row returns each target on that side as `null`.

### Apps table

```http
GET .../milestones/{milestoneId}/apps-quality-gates
    ?baselineMilestoneId=
    &page=
    &pageSize=
    &sortBy=
    &sortOrder=
```

`isPassed` on each cell is against the milestone targets.

`sortBy`: `codeCoveragePercentage`, `methodsCoveragePercentage`, `testsPassed`, `changeCoveragePercentage`, `changeMethodsCoveragePercentage`, `impactedTests`.

```json
{
  "paging": { "page": 1, "pageSize": 20, "total": 48 },
  "data": [{
    "appId": "checkout",
    "buildId": "shop:checkout:0.12.0",
    "baselineBuildId": "shop:checkout:0.11.0",
    "coverage": { "probesCovered": 3200, "probesTotal": 4100 },
    "changeCoverage": { "probesCovered": 400, "probesOther": 80, "probesGaps": 120 },
    "overall": {
      "isPassed": true,
      "codeCoveragePercentage": { "valuePercentage": 78.0, "isPassed": true },
      "methodsCoverage": { "valuePercentage": 81.0, "isPassed": true, "coveredCount": 900, "totalCount": 1110 },
      "tests": { "passedCount": 40, "failedCount": 2, "totalCount": 42 }
    },
    "impact": {
      "isPassed": false,
      "codeCoveragePercentage": { "valuePercentage": 80.0, "isPassed": true },
      "methodsCoverage": { "valuePercentage": 55.0, "isPassed": false, "coveredCount": 11, "totalCount": 20, "missedCount": 9 },
      "tests": { "passedCount": 6, "failedCount": 1, "missedCount": 2, "totalCount": 9 }
    }
  }]
}
```

`baselineBuildId` is null, and `impact` is omitted, when the baseline milestone has no row for that app. The row still has `overall`. `impact` is also omitted when no baseline is selected.

`coverage` and `changeCoverage` are the absolute probe counts the "coverage by app size" treemap uses (tile size is `probesTotal` or the change total, color is the ratio). The original sketch had only percentages, which cannot size the tiles.

The app name links to the existing build report: `/metrics/{groupId}/apps/{appId}/builds/{buildId}` with `baselineBuildId` when it is present.

### Test projects table

```http
GET .../milestones/{milestoneId}/test-projects
    ?baselineMilestoneId=
    &page=
    &pageSize=
    &sortBy=
    &sortOrder=
```

`sortBy`: `testsTotal`, `testsPassed`, `testsFailed`, `impactedTotal`, `impactedPassed`, `impactedMissed`, `impactedFailed`, `apps`.

```json
{
  "paging": { "page": 1, "pageSize": 20, "total": 10 },
  "data": [{
    "testProjectId": "ui-e2e",
    "testType": { "id": "e2e", "name": "E2E" },
    "affectedAppCount": 11,
    "overall": { "totalTests": 120, "passedTests": 110, "failedTests": 10 },
    "impact": { "totalTests": 30, "passedTests": 24, "failedTests": 4, "missedTests": 2 }
  }]
}
```

`testType.id` is `_Other_` when the project has no other type. The name is `Other`. Null is not a value. `impact` is null when no baseline is selected. `affectedAppCount` is the number of milestone apps the project touched.

There is no `test_type` column today. `test_project_id` is already on `metrics.test_sessions`, `test_definitions`, `test_launches`, and the coverage tables. Adding a type means a new attribute, not a new fact table:

`raw_data.test_projects`

| Column | |
|---|---|
| `group_id`, `test_project_id` | PK |
| `test_type` | Required. Known values: `auto`, `manual`, `unit`, `integration`, `e2e`, `_Other_`. `_Other_` is the unmarked type |

No write API for this yet. Until something sets `test_type`, every project is `_Other_` and the coverage-by-test-type charts have a single bucket.

### Charts

```http
GET .../milestones/{milestoneId}/charts/coverage-by-test-type-overall

GET .../milestones/{milestoneId}/charts/coverage-by-test-type-impact
    ?baselineMilestoneId=
```

```json
{
  "probesCovered": 21540,
  "probesTotal": 25790,
  "byTestType": [
    { "testTypeId": "e2e", "testTypeName": "E2E", "probesCovered": 9000, "probesTotal": 11000 },
    { "testTypeId": "_Other_", "testTypeName": "Other", "probesCovered": 800, "probesTotal": 1400 }
  ]
}
```

Impact uses change-probe totals (`probesCovered + probesOther` over `probesCovered + probesOther + probesGaps`) both for the milestone and for each type.

The UI overlap chart also draws exclusive vs shared slices. The mock fills those with fixed weights, not from probe sets. The API returns per-type totals only. A real exclusive/shared split needs probe-set intersection and is out of this contract.

```http
GET .../milestones/{milestoneId}/charts/coverage-by-app-size-overall

GET .../milestones/{milestoneId}/charts/coverage-by-app-size-impact
    ?baselineMilestoneId=
```

Same rows as the apps table's `appId` + `coverage` (overall) or `changeCoverage` (impact), without the gate fields. The page can use the apps route instead when it already has those counts. An app missing from the baseline milestone is omitted from the impact chart.

### Build report chart

The build page header says there is no test-type coverage API yet and renders a placeholder. Same series as the milestone chart, for one build. Overall and impact are separate routes.

```http
GET /api/metrics/builds/{buildId}/coverage-by-test-type/overall
    ?groupId=
    &appId=
```

```http
GET /api/metrics/builds/{buildId}/coverage-by-test-type/impact
    ?groupId=
    &appId=
    &baselineBuildId=
```

Response shape matches `byTestType` above, plus `probesCovered` and `probesTotal`. `testTypeId` uses `_Other_` for the unmarked type.

The build page's own quality gate targets are `app_quality_gates`. The coverage and test figures on that page stay on the existing build metrics routes.

## Challenges

These deletes also have to drop stored reports. A report that is still `calculating` has no finished row to delete, and the rule for that case is not settled.

1. **Removing an app from a milestone.** Delete that app's reports for this milestone, and every report that uses this milestone as its baseline. **TBD:** what happens to a report that is still calculating.
2. **Deleting an app from the group.** Same report cleanup as above, and delete the app from `release_track_apps` on every track. **TBD:** the same calculating-report case.
3. **Deleting a build that a milestone pins.** The milestone row points at that build. **TBD:** whether the delete is rejected, or the pin and its reports are removed.

## Same branch, separate from quality gates

`feature/release-tracks-and-qg-mock` also adds Group Settings → Smart Coverage. That page is mock data too. It applies to every app and release track in the group when smart coverage is calculated.

Natural home is the existing group settings resource, not a new one:

```http
GET /api/group-settings/{groupId}
PUT /api/group-settings/{groupId}
```

Add to `raw_data.group_settings` (columns or one `jsonb`):

| Field | |
|---|---|
| `smartCoverage.enabled` | default `true`. Off means coverage is the build alone |
| `smartCoverage.branches` | empty means all branches. Masks allowed (`feature*`). The build's own branch is always included |
| `smartCoverage.environments` | empty means all environments |
| `smartCoverage.testStatuses` | default `PASSED`, `UNQUALIFIED`. Allowed: `PASSED`, `UNQUALIFIED`, `FAILED`, `SKIPPED`, `SMART_SKIPPED` |

Current `group_settings` only has `retention_period_days` (and `metrics_period_days` in the Exposed table). This payload is an extension of that resource.
