@file:OptIn(ExperimentalForeignApi::class)

package com.sam.kmp_battery

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cinterop.*
import platform.posix.GUID
import platform.windows.*

private const val CLASS_NAME = "BatteryFlowManager"

private val logger = KotlinLogging.logger("Mingwx64BatteryLogger")
// TODO : Look for memory error in the file

@OptIn(ExperimentalForeignApi::class)
private val GUID_AC_DC_POWER_SOURCE = nativeHeap.alloc<GUID>().apply {
    Data1 = 0x5D3E9A59u
    Data2 = 0xE9D5u
    Data3 = 0x4B00u

    val bytes = listOf(0xA6u, 0xBDu, 0xFFu, 0x34u, 0xFFu, 0x51u, 0x65u, 0x48u)

    for (i in bytes.indices) {
        Data4[i] = bytes[i].toUByte()
    }
}

@OptIn(ExperimentalForeignApi::class)
private val GUILD_BATTERY_SAVER_MODE = nativeHeap.alloc<GUID>().apply {
    Data1 = 0x20630d7fu
    Data2 = 0xe248u
    Data3 = 0x4b52u

    val bytes = listOf(0xa7u, 0x46u, 0x81u, 0x49u, 0x8cu, 0x0bu, 0x70u, 0xe5u)

    for (i in bytes.indices) {
        Data4[i] = bytes[i].toUByte()
    }
}

// hidden window pointer to receive notification
private var window: CPointer<HWND__>? = null

// pointer to power notification
private var hPowerNotification: CPointer<out CPointed>? = null
private var hPowerACDCNotification: CPointer<out CPointed>? = null
private var hSaverModeNotification: CPointer<out CPointed>? = null

// call when a new broadcast is received
private var callback: (() -> Unit)? = null

@OptIn(ExperimentalForeignApi::class)
private val procedureFunction = staticCFunction(::createWindowProcedure)

@OptIn(ExperimentalForeignApi::class)
private val observerRoutine = staticCFunction(::createObserverWindow)

@OptIn(ExperimentalForeignApi::class)
private fun createWindowProcedure(window: HWND?, msg: UInt, wParam: WPARAM, lParams: LPARAM): LRESULT {
    return when (msg.toInt()) {
        WM_CREATE -> {
            logger.info { "WINDOW PROCEDURE CREATING" }

            // the window is loaded so creating the power setting notification
            hPowerNotification = RegisterPowerSettingNotification(
                hRecipient = window,
                PowerSettingGuid = GUID_BATTERY_PERCENTAGE_REMAINING.ptr,
                Flags = DEVICE_NOTIFY_WINDOW_HANDLE.toUInt()
            )

            // register another one
            hPowerACDCNotification = RegisterPowerSettingNotification(
                hRecipient = window,
                PowerSettingGuid = GUID_AC_DC_POWER_SOURCE.ptr,
                Flags = DEVICE_NOTIFY_WINDOW_HANDLE.toUInt()
            )

            // register another one for battery saver mode
            hSaverModeNotification = RegisterPowerSettingNotification(
                hRecipient = window,
                PowerSettingGuid = GUILD_BATTERY_SAVER_MODE.ptr,
                Flags = DEVICE_NOTIFY_WINDOW_HANDLE.toUInt()
            )

            val message = buildString {
                append("BATTERY PERCENTAGE NOTIFICATION :")
                if (hPowerNotification == null) append("ERROR: FAILED TO REGISTER NOTIFICATION :${GetLastError()}")
                else append("REGISTERED,")
                append("AC/DC NOTIFICATION: ")
                if (hPowerNotification == null) append("ERROR: FAILED TO REGISTER NOTIFICATION :${GetLastError()}")
                else append("REGISTERED,")
                append("BATTERY SAVER: ")
                if (hPowerNotification == null) append("ERROR: FAILED TO REGISTER NOTIFICATION :${GetLastError()}")
                else append("REGISTERED")
            }

            logger.info { message }

            return 0
        }

        WM_POWERBROADCAST -> {
            logger.info { "PROCEDURE SOME BROADCAST FOUND ${wParam.toInt()}" }
            if (wParam.toInt() != PBT_APMPOWERSTATUSCHANGE && wParam.toInt() != PBT_POWERSETTINGCHANGE) return 0
            // receiving broadcast here
            callback?.invoke()
            return 1
        }

        WM_DESTROY -> {
            // the window is destroyed so remove all the registered notification
            hPowerNotification?.let {
                UnregisterPowerSettingNotification(it);
                hPowerNotification = null
            }
            hPowerACDCNotification?.let {
                UnregisterPowerSettingNotification(it)
                hPowerACDCNotification = null
            }
            hSaverModeNotification?.let {
                UnregisterPowerSettingNotification(it);
                hSaverModeNotification = null;
            }
            PostQuitMessage(0)
            logger.info { "WINDOW PROCEDURE DESTROYED UNREGISTERING ALL THE NOTIFICATIONS" }
            return 0
        }
        // send the default messages
        else -> DefWindowProcW(window, msg, wParam, lParams)
    }
}


@OptIn(ExperimentalForeignApi::class)
private fun createObserverWindow(lpParams: LPVOID?): DWORD = memScoped {
    val wc = alloc<WNDCLASS>().apply {
        lpfnWndProc = procedureFunction
        hInstance = GetModuleHandleW(null)
        this.lpszClassName = CLASS_NAME.wcstr.ptr
    }

    // registering a window class for procedure function
    if (RegisterClassW(wc.ptr) <= 0u) return 0u

    // this will create the window
    window = CreateWindowExW(
        dwExStyle = 0u,
        lpClassName = CLASS_NAME,
        lpWindowName = "Battery Observer",
        dwStyle = 0u,
        X = CW_USEDEFAULT,
        Y = CW_USEDEFAULT,
        nWidth = CW_USEDEFAULT,
        nHeight = CW_USEDEFAULT,
        hWndParent = HWND_MESSAGE,
        hMenu = null,
        hInstance = wc.hInstance,
        lpParam = null
    )

    if (window == null) return 1u

    logger.info { "WINDOW CREATED" }

    val msg = alloc<MSG>()
    while (GetMessageW(msg.ptr, null, 0u, 0u) == 1) {
        TranslateMessage(msg.ptr)
        DispatchMessageW(msg.ptr)
    }

    // when the message loop ends, unregister the class or remove it.
    UnregisterClassW(CLASS_NAME, GetModuleHandleW(null))

    return 0u
}

@OptIn(ExperimentalForeignApi::class)
fun createNewThreadAndStartObserver(caller: () -> Unit): HANDLE? {
    if (window != null) {
        logger.warn { "OBSERVER ALREADY PLANTED" }
        return 1L.toCPointer<COpaque>()?.reinterpret()
    }

    callback = caller

    val thread = CreateThread(
        lpThreadAttributes = null,
        dwStackSize = 0u,
        lpStartAddress = observerRoutine,
        lpParameter = null,
        dwCreationFlags = 0u,
        lpThreadId = null
    )

    if (thread == null) {
        logger.warn { "UNABLE TO CREATE A THREAD CALLBACK REMOVED" }
        callback = null
        return null
    }
    return thread
}

@OptIn(ExperimentalForeignApi::class)
fun stopObserverAndCloseThread(handle: HANDLE?) {
    if (window == NULL) return
    //wait for a single object is a blocking call so running it on a different thread

    logger.info { "STOPPING THE OBSERVER AND SENDING MESSAGE" }
    PostMessageW(window, WM_DESTROY.toUInt(), 0u, 0L)

    val isBlankHandle: HANDLE? = 1L.toCPointer<COpaque>()?.reinterpret()

    if (handle == null || handle == isBlankHandle) {
        logger.warn { "CANNOT REMOVE AS IT WAS NOT CREATED PROPERLY" }
        return
    }

    WaitForSingleObject(handle, INFINITE)
    CloseHandle(handle)

    window = null
    callback = null
    logger.info { "CLEAN UP DONE" }

}
