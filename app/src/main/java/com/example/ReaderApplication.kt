package com.example

import android.app.Application
import android.widget.Toast
import com.example.data.LocalLibrary
import kotlinx.coroutines.*

class ReaderApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, _ ->
        Toast.makeText(this, "无法保存书架数据，请检查设备存储空间", Toast.LENGTH_LONG).show()
    })
    val library by lazy { LocalLibrary(this, scope) }
    override fun onCreate() {
        super.onCreate()
        scope.launch { library.recover() }
    }
}
