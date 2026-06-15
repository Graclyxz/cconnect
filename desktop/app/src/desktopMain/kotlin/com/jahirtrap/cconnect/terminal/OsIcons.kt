package com.jahirtrap.cconnect.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.fontawesome.FontAwesome
import com.composables.icons.fontawesome.brands.Android
import com.composables.icons.fontawesome.brands.Apple
import com.composables.icons.fontawesome.brands.Centos
import com.composables.icons.fontawesome.brands.Fedora
import com.composables.icons.fontawesome.brands.Linux
import com.composables.icons.fontawesome.brands.RaspberryPi
import com.composables.icons.fontawesome.brands.Redhat
import com.composables.icons.fontawesome.brands.Suse
import com.composables.icons.fontawesome.brands.Ubuntu
import com.composables.icons.fontawesome.brands.Windows
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SquareTerminal

private fun normalize(os: String?) = os?.trim()?.lowercase().orEmpty()

fun iconForOs(os: String?): ImageVector = when (normalize(os)) {
    "" -> Lucide.SquareTerminal
    "windows" -> FontAwesome.Brands.Windows
    "macos", "darwin", "apple", "ios" -> FontAwesome.Brands.Apple
    "android" -> FontAwesome.Brands.Android
    "ubuntu" -> FontAwesome.Brands.Ubuntu
    "fedora" -> FontAwesome.Brands.Fedora
    "centos" -> FontAwesome.Brands.Centos
    "rhel", "redhat", "red-hat", "rocky", "almalinux", "alma" -> FontAwesome.Brands.Redhat
    "opensuse", "opensuse-leap", "opensuse-tumbleweed", "suse", "sles" -> FontAwesome.Brands.Suse
    "raspbian", "raspberrypi", "raspberry-pi" -> FontAwesome.Brands.RaspberryPi
    else -> FontAwesome.Brands.Linux
}

fun colorForOs(os: String?): Color? = when (normalize(os)) {
    "windows" -> Color(0xFF00A4EF)
    "macos", "darwin", "apple", "ios" -> Color(0xFFA2AAAD)
    "android" -> Color(0xFF3DDC84)
    "ubuntu" -> Color(0xFFE95420)
    "debian" -> Color(0xFFA81D33)
    "fedora" -> Color(0xFF51A2DA)
    "centos" -> Color(0xFF932279)
    "rhel", "redhat", "red-hat" -> Color(0xFFEE0000)
    "rocky" -> Color(0xFF10B981)
    "almalinux", "alma" -> Color(0xFF0FB37D)
    "opensuse", "opensuse-leap", "opensuse-tumbleweed", "suse", "sles" -> Color(0xFF73BA25)
    "arch", "archlinux" -> Color(0xFF1793D1)
    "manjaro" -> Color(0xFF35BF5C)
    "endeavouros" -> Color(0xFF7F3FBF)
    "linuxmint", "mint" -> Color(0xFF87CF3E)
    "pop", "popos", "pop_os" -> Color(0xFF48B9C7)
    "kali", "kalilinux", "kali-linux" -> Color(0xFF367BF0)
    "alpine" -> Color(0xFF0D597F)
    "raspbian", "raspberrypi", "raspberry-pi" -> Color(0xFFC51A4A)
    "freebsd" -> Color(0xFFAB2B28)
    "openbsd" -> Color(0xFFF2CA30)
    "nixos", "nix" -> Color(0xFF5277C3)
    "gentoo" -> Color(0xFF54487A)
    "elementary" -> Color(0xFF64BAFF)
    "void", "voidlinux" -> Color(0xFF478061)
    "linux" -> Color(0xFFFCC624)
    else -> null
}
