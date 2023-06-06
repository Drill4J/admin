> **DISCLAIMER**: We use Google Analytics for sending anonymous usage information such as agent's type, version
> after a successful agent registration. This information might help us to improve both Drill4J backend and client sides. It is used by the
> Drill4J team only and is not supposed for sharing with 3rd parties.
> You are able to turn off by set system property `analytic.disable = true` or send PATCH request `/api/analytic/toggle`

[![Check](https://github.com/Drill4J/admin/actions/workflows/check.yml/badge.svg)](https://github.com/Drill4J/admin/actions/workflows/check.yml)
[![Release](https://github.com/Drill4J/admin/actions/workflows/release.yml/badge.svg)](https://github.com/Drill4J/admin/actions/workflows/release.yml)
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

See more in [documentation](https://drill4j.github.io/docs/installation/drill-admin) and [Launch Parameters](https://drill4j.github.io/docs/configuration/launch-parameters)

## Modules

- **admin-core**: backend core part of Drill4J, admin component itself
- **test-framework**: framework for creating integration tests, runs lightweight version of admin application for testing purposes, used in test2code plugin
- **tests** - integration tests for **admin-core**, based on **test-framework**
