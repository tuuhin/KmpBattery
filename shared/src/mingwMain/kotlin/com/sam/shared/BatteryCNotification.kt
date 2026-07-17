@file:OptIn(ExperimentalForeignApi::class)

package com.sam.shared

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.wcstr
import platform.posix.GUID
import platform.windows.CW_USEDEFAULT
import platform.windows.CloseHandle
import platform.windows.CreateThread
import platform.windows.CreateWindowExW
import platform.windows.DEVICE_NOTIFY_WINDOW_HANDLE
import platform.windows.DWORD
import platform.windows.DefWindowProcW
import platform.windows.DispatchMessageW
import platform.windows.GUID_BATTERY_PERCENTAGE_REMAINING
import platform.windows.GetLastError
import platform.windows.GetMessageW
import platform.windows.GetModuleHandleW
import platform.windows.HANDLE
import platform.windows.HWND
import platform.windows.HWND_MESSAGE
import platform.windows.HWND__
import platform.windows.INFINITE
import platform.windows.LPARAM
import platform.windows.LPVOID
import platform.windows.LRESULT
import platform.windows.MSG
import platform.windows.NULL
import platform.windows.PBT_APMPOWERSTATUSCHANGE
import platform.windows.PBT_POWERSETTINGCHANGE
import platform.windows.PostMessageW
import platform.windows.PostQuitMessage
import platform.windows.RegisterClassW
import platform.windows.RegisterPowerSettingNotification
import platform.windows.TranslateMessage
import platform.windows.UnregisterClassW
import platform.windows.UnregisterPowerSettingNotification
import platform.windows.WM_CREATE
import platform.windows.WM_DESTROY
import platform.windows.WM_POWERBROADCAST
import platform.windows.WNDCLASS
import platform.windows.WPARAM
import platform.windows.WaitForSingleObject

private const val CLASS_NAME = "BatteryFlowManager"

private val logger = KotlinLogging.logger("WindowsBatteryLogger")

private val GUID_AC_DC_POWER_SOURCE = nativeHeap.alloc<GUID>().apply {
	Data1 = 0x5D3E9A59u
	Data2 = 0xE9D5u
	Data3 = 0x4B00u

	listOf(0xA6u, 0xBDu, 0xFFu, 0x34u, 0xFFu, 0x51u, 0x65u, 0x48u)
		.forEachIndexed { idx, byte -> Data4[idx] = byte.toUByte() }
}

private val GUILD_BATTERY_SAVER_MODE = nativeHeap.alloc<GUID>().apply {
	Data1 = 0x20630d7fu
	Data2 = 0xe248u
	Data3 = 0x4b52u

	listOf(0xa7u, 0x46u, 0x81u, 0x49u, 0x8cu, 0x0bu, 0x70u, 0xe5u)
		.forEachIndexed { idx, byte -> Data4[idx] = byte.toUByte() }
}

// hidden window pointer to receive notification
private var window: CPointer<HWND__>? = null

// pointer to power notification
private var powerCommonNotification: CPointer<out CPointed>? = null
private var powerACDCNotification: CPointer<out CPointed>? = null
private var powerSaverModeNotification: CPointer<out CPointed>? = null

// call when a new broadcast is received
private var callback: (() -> Unit)? = null

private val procedureFunction = staticCFunction(::createWindowProcedure)
private val observerRoutine = staticCFunction(::createObserverWindow)

private fun createWindowProcedure(
	window: HWND?,
	msg: UInt,
	wParam: WPARAM,
	lParams: LPARAM
): LRESULT {
	logger.info { "WINDOW PROCEDURE CREATING" }
	return when (msg.toInt()) {
		WM_CREATE -> {
			logger.info { "EVENT WINDOW CREATED" }
			// the window is loaded so creating the power setting notification
			powerCommonNotification = RegisterPowerSettingNotification(
				hRecipient = window,
				PowerSettingGuid = GUID_BATTERY_PERCENTAGE_REMAINING.ptr,
				Flags = DEVICE_NOTIFY_WINDOW_HANDLE.toUInt()
			)

			// register another one
			powerACDCNotification = RegisterPowerSettingNotification(
				hRecipient = window,
				PowerSettingGuid = GUID_AC_DC_POWER_SOURCE.ptr,
				Flags = DEVICE_NOTIFY_WINDOW_HANDLE.toUInt()
			)

			// register another one for battery saver mode
			powerSaverModeNotification = RegisterPowerSettingNotification(
				hRecipient = window,
				PowerSettingGuid = GUILD_BATTERY_SAVER_MODE.ptr,
				Flags = DEVICE_NOTIFY_WINDOW_HANDLE.toUInt()
			)

			val message = buildString {
				append("BATTERY PERCENTAGE NOTIFICATION :")
				if (powerCommonNotification == null) append("ERROR: FAILED TO REGISTER NOTIFICATION :${GetLastError()}")
				else append("REGISTERED,")
				append("AC/DC NOTIFICATION: ")
				if (powerCommonNotification == null) append("ERROR: FAILED TO REGISTER NOTIFICATION :${GetLastError()}")
				else append("REGISTERED,")
				append("BATTERY SAVER: ")
				if (powerCommonNotification == null) append("ERROR: FAILED TO REGISTER NOTIFICATION :${GetLastError()}")
				else append("REGISTERED")
			}

			logger.info { message }
			0
		}

		WM_POWERBROADCAST -> {
			val isPowerStatusChange = wParam.toInt() == PBT_APMPOWERSTATUSCHANGE
			val isPowerSettingsChanged = wParam.toInt() == PBT_POWERSETTINGCHANGE
			logger.info { "EVENT POWER STATUS CHANGED:$isPowerStatusChange POWER SETTINGS CHANGED:$isPowerSettingsChanged" }

			if (!isPowerStatusChange && !isPowerSettingsChanged) return 0
			// receiving broadcast here
			callback?.invoke()
			1
		}

		WM_DESTROY -> {
			logger.info { "EVENT :WINDOW DESTROYED UNREGISTERING ALL THE POWER NOTIFICATIONS" }
			// the window is destroyed so remove all the registered notification
			powerCommonNotification?.let {
				UnregisterPowerSettingNotification(it)
				powerCommonNotification = null
			}
			powerACDCNotification?.let {
				UnregisterPowerSettingNotification(it)
				powerACDCNotification = null
			}
			powerSaverModeNotification?.let {
				UnregisterPowerSettingNotification(it)
				powerSaverModeNotification = null
			}
			PostQuitMessage(0)
			0
		}
		// send the default messages
		else -> DefWindowProcW(window, msg, wParam, lParams)
	}
}


private fun createObserverWindow(h: LPVOID?): DWORD = memScoped {
	val handleInstance = GetModuleHandleW(null)

	val wc = alloc<WNDCLASS>().apply {
		lpfnWndProc = procedureFunction
		hInstance = handleInstance
		this.lpszClassName = CLASS_NAME.wcstr.ptr
	}

	// registering a window class for procedure function
	if (RegisterClassW(wc.ptr) <= 0u) return 0u

	// this will create the window
	window = CreateWindowExW(
		dwExStyle = 0u,
		lpClassName = CLASS_NAME,
		lpWindowName = null,
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

	logger.info { "TRANSPARENT WINDOW CREATED BEGIN EVENT LOOP" }

	val msg = alloc<MSG>()
	while (GetMessageW(msg.ptr, null, 0u, 0u) == 1) {
		TranslateMessage(msg.ptr)
		DispatchMessageW(msg.ptr)
	}
	logger.info { "MESSAGE LOOP ENDED UNREGISTERING WINDOW" }
	// when the message loop ends ie, a quit message is received
	// unregister the class or remove it.
	UnregisterClassW(CLASS_NAME, wc.hInstance)
	return 0u
}

fun createNewThreadAndStartObserver(caller: () -> Unit): HANDLE? {
	if (window != null) {
		logger.warn { "OBSERVER ALREADY PLANTED NEED TO CLEAR THE WINDOW TO CONTINUE" }
		return 1L.toCPointer<COpaque>()?.reinterpret()
	}

	callback = caller

	val handle = CreateThread(
		lpThreadAttributes = null,
		dwStackSize = 0u,
		lpStartAddress = observerRoutine,
		lpParameter = null,
		dwCreationFlags = 0u,
		lpThreadId = null
	)

	if (handle == null) {
		logger.warn { "UNABLE TO CREATE A THREAD CALLBACK REMOVED" }
		callback = null
		return null
	}
	logger.warn { "CREATED THREAD TO RECEIVE EVENTS" }
	return handle
}

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
	logger.info { "CLOSING WINDOW HANDLE" }
	CloseHandle(handle)

	window = null
	callback = null
	logger.info { "CLEAN UP DONE" }

}
