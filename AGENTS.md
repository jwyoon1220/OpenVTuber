# AGENTS.md

## Project purpose

OpenVTuber is an AGPLv3 virtual-avatar engine targeting Windows 10/11 and Linux
(Ubuntu, Arch Linux, SteamOS; X11 and Wayland). The long-term goals described in
`README.md` are native Vulkan 1.3/1.4 rendering, low-latency local tracking and
physics, zero-copy JVM/native communication, low CPU/RAM/VRAM usage, and high
frame rates at 4K.

Treat the architecture in `README.md` as the target direction, not as a statement
that every component already exists. The current implementation is Kotlin/JVM
with LWJGL, Kool, Vulkan, Assimp, and JOML. Do not silently replace the current
renderer or introduce jME3, C++, PhysX, FSR, CoreCLR, networking, or a Unity shim
unless the task explicitly requires that work.

## Repository layout

- `api/`: renderer-independent public API only. It must not contain a main class
  or expose Kool, LWJGL, Vulkan, Assimp, or other backend implementation types.
- `core/`: engine implementation, Vulkan/Kool integration, native bindings,
  model conversion, asset catalogs, and performance-sensitive data structures.
  It must not contain the application entry point.
- `application/`: composition root and the only module containing the main class.
- `run/`: runtime working directory and local model assets. The root `run` task
  launches the application with this directory as its working directory.

Keep dependencies directed as follows:

```text
application -> core -> api
application --------> api
```

Do not introduce a dependency from `api` to `core` or `application`, or from
`core` to `application`.

## Build and run

Use the Gradle wrapper from the repository root.

```powershell
.\gradlew.bat test
.\gradlew.bat :application:installDist
.\gradlew.bat run
```

On Unix-like systems use `./gradlew` instead. The root `run` task is the canonical
way to launch the application. Native calls require
`--enable-native-access=ALL-UNNAMED`; preserve this setting in application and
tooling tasks.

Before handing off a code change, run tests and the smallest relevant build.
For renderer, native-library, asset-loading, or working-directory changes, also
run the root `run` task and confirm Vulkan initialization and model loading in the
logs.

## Current model-loading design

- Public requests and handles live under `api/.../model`.
- `ModelSourceResolver` validates and normalizes model paths.
- `ModelPreprocessor` separates source conversion from GPU loading.
- `FbxConverter` uses native Assimp and maintains a versioned GLB cache.
- `KoolModelAssetCatalog` owns handle allocation, load state, bounds, deduplication,
  and Kool model lifetime.
- Hot metadata is stored in structure-of-arrays form; backend objects remain in a
  separate cold object column.

Preserve both usage styles:

- object-oriented access through `KoolModelAsset`;
- data-oriented access through `ModelAssetId`, catalog state, and packed bounds.

GPU resource creation must remain on Kool's render coroutine. Registration and
path preparation may happen before GPU loading. Do not add reflection-based
discovery, `ServiceLoader`, runtime classpath scanning, or hidden global mutable
registries.

## Performance and native-boundary rules

The README sets aggressive performance targets. Changes on hot paths should:

- avoid per-frame allocation, boxing, collection rebuilding, and temporary
  vector/matrix objects;
- prefer primitive arrays, packed buffers, stable integer handles, and batched
  operations for frequently iterated data;
- use direct native buffers for JNI/LWJGL boundaries and document buffer ownership;
- make native resource ownership explicit and release resources deterministically;
- avoid blocking file I/O, model conversion, or shader compilation inside the
  steady-state frame loop;
- cache converted assets using source timestamps and an explicit cache version;
- keep renderer-independent contracts free of JVM backend classes so a future
  native core or GraalVM integration does not require rewriting the public API.

Native compatibility means Windows x64 and Linux x64 unless a task explicitly
changes the platform matrix. macOS is out of scope per `README.md`. Do not add
Windows-only assumptions to public APIs. Keep path handling in `java.nio.file`
inside implementation code and use forward-slash asset URIs when passing paths to
Kool/glTF loaders.

## Vulkan and rendering rules

- Vulkan is required. Do not add an OpenGL fallback unless explicitly requested.
- Keep `useOpenGlFallback = false` in the default application configuration.
- Validate renderer changes on an actual Vulkan startup, not only compilation.
- Maintain explicit material behavior for imported character assets: two-sided
  rendering, alpha cutout where needed, and base-color textures taking precedence
  over Assimp-generated constant white vertex colors.
- Do not modify source avatar textures or FBX files to hide an importer bug.
  Fix conversion metadata or loader behavior instead.

## Assets and generated files

Files under `run/model/fbx`, `run/model/texture`, and `run/model/psd` are source
assets and belong to the user. Do not overwrite or delete them. Generated GLB
files and their version sidecars belong under `run/model/cache`.

When changing conversion behavior:

1. increment `FbxConverter.CACHE_VERSION`;
2. regenerate the cache through the normal application or `convertModel` task;
3. verify material-to-texture URIs, UV attributes, and model bounds;
4. confirm the model loads from the `run` working directory.

Do not commit temporary screenshots, extracted archives, diagnostic previews,
Gradle output, IDE metadata, or other generated investigation files.

## Coding conventions

- Use Kotlin for JVM modules unless an existing boundary requires Java or native
  code.
- Prefer constructor injection and small interfaces at native/backend boundaries.
- Keep the public API minimal, backend-neutral, and source-compatible where
  practical.
- Validate public value types at construction and fail early with actionable
  messages.
- Keep comments focused on non-obvious native, ownership, format, or performance
  constraints.
- Preserve unrelated user changes and avoid broad mechanical rewrites.

## Security direction

The README plans scoped storage and script sandboxing. Until those systems exist,
do not claim arbitrary avatar scripts or paths are sandboxed. New file APIs must
normalize paths and validate them against an explicitly configured allowed root
before reads or writes. Do not expose process execution, unrestricted filesystem
access, or arbitrary native-library loading through avatar-facing APIs.

## Licensing

The project is AGPLv3. New bundled engine code or dependencies must be compatible
with AGPLv3 distribution. Record copied or adapted third-party code and its license
in `core/THIRD_PARTY_NOTICES.txt`. Do not add ARR/proprietary source or dependencies
whose redistribution terms conflict with AGPLv3.

## Completion checklist

- Module boundaries and dependency direction remain intact.
- `api` contains no entry point or backend implementation types.
- Native resources have explicit ownership and cleanup.
- Tests pass with the Gradle wrapper.
- Relevant distribution tasks build successfully.
- Vulkan/model-loading changes are exercised through the root `run` task.
- Source assets remain untouched; only versioned cache artifacts are regenerated.
- License notices are updated when third-party code is introduced.
