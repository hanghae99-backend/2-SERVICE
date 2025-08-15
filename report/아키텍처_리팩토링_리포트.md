# New Architecture - Feature-based Clean Architecture

## 🏗️ 아키텍처 개요

**Feature-based Clean Architecture**를 적용하여 도메인 중심의 모듈화된 구조로 리팩토링했습니다.

### 핵심 원칙
- **도메인 중심 설계**: 비즈니스 도메인별로 모듈 분리
- **의존성 역전**: Infrastructure가 Domain을 의존하고 구현
- **계층 분리**: API → Domain ← Infrastructure (단방향 의존성)
- **단일 책임**: 각 계층과 모듈의 명확한 역할 정의

## 📁 전체 구조

```
kr.hhplus.be.server/
├── api/                    # 🔴 Interface 계층
│   └── {domain}/
│       ├── controller/
│       └── dto/
├── domain/                 # 🔵 Domain 계층
│   └── {domain}/
│       ├── models/
│       ├── repositories/
│       ├── service/
│       └── infrastructure/
├── global/                 # 🌟 횡단 관심사
└── ServerApplication.kt
```

## 🎯 계층별 역할

### 1. API 계층
- **api/{domain}/controller/** - REST Controller
- **api/{domain}/dto/** - Request/Response DTO

### 2. Domain 계층
- **domain/{domain}/models/** - 순수 도메인 모델
- **domain/{domain}/repositories/** - Repository 인터페이스 (CQRS)
- **domain/{domain}/service/** - 비즈니스 로직
- **domain/{domain}/infrastructure/** - JPA 구현체

## 🔄 의존성 흐름

```
┌─────────────┐    depends on    ┌─────────────┐
│     API     │ ───────────────► │   Domain    │
│(Interface)  │                  │   (Core)    │
└─────────────┘                  └─────────────┘
                                        │
                                        │ implements
                                        ▼
                                ┌─────────────┐
                                │Infrastructure│
                                │ (Technical) │
                                └─────────────┘
```



## 🔧 마이그레이션 가이드

### 기존 코드에서 새 구조로 이동

1. **Controller & DTO** → `api/{domain}/`
2. **Entity & Service** → `domain/{domain}/`
3. **Repository 인터페이스** → `domain/{domain}/repositories/`
4. **JPA Repository & 구현체** → `domain/{domain}/infrastructure/`

