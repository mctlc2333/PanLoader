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
```

###🤝 Contributing
We welcome all contributions! Please read CONTRIBUTING.md to learn how to get involved.

###📜 License
This project is licensed under the MIT License.

###🙏 Acknowledgements
Fabric Loader – Launch core reference

ModLauncher – Bytecode transformation reference

MinecraftForge – Forge implementation reference
