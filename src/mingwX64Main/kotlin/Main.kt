import com.sam.kmp_battery.MingwBatteryManager
import kotlinx.coroutines.runBlocking

fun main() {
    val manager = MingwBatteryManager()
    runBlocking {
//       println(manager.batteryState())
        val state = manager.batteryState()
        println(state)
    }
}