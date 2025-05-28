package com.sam.shared

enum class LinuxPowerClass(val type: String, val folderName: String) {
    BATTERY("Battery", "battery"),
    USB("USB", "usb"),
    AC("Mains", "ac")
    //TODO Study other os and check if there are any other power classes
}