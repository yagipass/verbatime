{
  description = "Verbatime - lossless timeline method tracing for the JVM (agent + JMC plugin)";

  nixConfig = {
    extra-substituters = [ "https://verbatime.cachix.org" ];
    extra-trusted-public-keys = [
      "verbatime.cachix.org-1:Qqie2fyx4q6SYyjWuwmkr78fNAizxW1acBKeiAcHql8="
    ];
  };

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    git-hooks = {
      url = "github:cachix/git-hooks.nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      git-hooks,
    }:
    let
      systems = [
        "aarch64-darwin"
        "x86_64-darwin"
        "x86_64-linux"
        "aarch64-linux"
      ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f (import nixpkgs { inherit system; }));
      systemOf = pkgs: pkgs.stdenv.hostPlatform.system;

      preCommitFor =
        pkgs:
        git-hooks.lib.${systemOf pkgs}.run {
          src = ./.;
          hooks = {
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

            nixfmt.enable = true;

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
        };

      mkJavaShell =
        pkgs: name: jdk:
        let
          preCommit = self.checks.${systemOf pkgs}.pre-commit;
        in
        pkgs.mkShell {
          packages = [
            jdk
            (pkgs.maven.override { jdk_headless = jdk; })
            pkgs.git
          ]
          ++ preCommit.enabledPackages;

          JAVA_HOME = jdk.home;

          shellHook = ''
            ${preCommit.shellHook}
            echo "[verbatime-${name}] JAVA_HOME=$JAVA_HOME"
            echo "[verbatime-${name}] $(java -version 2>&1 | head -n1)"
          '';
        };

      mkDocsShell =
        pkgs:
        let
          preCommit = self.checks.${systemOf pkgs}.pre-commit;
        in
        pkgs.mkShell {
          packages = [
            pkgs.nodejs_24
            pkgs.git
          ]
          ++ preCommit.enabledPackages;

          shellHook = ''
            ${preCommit.shellHook}
            echo "[verbatime-docs] node $(node --version)"
          '';
        };
    in
    {
      checks = forAllSystems (pkgs: {
        pre-commit = preCommitFor pkgs;
      });

      devShells = forAllSystems (pkgs: rec {
        agent = mkJavaShell pkgs "agent" pkgs.jdk25;
        jmc = mkJavaShell pkgs "jmc" pkgs.jdk21;
        cli = mkJavaShell pkgs "cli" pkgs.graalvmPackages.graalvm-ce;
        docs = mkDocsShell pkgs;
        default = agent;
      });

      formatter = forAllSystems (pkgs: pkgs.nixfmt);
    };
}
