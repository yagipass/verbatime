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
    ajmx = {
      url = "github:yagipass/ajmx";
      inputs = {
        nixpkgs.follows = "nixpkgs";
        flake-parts.follows = "flake-parts";
        treefmt-nix.follows = "treefmt-nix";
        git-hooks.follows = "git-hooks";
      };
    };
    agent-skills = {
      url = "github:Kyure-A/agent-skills-nix";
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
        "aarch64-linux"
        "x86_64-linux"
      ];

      perSystem =
        {
          config,
          lib,
          pkgs,
          inputs',
          ...
        }:
        let
          agentLib = inputs.agent-skills.lib.agent-skills;
          skillSources = {
            ajmx = {
              path = inputs.ajmx;
              subdir = "skills";
            };
          };
          skillCatalog = agentLib.discoverCatalog skillSources;
          skillsHook = agentLib.mkShellHook {
            inherit pkgs;
            bundle = agentLib.mkBundle {
              inherit pkgs;
              selection = agentLib.selectSkills {
                catalog = skillCatalog;
                sources = skillSources;
                allowlist = agentLib.allowlistFor {
                  catalog = skillCatalog;
                  sources = skillSources;
                  enable = [ "ajmx" ];
                };
                skills = { };
              };
            };
            targets = {
              claude = agentLib.defaultLocalTargets.claude // {
                enable = true;
              };
              agents = agentLib.defaultLocalTargets.agents // {
                enable = true;
              };
            };
            quiet = true;
          };

          mkJavaShell =
            name: jdk:
            pkgs.mkShell {
              packages = [
                jdk
                (pkgs.maven.override { jdk_headless = jdk; })
                pkgs.mvnd
                inputs'.ajmx.packages.ajmx
              ]
              ++ config.pre-commit.settings.enabledPackages;

              JAVA_HOME = jdk.home;

              shellHook = ''
                ${config.pre-commit.shellHook}
                ${skillsHook}
                export SOURCE_DATE_EPOCH="$(git log -1 --format=%ct)"
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
          }
          // lib.mapAttrs' (name: lib.nameValuePair "devShell-${name}") config.devShells;

          treefmt = {
            projectRootFile = "flake.nix";
            flakeCheck = false;
            programs.nixfmt.enable = true;
          };

          pre-commit = {
            settings.excludes = [ "^CHANGELOG\\.md$" ];

            settings.hooks = {
              treefmt.enable = true;

              gitleaks.enable = true;

              convco.enable = true;

              end-of-file-fixer.enable = true;
              trim-trailing-whitespace.enable = true;
              check-merge-conflicts = {
                enable = true;
                args = [ "--assume-in-merge" ];
              };
              check-added-large-files = {
                enable = true;
                args = [ "--enforce-all" ];
              };
              detect-private-keys.enable = true;

              typos = {
                enable = true;
                files = "\\.md$";
              };
              markdownlint = {
                enable = true;
                settings.configuration = {
                  default = true;
                  MD013 = false;
                  MD031.list_items = false;
                  MD033 = false;
                  MD060 = false;
                };
              };

              jactionlint = {
                enable = true;
                package = pkgs.jactionlint;
                entry = "${lib.getExe pkgs.jactionlint} --shellcheck=${lib.getExe pkgs.shellcheck} --pyflakes=${lib.getExe pkgs.python3Packages.pyflakes}";
                files = "^\\.github/|(^|/)action\\.ya?ml$";
                pass_filenames = false;
              };
            };
          };

          devShells = rec {
            agent = mkJavaShell "agent" pkgs.jdk25;
            jmc = mkJavaShell "jmc" pkgs.jdk21;
            cli = mkJavaShell "cli" pkgs.graalvmPackages.graalvm-ce;
            docs = pkgs.mkShellNoCC {
              packages = [
                pkgs.nodejs_26
                pkgs.pnpm
              ]
              ++ config.pre-commit.settings.enabledPackages;

              shellHook = ''
                ${config.pre-commit.shellHook}
                echo "[verbatime-docs] node $(node --version)"
              '';
            };
            default = agent;
          };
        };
    };
}
