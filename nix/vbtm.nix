{
  lib,
  maven,
  graalvmPackages,
  buildGraalvmNativeImage,
}:

let
  version = "0.7.2";
  graalvm = graalvmPackages.graalvm-ce;
  fs = lib.fileset;

  jar = (maven.override { jdk_headless = graalvm; }).buildMavenPackage {
    pname = "verbatime-cli";
    inherit version;
    src = fs.toSource {
      root = ../.;
      fileset = fs.unions [
        ../pom.xml
        ../.mvn
        ../eclipse-formatter.xml
        ../modules/agent/pom.xml
        ../modules/jmc/pom.xml
        ../modules/format/pom.xml
        ../modules/format/src/main
        ../modules/cli/pom.xml
        ../modules/cli/src/main
      ];
    };
    mvnHash = "sha256-90p1F7bUFQYoCdMOJcgLcxl0fkshyEEQHVRlhDLv3wM=";
    mvnParameters = "-pl modules/cli -am";
    doCheck = false;
    installPhase = ''
      install -Dm644 modules/cli/target/verbatime-cli.jar $out/verbatime-cli.jar
    '';
  };
in
buildGraalvmNativeImage {
  pname = "vbtm";
  inherit version;
  src = "${jar}/verbatime-cli.jar";
  graalvmDrv = graalvm;
  extraNativeImageBuildArgs = [ "-O2" ];

  doInstallCheck = true;
  installCheckPhase = ''
    runHook preInstallCheck
    $out/bin/vbtm --help > /dev/null
    runHook postInstallCheck
  '';

  passthru = {
    inherit jar;
    # nix-update reads mvnHash through this attribute.
    inherit (jar) fetchedMavenDeps;
  };

  meta = {
    description = "Reads .vbtm recordings and shows where the time went";
    license = lib.licenses.asl20;
    mainProgram = "vbtm";
  };
}
