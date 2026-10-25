package com.cheku.app.performance

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cheku.app.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PerformanceViewModel(app:Application):AndroidViewModel(app){
    private val repo=PerformanceRepository(AppDatabase.get(app).performanceDao())
    val message = MutableStateFlow<String?>(null)
    val sessions=repo.observeSessions().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    private val selectedId=MutableStateFlow<String?>(null)
    val selected=combine(sessions,selectedId){list,id->list.firstOrNull{it.id==id}}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),null)
    val samples=selectedId.flatMapLatest{id->if(id==null)flowOf(emptyList())else repo.observeSamples(id)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val jerks=selectedId.flatMapLatest{id->if(id==null)flowOf(emptyList())else repo.observeJerks(id)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val splits=selectedId.flatMapLatest{id->if(id==null)flowOf(emptyList())else repo.observeSplits(id)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val activeSamples=sessions.map{it.firstOrNull{s->s.status!=PerformanceStatus.Completed&&s.status!=PerformanceStatus.Interrupted}?.id}.distinctUntilChanged().flatMapLatest{id->if(id==null)flowOf(emptyList())else repo.observeSamples(id)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    fun select(id:String?){selectedId.value=id}
    fun start(type:PerformanceTestType,vehicleId:String?,name:String){
        val context=getApplication<Application>()
        runCatching {
            ContextCompat.startForegroundService(context,Intent(context,PerformanceService::class.java).setAction(PerformanceService.START).putExtra(PerformanceService.TYPE,type.name).putExtra(PerformanceService.VEHICLE,vehicleId).putExtra(PerformanceService.NAME,name))
        }.onFailure { message.value = it.message ?: "无法启动性能测试" }
    }
    fun stop(){getApplication<Application>().startService(Intent(getApplication(),PerformanceService::class.java).setAction(PerformanceService.STOP))}
    fun mark(){getApplication<Application>().startService(Intent(getApplication(),PerformanceService::class.java).setAction(PerformanceService.MARK))}
    fun delete(id:String)=viewModelScope.launch{repo.delete(id);if(selectedId.value==id)selectedId.value=null}
    fun calibration()=getApplication<Application>().getSharedPreferences("performance",0).getFloat("noise_calibration",94f)
    fun setCalibration(value:Float){getApplication<Application>().getSharedPreferences("performance",0).edit().putFloat("noise_calibration",value).apply()}
    fun clearMessage(){message.value=null}
}
