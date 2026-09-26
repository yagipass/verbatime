#!/bin/sh

set -eu

main() {
  case "$(uname -s)/$(uname -m)" in
    Darwin/arm64) platform=macos-arm64 ;;
    Linux/x86_64) platform=linux-amd64 ;;
    Linux/aarch64) platform=linux-arm64 ;;
    *)
      echo "vbtm: no prebuilt binary for $(uname -s) $(uname -m); use verbatime-cli.jar with Java 17+" >&2
      exit 1
      ;;
  esac

  install_dir=$HOME/.local/bin
  url=https://github.com/yagipass/verbatime/releases/latest/download/vbtm-$platform

  mkdir -p "$install_dir"
  tmp=$(mktemp -d "$install_dir/.vbtm-install.XXXXXX")
  trap 'rm -rf "$tmp"' EXIT
  trap 'exit 1' HUP INT TERM

  curl -fsSL -o "$tmp/vbtm" "$url"
  chmod +x "$tmp/vbtm"
  mv -f "$tmp/vbtm" "$install_dir/vbtm"
  echo "Installed vbtm to $install_dir/vbtm"

  case ":$PATH:" in
    *":$install_dir:"*) ;;
    *) echo "Add $install_dir to your PATH, for example: export PATH=\"$install_dir:\$PATH\"" ;;
  esac
}

main
