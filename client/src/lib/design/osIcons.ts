import Almalinux from "~icons/devicon/almalinux";
import Android from "~icons/logos/android-icon";
import Apple from "~icons/logos/apple";
import Archlinux from "~icons/logos/archlinux";
import Artixlinux from "~icons/devicon/artixlinux";
import Centos from "~icons/devicon/centos";
import Debian from "~icons/logos/debian";
import Elementary from "~icons/logos/elementary";
import Fedora from "~icons/logos/fedora";
import Freebsd from "~icons/logos/freebsd";
import Gentoo from "~icons/devicon/gentoo";
import Kalilinux from "~icons/devicon/kalilinux";
import Linux from "~icons/logos/linux-tux";
import Manjaro from "~icons/logos/manjaro";
import Mint from "~icons/logos/linux-mint";
import Nixos from "~icons/devicon/nixos";
import Opensuse from "~icons/devicon/opensuse";
import Raspberrypi from "~icons/logos/raspberry-pi";
import Redhat from "~icons/devicon/redhat";
import Rocky from "~icons/devicon/rockylinux";
import Ubuntu from "~icons/logos/ubuntu";
import Void from "~icons/logos/void";
import Windows10 from "~icons/devicon/windows8";
import Windows11 from "~icons/devicon/windows11";
import type { Component } from "svelte";

const normalize = (os: string | null | undefined) => os?.trim().toLowerCase() ?? "";

const BRANDS: Record<string, Component> = {
  windows: Windows11,
  "windows-11": Windows11,
  "windows-10": Windows10,
  macos: Apple,
  darwin: Apple,
  apple: Apple,
  ios: Apple,
  android: Android,
  ubuntu: Ubuntu,
  debian: Debian,
  fedora: Fedora,
  centos: Centos,
  rhel: Redhat,
  redhat: Redhat,
  "red-hat": Redhat,
  rocky: Rocky,
  rockylinux: Rocky,
  almalinux: Almalinux,
  alma: Almalinux,
  opensuse: Opensuse,
  "opensuse-leap": Opensuse,
  "opensuse-tumbleweed": Opensuse,
  suse: Opensuse,
  sles: Opensuse,
  arch: Archlinux,
  archlinux: Archlinux,
  endeavouros: Archlinux,
  artix: Artixlinux,
  artixlinux: Artixlinux,
  manjaro: Manjaro,
  linuxmint: Mint,
  mint: Mint,
  elementary: Elementary,
  void: Void,
  voidlinux: Void,
  freebsd: Freebsd,
  kali: Kalilinux,
  kalilinux: Kalilinux,
  "kali-linux": Kalilinux,
  nixos: Nixos,
  nix: Nixos,
  gentoo: Gentoo,
  raspbian: Raspberrypi,
  raspberrypi: Raspberrypi,
  "raspberry-pi": Raspberrypi,
};

export const osIcon = (os: string | null | undefined): Component | null => {
  const key = normalize(os);
  if (!key) return null;
  return BRANDS[key] ?? Linux;
};
