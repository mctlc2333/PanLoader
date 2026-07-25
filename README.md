# 🍽️ PanLoader

> A Minecraft mod loader that natively supports both Forge and Fabric mods.

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://adoptium.net/)
[![Build](https://github.com/mctlc2333/PanLoader/actions/workflows/build.yml/badge.svg)](https://github.com/mctlc2333/PanLoader/actions/workflows/build.yml)

## 📖 Introduction

**PanLoader** is a Minecraft mod loader designed from the ground up to **natively support both Forge and Fabric mods** in the same game environment, without requiring compatibility layers or bridges.

## ✨ Features

- 🔀 **Dual mod support** – Simultaneously loads Forge and Fabric mods
- 🏗️ **Classloader isolation** – Separate classloaders to isolate mod environments
- 🕵️ **Auto-detection** – Automatically detects mod type and routes to appropriate environment
- ⚡ **Lightweight** – Built on Fabric Loader’s launch core for minimal overhead
- 📦 **No bridges required** – Native support without extra compatibility layers

## 🏗️ Architecture Overview
PanLoader
├── Master Dispatcher → Parses launch args and coordinates loading
├── Mod Detector → Scans and identifies mod types
├── Environment Factory → Creates isolated Forge/Fabric environments
└── Containers → Forge container / Fabric container load independently

text

## 🚀 Quick Start

### Requirements

- Java 17 or later
- Minecraft 1.20.1+

### Installation

1. Download the latest `PanLoader.jar` from [Releases](https://github.com/yourusername/PanLoader/releases)
2. Place it in your Minecraft game root directory (same level as `.minecraft`)
3. Run `java -jar PanLoader.jar --gameDir /path/to/.minecraft --version game-version`

### Usage Example

```bash
java -jar PanLoader.jar --gameDir D:/Minecraft/.minecraft --version 1.20.1


##🤝 Contributing
We welcome all contributions! Please read CONTRIBUTING.md to learn how to get involved.

##📜 License
This project is licensed under the MIT License.

##🙏 Acknowledgements
Fabric Loader – Launch core reference

ModLauncher – Bytecode transformation reference

MinecraftForge – Forge implementation reference

text

---

### 2. `CONTRIBUTING.md`

```markdown
# 🤝 Contributing to PanLoader

First of all, thank you for considering contributing to PanLoader! We welcome all forms of contributions, including bug reports, code contributions, documentation improvements, and more.

## 📋 Code of Conduct

Please read and follow our [Code of Conduct](CODE_OF_CONDUCT.md).

## 🐛 Reporting Bugs

Use GitHub Issues to report bugs, and please fill out the [Bug Report template](.github/ISSUE_TEMPLATE/bug_report.md).

## 💡 Suggesting Features

Use GitHub Issues to suggest features, and please fill out the [Feature Request template](.github/ISSUE_TEMPLATE/feature_request.md).

## 🔧 Setting Up Your Development Environment

### Prerequisites

- JDK 17 or higher
- Git
- Gradle (included via Gradle Wrapper)

### Cloning and Building

```bash
git clone https://github.com/yourusername/PanLoader.git
cd PanLoader
./gradlew build
./gradlew test
##📝 Coding Standards
Follow the Google Java Style Guide.

All public APIs must have JavaDoc comments.

Ensure all tests pass before submitting.

##🔀 Submitting a Pull Request
Fork the repository.

Create your feature branch (git checkout -b feature/amazing-feature).

Commit your changes (git commit -m 'Add some amazing feature').

Push to the branch (git push origin feature/amazing-feature).

Open a Pull Request.

PR Requirements
PR title should clearly describe the change.

All CI checks must pass.

At least one maintainer review is required.

##📧 Contact
If you have any questions, you can reach us via:

GitHub Issues

Email: [project-email@example.com]

text

---

### 3. `CODE_OF_CONDUCT.md`

```markdown
# Contributor Covenant Code of Conduct

## Our Pledge

We as members, contributors, and leaders pledge to make participation in our community a harassment-free experience for everyone, regardless of age, body size, visible or invisible disability, ethnicity, sex characteristics, gender identity and expression, level of experience, education, socio-economic status, nationality, personal appearance, race, religion, or sexual identity and orientation.

## Our Standards

Examples of behavior that contributes to a positive environment:

- Using welcoming and inclusive language
- Being respectful of differing viewpoints and experiences
- Gracefully accepting constructive criticism
- Focusing on what is best for the community
- Showing empathy towards other community members

Examples of unacceptable behavior:

- The use of sexualized language or imagery, and unwelcome sexual attention or advances
- Trolling, insulting/derogatory comments, and personal or political attacks
- Public or private harassment
- Publishing others' private information without explicit permission
- Other conduct which could reasonably be considered inappropriate in a professional setting

## Our Responsibilities

Project maintainers are responsible for clarifying the standards of acceptable behavior and are expected to take appropriate and fair corrective action in response to any instances of unacceptable behavior.

## Scope

This Code of Conduct applies within all project spaces, and also when an individual is representing the project or its community in public spaces.

## Enforcement

Instances of abusive, harassing, or otherwise unacceptable behavior may be reported by contacting the project team at [INSERT EMAIL ADDRESS]. All complaints will be reviewed and investigated.

## Attribution

This Code of Conduct is adapted from the [Contributor Covenant][homepage], version 1.4, available at [http://contributor-covenant.org/version/1/4][version].

[homepage]: http://contributor-covenant.org
[version]: http://contributor-covenant.org/version/1/4/
