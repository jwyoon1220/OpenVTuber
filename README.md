# OpenVTuber


## 1. 개요 및 설계 목표 (Overview & Target Performance)

* **목표:** 기존 Unity/C# 기반 버튜버 툴(VSeeFace, Luppet 등)의 프레임 드랍, 높은 CPU/VRAM 점유율, 메모리 누수를 극복하는 AAA급 전용 엔진 구축.
* **타겟 플랫폼:** Windows 10/11 (64-bit), Linux (Ubuntu, Arch Linux, SteamOS / X11 & Wayland). *(macOS는 Metal 변환 레이어 오버헤드로 제외)*
* **렌더링 성능:** 4K 해상도 환경에서 120~144+ fps 유지 (FSR 3.1 Frame Generation 적용 기준).
* **시스템 리소스 점유율:**
* **CPU 점유율:** 8코어 기준 3~5% 미만 유지.
* **RAM 점유율:** 300~500 MB 내외 (GC 스톨 최소화).
* **VRAM 점유율:** 800 MB ~ 1.2 GB (Zero-Copy Shared Memory 적용).


* **트래킹 및 물리 반응성:** 로컬 연산 연동으로 0~2 ms 레이턴시 달성.

---

## 2. 전체 아키텍처 (System Architecture)

```
[ Java / jME3 (JVM) ]
├── 씬 그래프(Scene Graph) & 오케스트레이션
├── Unity C# API Shim Layer (GraalVM / .NET CoreCLR)
└── Scoped Storage (I/O 가상화 및 샌드박싱)
│
│ DirectByteBuffer (Zero-Copy Shared Memory)
▼
[ Native C++ Core Engine (DLL / .so) ]
├── Vulkan 1.3/1.4 Native (G-Buffer, Motion Vector)
├── Vulkan Ray Tracing (VK_KHR_ray_tracing_pipeline)
├── PhysX 5.x (AVX2 / Task Graph Multithreading)
└── AMD FSR 3.1 SDK (Spatial Upscale + Frame Gen)

```

---

## 3. 핵심 모듈별 상세 명세 (Core Modules)

### 3.1. Java(jME3) - C++ Native 하이브리드 파이프라인

* **역할 분담:** Java/jME3는 전체 프레임워크 제어, 스크립트 오케스트레이션, UI 관리를 담당하며, 최하단 그래픽스 및 물리는 C++ Native DLL로 처리.
* **Zero-Copy 메모리 바인딩:** `DirectByteBuffer`를 통해 Java 메모리와 C++ Native 메모리를 직접 공유. JNI 호출 시 데이터 복사 비용을 없애 데이터 전송 레이턴시 $0\text{ ms}$ 구현.

### 3.2. C++ Vulkan 1.3/1.4 렌더링 & FSR 3.1

* **Vulkan Native 타격:** MoltenVK 등 변환 레이어 없이 Windows(NVN/AMD) 및 Linux(Mesa/RADV) Native Vulkan 드라이버 직접 연동.
* **하드웨어 가속 레이 트레이싱 (VK_KHR_ray_tracing_pipeline):**
* 아바타 눈동자 반사(RT Reflections), 머리카락 Soft Shadows, 피부 간접 반사광(RT GI) 제공.
* **AABB 공간 축소 최적화:** 배경 3D 전체가 아닌, 아바타 $N$명의 좁은 AABB 영역만 BVH(Acceleration Structure)로 구성하여 RT 연산량 80% 이상 절감.


* **AMD FSR 3.1 연동:** 1080p 해상도에서 RT를 연산한 후, FSR 3.1 Upscaling 및 Frame Generation을 통해 4K 144fps로 프레임 보정.

### 3.3. PhysX 5.x 기반 물리 연산 엔진

* **AVX2 / Task Graph 멀티스레딩:** CPU 스레드 풀에 시뮬레이션을 분산하여 머리카락, 의상, IK 연산 시 특정 코어 병목 현상 방지.
* **Featherstone Articulated Body / PBD:** 아바타 관절 및 Cloth 시뮬레이션의 계산 수렴 속도를 높여 연산 튐 현상(Explosion) 방지.
* **Direct Transfer:** 물리 연산 결과를 Java를 거치지 않고 C++ 백엔드 내에서 Vulkan UBO 버퍼로 직접 이관.

### 3.4. 분산 P2P Listen Server 물리 모델

* **Client-Side Ownership:** 아바타 머리카락, 옷, IK, 손가락 등 비주얼 물리는 각 클라이언트 PC에서 100% 로컬 연산 (핑 영향 0ms).
* **Host Authority:** 맵 충돌 및 상호작용만 호스트가 담당하며, 트래킹 변환 데이터만 소용량 UDP 패킷(초당 10~20 KB/s 미만)으로 전송.

### 3.5. C# Unity Compatible API Shim Layer

* **스크립트 호환성:** 기존 VRChat 및 유니티 아바타에 내장된 C# `MonoBehaviour` 스크립트 재활용 지원.
* **API 매핑:** `MonoBehaviour` 생명주기, `Transform`, `Animator`, `Time`, `Vector3`, `Quaternion` 등을 jME3 씬 노드로 변환.
* **`UnityEngine.Mathf`:** JNI 경계를 거치지 않도록 C# Shim 라이브러리 내에서 Pure C# Fast-Math 알고리즘으로 100% 자체 재구현.

### 3.6. Scoped Storage 및 보안 샌드박싱

* **Scoped File Access:** 아바타 전용 폴더(`UserData/Avatars/[ID]/`) 및 OS 지정 문서/사진 폴더 내에서만 `System.IO` 읽기/쓰기(Read/Write) 허용.
* **시스템 파일 보호:** `Path.GetFullPath()` 검증을 통해 샌드박스 영역 밖 파일 접근 및 OS 핵심 시스템 파일 접근 시도 거부.
* **보안 격리:** Bytecode Verifier를 통해 `System.Diagnostics.Process` 등 OS 제어 어셈블리 로딩을 강제 차단.

---

## 4. 라이선스 (License)

본 프로젝트는 **AGPLv3 (GNU Affero General Public License v3.0)** 라이선스 하에 배포됩니다.