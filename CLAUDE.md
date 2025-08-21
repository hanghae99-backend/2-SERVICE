# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

### Build and Test
- `./gradlew build` - Build the project
- `./gradlew test` - Run all tests 
- `./gradlew test --tests "*ConcurrencyTest"` - Run concurrency tests specifically
- `./gradlew test --tests "*.integration.*"` - Run integration tests
- `./gradlew bootRun` - Run the application locally

### Development Environment
- `docker-compose up -d` - Start MySQL (port 13306) and Redis (port 16379) containers
- `docker-compose down` - Stop containers
- Database: MySQL 8.0 at localhost:13306, user: application/application, database: hhplus
- Redis: localhost:16379

### Useful Development Commands
- `./gradlew clean` - Clean build artifacts
- `./gradlew check` - Run all checks including tests
- Run specific test class: `./gradlew test --tests "TokenServiceTest"`

## Architecture Overview

This is a concert reservation system built with Spring Boot and Kotlin, following Domain-Driven Design (DDD) principles.

### Key Architectural Patterns

**Domain-Driven Design Structure:**
- `domain/` - Core business logic organized by bounded contexts (auth, balance, concert, payment, reservation, user)
- `api/` - Application layer with controllers and use cases
- `global/` - Cross-cutting concerns (config, exception handling, caching, locks)

**Bounded Contexts:**
- **Auth**: Token-based queue management system using Redis
- **Balance**: User point/balance management with history tracking
- **Concert**: Concert and seat management with view counting
- **Payment**: Payment processing with status tracking
- **Reservation**: Seat reservation with expiration handling
- **User**: User management with validation

### Critical Domain Patterns

**Event-Driven Architecture:**
- Domain events defined in `domain/common/DomainEvents.kt`
- Event handlers in each domain's `event/handler/` package
- Async event processing with Spring's `@EventListener`

**Distributed Locking:**
- Redis-based distributed locking in `global/lock/DistributedLock.kt`
- Supports multiple strategies: SIMPLE, SPIN, PUB_SUB
- Used via `@LockGuard` annotation for automatic locking

**Repository Pattern:**
- Each domain has repositories in `repositories/` package
- Infrastructure implementations in `infrastructure/` package using JPA
- Separation between domain interfaces and JPA implementations

**Exception Handling:**
- Domain-specific exceptions in each `exception/` package
- Global exception handler in `global/exception/GlobalExceptionHandler.kt`
- Consistent error response format

### Database and Caching

**Database:**
- MySQL 8.0 with Hibernate/JPA
- Schema created via `ddl-auto: create-drop` in local profile
- Initial data loaded from `src/main/resources/data.sql`

**Caching Strategy:**
- Redis for distributed caching and session storage
- Concert popularity caching with scheduled refresh
- Token queue management in Redis

**Transaction Management:**
- Uses Spring's `@Transactional` with specific isolation levels for concurrency
- Optimistic locking patterns in domain entities
- Distributed locking for critical sections

### Testing Strategy

**Test Structure:**
- Unit tests: `domain/*/service/*Test.kt`
- Integration tests: `api/*/integration/*Test.kt`
- Concurrency tests: `api/*/concurrency/*Test.kt`
- Controller tests: `api/*/controller/*Test.kt`

**Testing Technologies:**
- Kotest for assertions and property-based testing
- MockK for mocking
- Spring Boot Test with TestContainers
- H2 in-memory database for tests

### Key Implementation Notes

**Concurrency Handling:**
- Critical operations use distributed locks with Redis
- Queue management prevents race conditions in token processing
- Seat reservation uses optimistic locking with retry mechanisms

**Queue System:**
- Token-based waiting queue implemented with Redis sorted sets
- Automatic token activation and expiration
- Position tracking and estimated wait times

**Monitoring and Observability:**
- Structured logging with correlation IDs
- Performance metrics for lock operations
- Event processing tracking and failure handling

### Development Guidelines

**Domain Modeling:**
- Rich domain models with behavior, not anemic data classes
- Domain events for cross-context communication
- Value objects for primitive obsession prevention

**Error Handling:**
- Use domain-specific exceptions with error codes
- Global exception handler provides consistent API responses
- Validation at both controller and domain levels

**Performance Considerations:**
- Distributed locking should be used sparingly and with short timeouts
- Cache frequently accessed data (concert popularity, user balances)
- Use appropriate indexing for database queries (check existing schema)

**Security:**
- Token-based authentication with Redis session storage
- Input validation using Bean Validation annotations
- SQL injection prevention through JPA/Hibernate

### Configuration Profiles
- `local` - Default profile for local development
- `test` - Test configuration with H2 database
- Environment-specific configuration in `application.yml`