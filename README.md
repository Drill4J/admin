> **DISCLAIMER**: We use Google Analytics for sending anonymous usage information such as agent's type, version
> after a successful agent registration. This information might help us to improve both Drill4J backend and client sides. It is used by the
> Drill4J team only and is not supposed for sharing with 3rd parties.
> You are able to turn off by set system property `analytic.disable = true` or send PATCH request `/api/analytic/toggle`

[![Check admin](https://github.com/Drill4J/admin/actions/workflows/check.yml/badge.svg)](https://github.com/Drill4J/admin/actions/workflows/check.yml)
[![Release](https://github.com/Drill4J/admin/actions/workflows/release.yml/badge.svg)](https://github.com/Drill4J/admin/actions/workflows/release.yml)
[![Build & publish drill artifacts](https://github.com/Drill4J/admin/actions/workflows/publish.yml/badge.svg)](https://github.com/Drill4J/admin/actions/workflows/publish.yml)
[![License](https://img.shields.io/github/license/Drill4J/admin)](LICENSE)
[![Visit the website at drill4j.github.io](https://img.shields.io/badge/visit-website-green.svg?logo=firefox)](https://drill4j.github.io/)
[![Telegram Chat](https://img.shields.io/badge/Chat%20on-Telegram-brightgreen.svg)](https://t.me/drill4j)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/Drill4J/admin)
![Docker Pulls](https://img.shields.io/docker/pulls/drill4j/admin)
![GitHub contributors](https://img.shields.io/github/contributors/Drill4J/admin)
![Lines of code](https://img.shields.io/tokei/lines/github/Drill4J/admin)
![YouTube Channel Views](https://img.shields.io/youtube/channel/views/UCJtegUnUHr0bO6icF1CYjKw?style=social)

# Drill4J Backend Server

The backend core part of Drill4J, based on Ktor framework.

Agents connect to this app(admin) by web sockets. 
And this app is extended by plugins (for example, [test2code](https://github.com/Drill4J/test2code-plugin)) by .jar files.

See more in [documentation](https://drill4j.github.io/docs/installation/drill-admin) 
and [Launch Parameters](https://drill4j.github.io/docs/configuration/launch-parameters)

## Links

Swagger 
- host/apidocs/index.html?url=./openapi.json
- example: http://localhost:8090/apidocs/index.html?url=./openapi.json

# Development
## Modules

- **core** uses:
  - [Drill API](https://github.com/Drill4J/drill) to communicate with agents and plugins.
  - [ORM for database](https://github.com/Drill4J/dsm)
  - swagger
- **analytics** - Google Analytics, see more in [here](analytics/README.md)
- **test-framework** for creating integration tests
- **tests** - integration tests by **test-framework**

## How to Run

- run database
- run application

see below how
### Database

#### Docker
```
docker run --name some-postgres -p 5432:5432 -e POSTGRES_PASSWORD=mysecretpassword -d postgres
```

custom configs see application.conf drill.database

#### Building Docker image locally
./gradlew tasks | grep docker

./gradlew jib

#### Embedded
set embeddedMode=true to use embedded database. 

To clean data from it use:

```shell script
./gradlew cleanData
```

### Application

Gradle command:
```shell script
./gradlew run
```

Default ports:
* HTTP: 8090
* Debug: 5006
