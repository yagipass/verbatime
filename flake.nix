{
  description = "Verbatime - lossless timeline method tracing for the JVM (agent, JMC plugin, vbtm CLI)";

  nixConfig = {
    extra-substituters = [ "https://verbatime.cachix.org" ];
    extra-trusted-public-keys = [
      "verbatime.cachix.org-1:Qqie2fyx4q6SYyjWuwmkr78fNAizxW1acBKeiAcHql8="
    ];
  };

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-parts = {
      url = "github:hercules-ci/flake-parts";
      inputs.nixpkgs-lib.follows = "nixpkgs";
    };
    treefmt-nix = {
      url = "github:numtide/treefmt-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    git-hooks = {
      url = "github:cachix/git-hooks.nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    inputs:
    inputs.flake-parts.lib.mkFlake { inherit inputs; } {
      imports = [
        inputs.treefmt-nix.flakeModule
        inputs.git-hooks.flakeModule
      ];

      systems = [
        "aarch64-darwin"
        "x86_64-linux"
        "aarch64-linux"
      ];

      perSystem =
        { config, pkgs, ... }:
        let
          mkJavaShell =
            name: jdk:
            pkgs.mkShell {
              packages = [
                jdk
                (pkgs.maven.override { jdk_headless = jdk; })
                pkgs.git
              ]
              ++ config.pre-commit.settings.enabledPackages;

              JAVA_HOME = jdk.home;

              shellHook = ''
                ${config.pre-commit.installationScript}
                echo "[verbatime-${name}] JAVA_HOME=$JAVA_HOME"
                echo "[verbatime-${name}] $(java -version 2>&1 | head -n1)"
              '';
            };
        in
        {
          packages = rec {
            vbtm = pkgs.callPackage ./nix/vbtm.nix { };
            default = vbtm;
          };

          checks = {
            inherit (config.packages) vbtm;
          };

          treefmt = {
            projectRootFile = "flake.nix";
            programs.nixfmt.enable = true;
          };

          pre-commit.settings.hooks = {
            treefmt = {
              enable = true;
              package = config.treefmt.build.wrapper;
            };

            gitleaks = {
              enable = true;
              name = "gitleaks";
              entry = "${pkgs.gitleaks}/bin/gitleaks git --pre-commit --staged --redact --verbose";
              pass_filenames = false;
            };

            convco.enable = true;

            end-of-file-fixer.enable = true;
            trim-trailing-whitespace.enable = true;
            check-merge-conflicts.enable = true;
            check-added-large-files.enable = true;
            detect-private-keys.enable = true;

            typos = {
              enable = true;
              files = "\\.md$";
            };
            markdownlint = {
              enable = true;
              excludes = [ "^CHANGELOG\\.md$" ];
              settings.configuration = {
                default = true;
                MD013 = false;
                MD031.list_items = false;
                MD033 = false;
                MD060 = false;
              };
            };
          };

          devShells = rec {
            agent = mkJavaShell "agent" pkgs.jdk25;
            jmc = mkJavaShell "jmc" pkgs.jdk21;
            cli = mkJavaShell "cli" pkgs.graalvmPackages.graalvm-ce;
            docs = pkgs.mkShell {
              packages = [
                pkgs.nodejs_24
                pkgs.git
              ]
              ++ config.pre-commit.settings.enabledPackages;

              shellHook = ''
                ${config.pre-commit.installationScript}
                echo "[verbatime-docs] node $(node --version)"
              '';
            };
            default = agent;
          };
        };
    };
}
