package com.panloader;

import com.panloader.core.LaunchHandler;
import com.panloader.core.MixinManager;
import com.panloader.core.ModManager;
import com.panloader.core.ModsFolderScanner;
import com.panloader.api.ModMetadata;
import com.panloader.api.PanMod;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class PanLoaderBootstrap {

    private static final String VERSION = "0.1.0";
    private static final String DEFAULT_GAME_DIR = System.getProperty("user.dir") + "/.minecraft";
    private static final String DEFAULT_MC_VERSION = "1.20.1";

    private static ModManager modManager;
    private static MixinManager mixinManager;
    private static Instrumentation instrumentation;

    public static void premain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;
        System.out.println("[PanLoader] Agent loaded (premain)");
    }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  PanLoader v" + VERSION);
        System.out.println("  A unified Forge + Fabric mod loader");
        System.out.println("========================================");

        String gameDir = getArg(args, "gameDir", System.getProperty("panloader.gameDir", DEFAULT_GAME_DIR));
        String mcVersion = getArg(args, "version", System.getProperty("panloader.version", DEFAULT_MC_VERSION));

        System.out.println("[PanLoader] Game directory: " + gameDir);
        System.out.println("[PanLoader] MC version: " + mcVersion);

        try {
            Path gameDirPath = Paths.get(gameDir);
            Path modsPath = gameDirPath.resolve("mods");

            modManager = new ModManager();
            mixinManager = new MixinManager(instrumentation);

            ModsFolderScanner scanner = new ModsFolderScanner(modsPath);
            List<ModsFolderScanner.ModCandidate> candidates = scanner.scan();

            System.out.println("[PanLoader] Found " + candidates.size() + " mod candidates in mods/");

            int loaded = 0;
            for (ModsFolderScanner.ModCandidate candidate : candidates) {
                try {
                    switch (candidate.getType()) {
                        case PANLOADER:
                            PanMod mod = modManager.loadMod(candidate.getJarPath());
                            if (mod != null) {
                                ModMetadata meta = modManager.getModMetadata(mod.getModId());
                                if (meta != null) {
                                    System.out.println("  [+] " + meta.getName() + " v" + meta.getVersion() + " (PanLoader)");
                                } else {
                                    System.out.println("  [+] " + mod.getModName() + " v" + mod.getModVersion() + " (PanLoader)");
                                }
                                mixinManager.registerMixinConfigsFromMod(candidate.getJarPath());
                                loaded++;
                            }
                            break;

                        case FABRIC:
                            System.out.println("  [~] Fabric mod detected: " + candidate.getJarPath().getFileName()
                                    + " (delegating to Fabric Loader)");
                            break;

                        case FORGE:
                            System.out.println("  [~] Forge mod detected: " + candidate.getJarPath().getFileName()
                                    + " (delegating to Forge loader)");
                            break;

                        case UNKNOWN:
                            System.out.println("  [?] Unknown mod type: " + candidate.getJarPath().getFileName());
                            break;
                    }
                } catch (Exception e) {
                    System.err.println("[PanLoader] Failed to load " + candidate.getJarPath().getFileName()
                            + ": " + e.getMessage());
                }
            }

            System.out.println("[PanLoader] Loaded " + loaded + " PanLoader native mods.");

            LaunchHandler launcher = new LaunchHandler(mcVersion, gameDir, modManager, mixinManager);
            launcher.launch(args);

        } catch (Exception e) {
            System.err.println("[PanLoader] Fatal error during startup:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String getArg(String[] args, String key, String defaultValue) {
        for (String arg : args) {
            if (arg.startsWith("--" + key + "=")) {
                return arg.substring(key.length() + 3);
            }
            if (arg.equals("--" + key)) {
                int idx = java.util.Arrays.asList(args).indexOf(arg);
                if (idx + 1 < args.length) {
                    return args[idx + 1];
                }
            }
        }
        return defaultValue;
    }

    public static ModManager getModManager() {
        return modManager;
    }

    public static MixinManager getMixinManager() {
        return mixinManager;
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }
}
