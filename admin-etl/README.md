# ETL Engine

## Overview

The `admin-etl` module contains a reusable ETL (Extract-Transform-Load) engine that is used by the `admin-metrics` module to periodically update aggregated metrics in the Drill4J Admin service.

The module is built around four main abstractions:

- `DataExtractor`
- `DataLoader`
- `EtlPipeline`
- `EtlOrchestrator`

## Architecture

The ETL engine is based on a **pipeline** and **orchestration** model:

* A pipeline (`EtlPipeline`) represents a single logical ETL process:
    - one `DataExtractor` (source),
    - one or more `DataLoader` implementations (sinks).
* The orchestrator (`EtlOrchestrator`) manages:
    - instantiation and configuration of `EtlPipeline`,
    - concurrency across multiple pipelines,
    - storing process state and results,
    - running and re-running pipelines on schedule or demand,
    - error handling and logging.

Data usually flows as:

1. `DataExtractor` reads rows from the database using a JDBC cursor with `fetchSize`.
2. Extracted items are emitted into a shared flow with capacity `bufferSize`.
3. `DataLoader`-s consume items in batches controlled by `batchSize` and write them into target metric tables via JDBC.

## Configuration

The `admin-metrics` module exposes `EtlConfig`, which is consumed by ETL components to control performance characteristics:

- `bufferSize`
    - Size of the in-memory buffer between extractor and loaders.
    - Affects throughput and memory usage.
    - Default: `2000`.
- `fetchSize`
    - JDBC fetch size hint for SQL queries in `DataExtractor`.
    - Determines how many rows are fetched per round trip.
    - Default: `2000`.
- `batchSize`
    - Number of items grouped into a single write batch/transaction by `DataLoader`.
    - Affects commit frequency and DB load.
    - Default: `1000`.

## Storing metadata

The ETL engine persists execution metadata and current state in the `etl_metadata` table to enable incremental processing, track pipeline health, and support resumable operations.

### Table Structure

The table uses a composite primary key `(pipeline_name, extractor_name, loader_name)` to uniquely identify each ETL flow component combination:

| Column | Description                                                                                                                                              |
|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `pipeline_name` | Name of the ETL pipeline. Part of composite primary key.                                                                   |
| `extractor_name` | Name of the data extractor used in the pipeline. Part of composite primary key.                                                                          |
| `loader_name` | Name of the specific data loader; pipelines may have multiple loaders writing to different targets. Part of composite primary key.                       |
| `last_processed_at` | Timestamp of the last successfully processed data item. Used as a watermark for incremental processing. |
| `last_run_at` | Timestamp when the pipeline was last executed, regardless of success or failure.                                                                         |
| `status` | Current execution status: `STARTING`, `RUNNING`, `SUCCESS`, or `FAILED`.                                                                                 |
| `error_message` | Error description if the pipeline failed.                                                                                             |
| `duration` | Cumulative duration in milliseconds across all executions for this pipeline/extractor/loader combination.                                                |
| `rows_processed` | Cumulative count of rows processed across all executions.                                                                                                |
| `created_at` | Timestamp when the metadata record was first created.                                                                                                    |
| `updated_at` | Timestamp when the metadata record was last modified.                                                                                                    |

### Persistence Strategy

Metadata is persisted by `EtlOrchestrator` during pipeline execution:

1. **Before execution**: The orchestrator queries existing metadata to determine the `last_processed_at` timestamp for each loader, which defines the starting point for incremental data extraction.

2. **During execution**: As each loader completes processing its batch of data, a callback is triggered to update metadata with the current execution results.

3. **After execution**: The orchestrator performs an **upsert** operation:
    - If no metadata exists for the given `(pipeline_name, extractor_name, loader_name)` key, a new record is inserted.
    - If metadata exists, it is updated with:
        - new `last_processed_at` watermark,
        - new `last_run_at` timestamp,
        - current `status` and optional `error_message`,
        - accumulated `duration` and `rows_processed` (summed with previous values).

4. **Error handling**: If metadata persistence fails (e.g., due to database issues), the error is logged but does not abort the pipeline execution itself.

## Running

The ETL orchestrator is executed through the Quartz-based `UpdateMetricsEtlJob`, which is scheduled by the `DrillScheduler` in the `admin-app` module.

### Scheduled Execution

The ETL job is scheduled at application startup with a configurable cron expression:

- **Default schedule**: `0 * * * * ?` (every minute)
- **Configuration property**: `drill.scheduler.etlJobCron` in `application.conf`
- **Job identity**: `metricsEtl` in group `metricsJobs`

The scheduler uses a `@DisallowConcurrentExecution` annotation to prevent multiple ETL runs from overlapping.

### On Demand Execution

For manual triggering, the ETL orchestrator can also be invoked via an admin REST API endpoint (not covered in this document).
The orchestrator supports two execution modes through the `EtlOrchestrator` interface:

#### 1. Incremental Run (`run()`)

```kotlin
suspend fun run(initTimestamp: Instant = Instant.EPOCH): List<EtlProcessingResult>
```

- **Purpose**: Process only new or modified data since the last successful run.
- **Behavior**:
    - Queries `etl_metadata` to retrieve `last_processed_at` watermarks for each loader.
    - Passes these timestamps to pipelines to extract data incrementally.
    - Updates metadata after each loader completes.
- **Use case**: Periodic scheduled updates to refresh metrics with recent data.

#### 2. Full Rerun (`rerun()`)

```kotlin
suspend fun rerun(initTimestamp: Instant = Instant.EPOCH, withDataDeletion: Boolean): List<EtlProcessingResult>
```

- **Purpose**: Rebuild metrics from scratch, optionally deleting existing metric data.
- **Behavior**:
    - Deletes all metadata records for the orchestrator's pipelines from `etl_metadata`.
    - If `withDataDeletion = true`, invokes `cleanUp()` on each pipeline to truncate target tables.
    - Executes a full `run()` starting from `initTimestamp` (typically based on the configured metrics retention period).
- **Use case**: Recovery from original data in case of changes that occurred retrospectively or periodic full refresh to restore consistency.

## Usage

The ETL engine is designed for reusability and extensibility. Below are the typical steps to implement a custom ETL pipeline.

### 1. Create a DataExtractor

A `DataExtractor` is responsible for reading raw data from a source (typically a database) and emitting it as a stream of items.

The module provides `UntypedSqlDataExtractor` for SQL-based extraction that works with untyped `Map<String, Any?>` rows:

```kotlin
val coverageExtractor = UntypedSqlDataExtractor(
    name = "coverage",
    sqlQuery = fromResource("/metrics/db/etl/coverage_extractor.sql"),
    database = database,
    fetchSize = 2000  // JDBC fetch size for streaming
)
```

**Key parameters:**
- `name`: Unique identifier for the extractor
- `sqlQuery`: SQL query with `:sinceTimestamp` and `:untilTimestamp` parameters for incremental extraction
- `database`: Exposed `Database` instance
- `fetchSize`: Number of rows fetched per JDBC round trip (controls memory usage and throughput)

**SQL Query Requirements:**
- Must include placeholders `:sinceTimestamp` and `:untilTimestamp` for time-based filtering
- Should return rows ordered by timestamp for consistent incremental processing
- Use appropriate indexes for efficient queries on large datasets

### 2. Create DataLoaders

A `DataLoader` receives extracted data and persists it to a target table. Each pipeline can have multiple loaders writing to different targets.

The module provides `UntypedSqlDataLoader` for SQL-based upsert operations:

```kotlin
val buildMethodCoverageLoader = UntypedSqlDataLoader(
    name = "build_method_coverage",
    sqlUpsert = fromResource("/metrics/db/etl/build_method_coverage_loader.sql"),
    sqlDelete = fromResource("/metrics/db/etl/build_method_coverage_delete.sql"),
    lastExtractedAtColumnName = "created_at",
    database = database,
    batchSize = 1000,  // Transaction batch size
    processable = { true }  // Optional filter predicate
)
```

**Key parameters:**
- `name`: Unique identifier for the loader
- `sqlUpsert`: SQL statement with named parameters (e.g., `:build_id`, `:method_id`) for inserting/updating rows
- `sqlDelete`: SQL statement to delete all rows from target table (used during `rerun()` with data deletion)
- `lastExtractedAtColumnName`: Name of the timestamp column used to track processing progress
- `batchSize`: Number of rows per transaction batch
- `processable`: Optional predicate to filter rows before loading (e.g., skip rows with missing required fields)

### 3. Assemble an EtlPipeline

An `EtlPipeline` connects one extractor to one or more loaders, managing data flow and concurrency:

```kotlin
val coveragePipeline = EtlPipelineImpl(
    name = "coverage",
    extractor = coverageExtractor,
    loaders = listOf(
        buildMethodCoverageLoader
    ),
    bufferSize = 2000  // In-memory buffer between extractor and loaders
)
```

**Key parameters:**
- `name`: Unique identifier for the pipeline
- `extractor`: The `DataExtractor` instance
- `loaders`: List of `DataLoader` instances that will process the extracted data in parallel
- `bufferSize`: Size of the shared flow buffer between extraction and loading stages

### 4. Create an EtlOrchestrator

An `EtlOrchestrator` manages multiple pipelines, handles metadata persistence, and provides high-level execution control:

```kotlin
val metricsOrchestrator = EtlOrchestratorImpl(
    name = "metrics",
    pipelines = listOf(
        coveragePipeline
    ),
    metadataRepository = EtlMetadataRepositoryImpl(database)
)
```

### 5. Execute the Orchestrator

**Incremental Execution:**

```kotlin
// Process only new data since last successful run
val results: List<EtlProcessingResult> = metricsOrchestrator.run(
    initTimestamp = Instant.EPOCH  // Fallback if no prior metadata exists
)
```

**Full Rerun:**

```kotlin
// Rebuild all metrics from scratch
val results = metricsOrchestrator.rerun(
    initTimestamp = Instant.now().minus(30, ChronoUnit.DAYS),  // Process last 30 days
    withDataDeletion = true  // Clear existing metric data before reprocessing
)
```