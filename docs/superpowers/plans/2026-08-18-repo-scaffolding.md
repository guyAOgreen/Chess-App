# Repository Scaffolding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a runnable project skeleton — PostgreSQL, a Spring Boot backend, and a React frontend that reports the backend's health — with no domain code.

**Architecture:** A monorepo holding one Maven backend service (`services/core`), one Vite frontend (`apps/web`), and a docker-compose file for local infrastructure (`infra/`). The backend exposes health through Spring Actuator; the frontend reaches it through the Vite dev proxy, which avoids CORS configuration entirely in development. A single Testcontainers integration test proves the datasource, JPA and Flyway are genuinely wired rather than merely declared.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Maven, PostgreSQL 18, Flyway, JUnit 5, Testcontainers, React, TypeScript, Vite, Yarn 1.x, Vitest, React Testing Library.

**Spec:** `docs/superpowers/specs/2026-08-18-scaffolding-design.md`

## Global Constraints

- Base package and Maven groupId: `com.chessapp`. Backend artifactId: `core`.
- Java version: **25**. Spring Boot parent version: **4.1.0**.
- PostgreSQL: **18**, database `chessapp`, user `chessapp`, password `chessapp` (local development only), published on host port **5433**.
- Yarn **classic** (1.22), not Berry.
- **No domain code.** No `Player`, `Game`, `GameImport`, PGN parsing, or chess rules library.
- **No empty domain packages.** The backend contains only `ChessAppApplication`. Feature packages arrive with the first real feature.
- Flyway is enabled but ships **zero migration files** — there is no schema yet. Hibernate `ddl-auto` is `validate`; Hibernate never generates schema.
- No `packages/`, no `services/notation-recognition/`, no root aggregator pom, no object storage.
- All work happens on branch `feat/repo-scaffolding` and ends in a pull request against `master`.

## Known blocker — read before starting

**The installed JDK on the development machine is 21; this project targets 25.**

Every step below that runs Maven will fail with an "invalid target release: 25" style error until a JDK 25 is installed. This was an explicit decision, not an oversight.

If JDK 25 is unavailable when a Maven step runs:

1. Do **not** downgrade `java.version` in the pom to make the build pass.
2. Do **not** claim the backend builds or the tests pass.
3. Record the exact command and the exact error, complete the remaining non-Maven steps, and report the gap plainly.

GitHub issue **#28 — "Verify the backend build on JDK 25"** tracks closing this gap.

Verify before running any Maven step:

```bash
java -version    # need 25; 21 means the backend cannot be verified
```

## File Structure

| File | Responsibility |
|---|---|
| `.gitignore` | Ignore build output, dependencies, IDE and OS files |
| `infra/docker-compose.yml` | Local PostgreSQL 18 |
| `services/core/pom.xml` | Backend build definition and dependencies |
| `services/core/src/main/java/com/chessapp/ChessAppApplication.java` | Spring Boot entry point |
| `services/core/src/main/resources/application.yml` | Datasource, JPA, Flyway and Actuator configuration |
| `services/core/src/main/resources/db/migration/.gitkeep` | Holds the Flyway location so it exists before the first migration |
| `services/core/src/test/java/com/chessapp/ApplicationContextIT.java` | Boots the context against a real PostgreSQL |
| `apps/web/vite.config.ts` | Build config, dev proxy to the backend, Vitest config |
| `apps/web/src/app/App.tsx` | Application shell |
| `apps/web/src/lib/api.ts` | Shared typed fetch helper |
| `apps/web/src/features/system-health/api/health.ts` | Health endpoint call and its types |
| `apps/web/src/features/system-health/hooks/useBackendHealth.ts` | Health request state |
| `apps/web/src/features/system-health/components/BackendHealthCard.tsx` | Renders health state (props-driven, no fetching) |
| `README.md` | How to run the three pieces |

**Note on a small refinement to the spec:** the spec said `src/features/` would be empty. This plan instead puts the health screen in `src/features/system-health/`, because a skeleton with an empty `features/` directory demonstrates nothing about the feature-first convention that `CLAUDE.md` requires. The health screen is the one piece of UI the skeleton has, so it doubles as the worked example. It contains no domain logic.

---

### Task 1: Repository hygiene and local database

**Files:**
- Create: `.gitignore`
- Create: `infra/docker-compose.yml`

**Interfaces:**
- Consumes: nothing.
- Produces: a PostgreSQL 18 instance on `localhost:5433`, database `chessapp`, user `chessapp`, password `chessapp`. Task 2 configures the backend against exactly these values.

- [ ] **Step 1: Confirm you are on the feature branch**

```bash
cd /c/Users/guygr/Development/repos/Chess-App
git branch --show-current
```

Expected: `feat/repo-scaffolding`. If not, run `git switch feat/repo-scaffolding`.

- [ ] **Step 2: Create `.gitignore`**

```gitignore
# Java / Maven
target/
*.class

# Node
node_modules/
dist/
.vite/
*.tsbuildinfo

# Environment
.env
.env.local
.env.*.local

# IDE
.idea/
*.iml
.vscode/

# OS
.DS_Store
Thumbs.db
```

- [ ] **Step 3: Create `infra/docker-compose.yml`**

```yaml
services:
  postgres:
    image: postgres:18
    container_name: chessapp-postgres
    environment:
      POSTGRES_DB: chessapp
      POSTGRES_USER: chessapp
      POSTGRES_PASSWORD: chessapp
    ports:
      # Published on 5433 because a native PostgreSQL install commonly occupies
      # 5432 on developer machines, and a client connecting from the host would
      # silently reach that instance instead of this container.
      - "5433:5432"
    volumes:
      - chessapp-postgres-data:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U chessapp -d chessapp"]
      interval: 5s
      timeout: 5s
      retries: 10

volumes:
  chessapp-postgres-data:
```

The volume is mounted at `/var/lib/postgresql` rather than `/var/lib/postgresql/data` because PostgreSQL 18's official image moved `PGDATA` into a version-numbered subdirectory. Mounting the parent works for both layouts.

- [ ] **Step 4: Start the database and verify it accepts connections**

```bash
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml ps
docker exec chessapp-postgres pg_isready -U chessapp -d chessapp
```

Expected: the service reports `healthy`, and `pg_isready` prints `accepting connections`. If the container restarts repeatedly, read `docker logs chessapp-postgres` before changing anything.

- [ ] **Step 5: Verify data survives the container being destroyed**

```bash
docker exec chessapp-postgres psql -U chessapp -d chessapp -c "create table scaffold_check (id int); insert into scaffold_check values (1);"
docker compose -f infra/docker-compose.yml down
docker compose -f infra/docker-compose.yml up -d --wait
docker exec chessapp-postgres psql -U chessapp -d chessapp -tAc "select count(*) from scaffold_check;"
```

Expected: the count is `1`. `down` removes the container but keeps the named volume, so the row can only survive if the volume is mounted at the path PostgreSQL 18 actually writes to. Use `down`, not `restart` — a restart reuses the same container, so its data would survive even with no volume at all, and the check would prove nothing.

`up --wait` blocks until the healthcheck passes, so no `sleep` is needed.

- [ ] **Step 6: Remove the check table**

```bash
docker exec chessapp-postgres psql -U chessapp -d chessapp -c "drop table scaffold_check;"
```

- [ ] **Step 7: Commit**

```bash
git add .gitignore infra/docker-compose.yml
git commit -m "chore: add gitignore and local PostgreSQL 18 compose file"
```

---

### Task 2: Backend service

**Files:**
- Create: `services/core/pom.xml`
- Create: `services/core/src/main/java/com/chessapp/ChessAppApplication.java`
- Create: `services/core/src/main/resources/application.yml`
- Create: `services/core/src/main/resources/db/migration/.gitkeep`
- Test: `services/core/src/test/java/com/chessapp/ApplicationContextIT.java`

**Interfaces:**
- Consumes: the PostgreSQL instance from Task 1 (`localhost:5433`, `chessapp`/`chessapp`/`chessapp`).
- Produces: an application listening on port 8080 serving `GET /actuator/health`, which returns JSON of the shape `{"status":"UP","components":{"db":{"status":"UP",...}}}` and answers **503** with the same JSON shape when a component is DOWN. Task 4's frontend depends on both that path and that shape.

**Every Maven step in this task requires JDK 25.** See "Known blocker" above.

- [ ] **Step 1: Write the failing integration test**

Create `services/core/src/test/java/com/chessapp/ApplicationContextIT.java`:

```java
package com.chessapp;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class ApplicationContextIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoadsAndDatabaseIsReachable() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.isValid(2)).isTrue();
        }
    }
}
```

`@ServiceConnection` makes Spring Boot point the datasource at the container automatically — no `@DynamicPropertySource` is needed. It requires the `spring-boot-testcontainers` dependency added in Step 3.

- [ ] **Step 2: Run the test to verify it fails**

```bash
mvn -f services/core/pom.xml verify
```

Expected: FAIL — there is no `pom.xml` yet, so Maven reports it cannot find the POM. That is the intended failure at this point.

- [ ] **Step 3: Create `services/core/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>

    <groupId>com.chessapp</groupId>
    <artifactId>core</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>chess-app-core</name>
    <description>Chess Prep core service</description>

    <properties>
        <java.version>25</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!--
          Spring Boot 4 splits autoconfiguration into per-technology modules.
          flyway-core alone puts the library on the classpath but leaves it
          inert: no migration runs and no schema history table is created.
          spring-boot-starter-flyway is what brings the autoconfiguration.
        -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-flyway</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>

        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <!--
          Testcontainers 2.x (managed by the Spring Boot 4.1 BOM) prefixes every
          module artifactId with "testcontainers-". The 1.x names
          (junit-jupiter, postgresql) are not managed by the BOM and fail
          resolution with a missing-version error.
        -->
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-postgresql</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>integration-test</goal>
                            <goal>verify</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

Testcontainers versions come from the Spring Boot parent's dependency management, so none are pinned here. The failsafe plugin is declared explicitly because the Spring Boot parent configures surefire (`*Test`) but not failsafe (`*IT`), and this project's integration tests use the `IT` suffix.

- [ ] **Step 4: Create the application entry point**

Create `services/core/src/main/java/com/chessapp/ChessAppApplication.java`:

```java
package com.chessapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ChessAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChessAppApplication.class, args);
    }
}
```

Do not create `game/`, `player/`, `shared/` or any other package. They arrive with their first real class.

- [ ] **Step 5: Create `services/core/src/main/resources/application.yml`**

```yaml
spring:
  application:
    name: chess-app-core
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5433/chessapp}
    username: ${SPRING_DATASOURCE_USERNAME:chessapp}
    password: ${SPRING_DATASOURCE_PASSWORD:chessapp}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

The defaults match `infra/docker-compose.yml` exactly, so a developer who has started the compose file needs no environment variables. `open-in-view` is disabled because leaving the persistence session open across view rendering hides lazy-loading problems.

- [ ] **Step 6: Create the Flyway migration directory**

```bash
mkdir -p services/core/src/main/resources/db/migration
touch services/core/src/main/resources/db/migration/.gitkeep
```

The directory exists but holds no migrations. Flyway logs "No migrations found" and proceeds. The `.gitkeep` is required because git does not track empty directories, and a missing location produces a confusing warning on every startup.

- [ ] **Step 7: Run the test to verify it passes**

```bash
mvn -f services/core/pom.xml verify
```

Expected: PASS. `ApplicationContextIT` starts a PostgreSQL 18 container, boots the full Spring context against it, and asserts the connection is valid. Docker must be running.

If this fails with `release version 25 not supported`, stop and consult the "Known blocker" section — do not change `java.version` in the pom.

Two checks are still available without JDK 25, and both should be run so the backend is not left entirely unverified:

1. Confirm the classes the test imports exist in the resolved jars:

```bash
jar tf ~/.m2/repository/org/testcontainers/testcontainers-junit-jupiter/*/testcontainers-junit-jupiter-*.jar | grep "junit/jupiter/Testcontainers.class"
jar tf ~/.m2/repository/org/testcontainers/testcontainers-postgresql/*/testcontainers-postgresql-*.jar | grep "containers/PostgreSQLContainer.class"
jar tf ~/.m2/repository/org/springframework/boot/spring-boot-testcontainers/*/spring-boot-testcontainers-*.jar | grep "service/connection/ServiceConnection.class"
```

2. Run the build with the release overridden **on the command line only**, which leaves the pom untouched:

```bash
mvn -f services/core/pom.xml verify -Djava.version=21
```

This exercises the Spring context, Hibernate, Flyway, Actuator and Testcontainers for real. A pass here means everything except the Java 25 toolchain itself is proven — say exactly that, and do not report it as "the build passes".

- [ ] **Step 8: Verify the running application serves health**

With the compose database from Task 1 running:

```bash
mvn -f services/core/pom.xml spring-boot:run
```

In another shell:

```bash
curl -s http://localhost:8080/actuator/health
```

Expected: `{"status":"UP","components":{"db":{"status":"UP","details":{"database":"PostgreSQL",...}},...}}`

Stop the application afterwards.

- [ ] **Step 9: Commit**

```bash
git add services/core
git commit -m "feat: add Spring Boot core service with PostgreSQL and Flyway"
```

---

### Task 3: Frontend project skeleton

**Files:**
- Create: `apps/web/` (generated by Vite, then trimmed)
- Create: `apps/web/src/app/App.tsx`
- Create: `apps/web/src/app/App.test.tsx`
- Create: `apps/web/src/test/setup.ts`
- Modify: `apps/web/vite.config.ts`
- Modify: `apps/web/src/main.tsx`
- Modify: `apps/web/package.json`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `App` — a default-exported React component rendering the application shell, importable as `import App from './app/App'`. Task 4 modifies this component to include the health card.

- [ ] **Step 1: Generate the Vite project**

```bash
yarn create vite apps/web --template react-ts
cd apps/web
yarn install
```

- [ ] **Step 2: Add test and formatting tooling**

```bash
yarn add --dev vitest jsdom @testing-library/react @testing-library/jest-dom @testing-library/dom @testing-library/user-event prettier
```

`@testing-library/dom` is a peer dependency of `@testing-library/jest-dom`. Yarn 1 does not install peer dependencies automatically, so omitting it fails at test time with `Cannot find package '@testing-library/dom'`.

- [ ] **Step 3: Create the feature-first directory structure**

From `apps/web`:

```bash
mkdir -p src/app src/features src/components/shared src/hooks/shared src/lib src/test
```

`src/features`, `src/components/shared` and `src/hooks/shared` stay empty until Task 4 and beyond; git will not track the empty ones, which is fine — they are a convention, not an artefact.

- [ ] **Step 4: Create the test setup file**

Create `src/test/setup.ts`:

```ts
import '@testing-library/jest-dom/vitest';
```

- [ ] **Step 5: Configure Vite, the dev proxy and Vitest**

Replace `vite.config.ts` with:

```ts
/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const BACKEND_URL = 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': { target: BACKEND_URL, changeOrigin: true },
      '/actuator': { target: BACKEND_URL, changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
  },
});
```

Proxying `/api` and `/actuator` to the backend means the frontend uses same-origin relative paths in development, so no CORS configuration is needed on the backend.

- [ ] **Step 6: Add the test scripts to `package.json`**

In the `scripts` block, add:

```json
"test": "vitest run",
"test:watch": "vitest",
"format": "prettier --write ."
```

Keep the generated `dev`, `build`, `lint` and `preview` scripts as they are.

- [ ] **Step 7: Write the failing shell test**

Create `src/app/App.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from './App';

describe('App', () => {
  it('renders the application name', () => {
    render(<App />);
    expect(screen.getByRole('heading', { name: /chess prep/i })).toBeInTheDocument();
  });
});
```

- [ ] **Step 8: Run the test to verify it fails**

```bash
yarn test
```

Expected: FAIL — `Failed to resolve import "./App"`, because `src/app/App.tsx` does not exist yet.

- [ ] **Step 9: Create the application shell**

Create `src/app/App.tsx`:

```tsx
export default function App() {
  return (
    <main>
      <h1>Chess Prep</h1>
    </main>
  );
}
```

- [ ] **Step 10: Point the entry point at the new shell**

Replace `src/main.tsx` with:

```tsx
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './app/App';
import './index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
```

- [ ] **Step 11: Remove the Vite template leftovers**

```bash
rm -f src/App.tsx src/App.css src/assets/react.svg
```

Leave `src/index.css` and `public/vite.svg` in place.

- [ ] **Step 12: Run the tests and the build to verify they pass**

```bash
yarn test
yarn build
```

Expected: the test passes and the build succeeds. If `yarn build` reports a TypeScript error about the removed `App.tsx`, an import of it survives somewhere — find and remove it rather than restoring the file.

- [ ] **Step 13: Commit**

```bash
cd /c/Users/guygr/Development/repos/Chess-App
git add apps/web
git commit -m "feat: add React frontend skeleton with Vitest and dev proxy"
```

---

### Task 4: Backend health on screen

**Files:**
- Create: `apps/web/src/lib/api.ts`
- Create: `apps/web/src/features/system-health/api/health.ts`
- Create: `apps/web/src/features/system-health/hooks/useBackendHealth.ts`
- Create: `apps/web/src/features/system-health/components/BackendHealthCard.tsx`
- Test: `apps/web/src/features/system-health/components/BackendHealthCard.test.tsx`
- Modify: `apps/web/src/app/App.tsx`

**Interfaces:**
- Consumes: `GET /actuator/health` from Task 2, and the `App` component from Task 3.
- Produces:
  - `getJson<T>(path: string): Promise<ApiResponse<T>>` where `ApiResponse<T> = { ok: boolean; status: number; data: T }`
  - `fetchBackendHealth(): Promise<BackendHealth>` where `BackendHealth = { status: string; components?: Record<string, { status: string }> }`
  - `useBackendHealth(): HealthState` where `HealthState` is `{ kind: 'loading' } | { kind: 'ready'; health: BackendHealth } | { kind: 'unreachable'; message: string }`
  - `BackendHealthCard({ state }: { state: HealthState })` — presentational, does no fetching

- [ ] **Step 1: Write the failing component test**

Create `src/features/system-health/components/BackendHealthCard.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { BackendHealthCard } from './BackendHealthCard';

describe('BackendHealthCard', () => {
  it('reports that the backend is being contacted', () => {
    render(<BackendHealthCard state={{ kind: 'loading' }} />);
    expect(screen.getByText(/contacting the backend/i)).toBeInTheDocument();
  });

  it('reports the backend status and each component', () => {
    render(
      <BackendHealthCard
        state={{
          kind: 'ready',
          health: { status: 'UP', components: { db: { status: 'UP' } } },
        }}
      />,
    );
    expect(screen.getByText(/backend: UP/i)).toBeInTheDocument();
    expect(screen.getByText(/db: UP/i)).toBeInTheDocument();
  });

  it('reports why the backend could not be reached', () => {
    render(<BackendHealthCard state={{ kind: 'unreachable', message: 'Failed to fetch' }} />);
    expect(screen.getByText(/backend unreachable/i)).toBeInTheDocument();
    expect(screen.getByText(/failed to fetch/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd apps/web && yarn test
```

Expected: FAIL — `Failed to resolve import "./BackendHealthCard"`.

- [ ] **Step 3: Create the shared fetch helper**

Create `src/lib/api.ts`:

```ts
export interface ApiResponse<T> {
  ok: boolean;
  status: number;
  data: T;
}

/**
 * Reads the JSON body regardless of status, because some endpoints
 * (Actuator health, validation failures) carry meaning in the body of a
 * non-2xx response. Callers decide what a given status means.
 */
export async function getJson<T>(path: string): Promise<ApiResponse<T>> {
  const response = await fetch(path, { headers: { Accept: 'application/json' } });
  const data = (await response.json()) as T;
  return { ok: response.ok, status: response.status, data };
}
```

- [ ] **Step 4: Create the health API module**

Create `src/features/system-health/api/health.ts`:

```ts
import { getJson } from '../../../lib/api';

export interface BackendHealth {
  status: string;
  components?: Record<string, { status: string }>;
}

/**
 * Actuator answers 503 with a well-formed body when a component is DOWN,
 * so the body is used whether or not the status is 2xx.
 */
export async function fetchBackendHealth(): Promise<BackendHealth> {
  const response = await getJson<BackendHealth>('/actuator/health');
  return response.data;
}
```

- [ ] **Step 5: Create the hook**

Create `src/features/system-health/hooks/useBackendHealth.ts`:

```ts
import { useEffect, useState } from 'react';
import { fetchBackendHealth, type BackendHealth } from '../api/health';

export type HealthState =
  | { kind: 'loading' }
  | { kind: 'ready'; health: BackendHealth }
  | { kind: 'unreachable'; message: string };

export function useBackendHealth(): HealthState {
  const [state, setState] = useState<HealthState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;

    fetchBackendHealth()
      .then((health) => {
        if (active) {
          setState({ kind: 'ready', health });
        }
      })
      .catch((error: unknown) => {
        if (active) {
          const message = error instanceof Error ? error.message : String(error);
          setState({ kind: 'unreachable', message });
        }
      });

    return () => {
      active = false;
    };
  }, []);

  return state;
}
```

The `active` flag stops the component updating after unmount, which React's StrictMode double-mounting in development would otherwise surface as a warning.

- [ ] **Step 6: Create the card component**

Create `src/features/system-health/components/BackendHealthCard.tsx`:

```tsx
import type { HealthState } from '../hooks/useBackendHealth';

export function BackendHealthCard({ state }: { state: HealthState }) {
  if (state.kind === 'loading') {
    return <p>Contacting the backend…</p>;
  }

  if (state.kind === 'unreachable') {
    return (
      <section>
        <p>Backend unreachable</p>
        <p>{state.message}</p>
      </section>
    );
  }

  const components = Object.entries(state.health.components ?? {});

  return (
    <section>
      <p>Backend: {state.health.status}</p>
      <ul>
        {components.map(([name, component]) => (
          <li key={name}>
            {name}: {component.status}
          </li>
        ))}
      </ul>
    </section>
  );
}
```

- [ ] **Step 7: Run the test to verify it passes**

```bash
yarn test
```

Expected: PASS — all four tests (three here, one from Task 3).

- [ ] **Step 8: Wire the card into the shell**

Replace `src/app/App.tsx` with:

```tsx
import { BackendHealthCard } from '../features/system-health/components/BackendHealthCard';
import { useBackendHealth } from '../features/system-health/hooks/useBackendHealth';

export default function App() {
  const health = useBackendHealth();

  return (
    <main>
      <h1>Chess Prep</h1>
      <BackendHealthCard state={health} />
    </main>
  );
}
```

- [ ] **Step 9: Run the tests and build again**

```bash
yarn test
yarn build
```

Expected: PASS. The Task 3 shell test still passes — it asserts only on the heading, and `fetch` failing in jsdom lands the card in the `unreachable` state, which renders without throwing.

- [ ] **Step 10: Verify the wire end to end**

Start all three pieces:

```bash
docker compose -f ../../infra/docker-compose.yml up -d
mvn -f ../../services/core/pom.xml spring-boot:run   # requires JDK 25
yarn dev
```

Open the URL Vite prints. Expected: the page shows "Backend: UP" and "db: UP".

If JDK 25 is unavailable, run only the database and Vite. Expected then: "Backend unreachable". That still proves the proxy path and the error state, but **not** the success path — say so rather than implying the wire is verified.

- [ ] **Step 11: Commit**

```bash
cd /c/Users/guygr/Development/repos/Chess-App
git add apps/web
git commit -m "feat: show backend health in the web app"
```

---

### Task 5: Documentation and pull request

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: everything from Tasks 1–4.
- Produces: a pull request against `master`.

- [ ] **Step 1: Replace `README.md`**

```markdown
# Chess Prep

Chess game storage, scoresheet digitisation and opponent preparation.

See [CONTEXT.md](CONTEXT.md) for product direction and architecture, and
[CLAUDE.md](CLAUDE.md) for development conventions.

## Layout

    apps/web/         React + TypeScript + Vite frontend
    services/core/    Java + Spring Boot backend
    infra/            local infrastructure (docker-compose)
    docs/             specs, plans and decision records

## Prerequisites

- JDK 25
- Maven 3.9+
- Node 22+ and Yarn 1.x
- Docker

## Running locally

Start the database:

    docker compose -f infra/docker-compose.yml up -d

Start the backend on port 8080:

    mvn -f services/core/pom.xml spring-boot:run

Start the frontend:

    cd apps/web && yarn install && yarn dev

The frontend proxies `/api` and `/actuator` to the backend, so no CORS
configuration is needed in development. The home page reports the backend's
health, which is the quickest check that all three pieces are talking.

## Tests

    mvn -f services/core/pom.xml verify     # needs Docker for Testcontainers
    cd apps/web && yarn test

## Current state

This is the project skeleton only. There is no domain code yet — no games,
players or PGN handling. See the open issues and milestones for what comes
next.
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: document how to run the project locally"
```

- [ ] **Step 3: Run the full verification sweep**

```bash
docker compose -f infra/docker-compose.yml up -d
mvn -f services/core/pom.xml verify
cd apps/web && yarn test && yarn build && cd ../..
```

Record the actual result of each command. If the Maven command could not run because JDK 25 is absent, record that verbatim — it belongs in the pull request description.

- [ ] **Step 4: Push the branch**

```bash
git push -u origin feat/repo-scaffolding
```

- [ ] **Step 5: Open the pull request**

```bash
gh pr create --base master --title "Repository scaffolding" --body-file -
```

The body must state:

- what the branch adds (backend, frontend, infrastructure, spec and plan);
- that there is deliberately no domain code;
- the exact verification commands run and their real outcomes;
- if JDK 25 was unavailable, that the backend is unverified, linking issue #28.

Do not describe the backend as working if it was never built.

- [ ] **Step 6: Report the pull request URL and the verification gaps**

---

## Self-Review

**Spec coverage:**

| Spec requirement | Task |
|---|---|
| Repository layout | 1, 2, 3 |
| `.gitignore`, docs committed | 1 (docs were committed with the spec) |
| Maven, Spring Boot 4.1.0, Java 25 | 2 |
| Backend dependency set | 2 |
| Only `ChessAppApplication`, no empty packages | 2 |
| Actuator health, no hand-written controller | 2 |
| `application.yml`, env-var defaults matching compose | 2 |
| Flyway enabled, zero migrations | 2 |
| `ApplicationContextIT` with Testcontainers | 2 |
| Vite + React + TS, Yarn classic | 3 |
| Feature-first `src/` structure | 3, 4 |
| `src/lib/api.ts` | 4 |
| Health screen | 4 |
| Vite dev proxy | 3 |
| Vitest + RTL smoke test | 3, 4 |
| Prettier | 3 |
| PostgreSQL 18 compose, named volume | 1 |
| No MinIO | not created — correct |
| Success criteria 1–4 | 1, 2, 3, 4, 5 |

Linting comes with the Vite `react-ts` template and needs no separate task. Current templates ship **oxlint** (`.oxlintrc.json`, `yarn lint`) rather than ESLint; the spec named ESLint, but the template's own choice is the one to keep.

**Deviation from spec:** the health UI lives under `src/features/system-health/` rather than leaving `src/features/` empty. Rationale is in the File Structure section.

**Placeholder scan:** no TBD, TODO, "handle edge cases", or "similar to Task N" entries. Every code step carries the actual code.

**Type consistency:** `HealthState`, `BackendHealth`, `ApiResponse<T>`, `getJson`, `fetchBackendHealth`, `useBackendHealth` and `BackendHealthCard` are defined in Task 4 Steps 3–6 and used with matching shapes in the Task 4 Step 1 test and Task 4 Step 8. `App` is default-exported in Task 3 Step 9 and imported as a default in Task 3 Steps 7 and 10.
