/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.advancedcoroutines

import android.util.Log
import androidx.annotation.AnyThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import com.example.android.advancedcoroutines.util.CacheOnSuccess
import com.example.android.advancedcoroutines.utils.ComparablePair
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository module for handling data operations.
 *
 * This PlantRepository exposes two UI-observable database queries [plants] and
 * [getPlantsWithGrowZone].
 *
 * To update the plants cache, call [tryUpdateRecentPlantsForGrowZoneCache] or
 * [tryUpdateRecentPlantsCache].
 */
class PlantRepository private constructor(
    private val plantDao: PlantDao,
    private val plantService: NetworkService,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    /**
     * Fetch a list of [Plant]s from the database.
     * Returns a LiveData-wrapped List of Plants.
     */
//    val plants = plantDao.getPlants()

    /**
     * Fetch a list of [Plant]s from the database that matches a given [GrowZone].
     * Returns a LiveData-wrapped List of Plants.
     */
//    fun getPlantsWithGrowZone(growZone: GrowZone) =
//        plantDao.getPlantsWithGrowZoneNumber(growZone.number)

    /**
     * Returns true if we should make a network request.
     */
    private fun shouldUpdatePlantsCache(): Boolean {
        // suspending function, so you can e.g. check the status of the database here
        return true
    }

    /**
     * Update the plants cache.
     *
     * This function may decide to avoid making a network requests on every call based on a
     * cache-invalidation policy.
     */
    suspend fun tryUpdateRecentPlantsCache() {
        if (shouldUpdatePlantsCache()) fetchRecentPlants()
    }

    /**
     * Update the plants cache for a specific grow zone.
     *
     * This function may decide to avoid making a network requests on every call based on a
     * cache-invalidation policy.
     */
    suspend fun tryUpdateRecentPlantsForGrowZoneCache(growZoneNumber: GrowZone) {
        if (shouldUpdatePlantsCache()) fetchPlantsForGrowZone(growZoneNumber)
    }

    /**
     * Fetch a new list of plants from the network, and append them to [plantDao]
     */
    private suspend fun fetchRecentPlants() {
        val plants = plantService.allPlants()
        plantDao.insertAll(plants)
    }

    /**
     * Fetch a list of plants for a grow zone from the network, and append them to [plantDao]
     */
    private suspend fun fetchPlantsForGrowZone(growZone: GrowZone) {
        val plants = plantService.plantsByGrowZone(growZone)
        plantDao.insertAll(plants)
    }

    //TODO 추가
    /**
     * 맞춤 정렬 순서를 위한 메모리 내 캐시로 사용됩니다. 네트워크 오류가 있는 경우 정렬 순서를 가져오지 않았더라도 앱이 데이터를 표시할 수 있도록 빈 목록이 대신 사용됩니다.
     * */
    private var plantListSortOrderCache =
        CacheOnSuccess(onErrorFallback = { listOf<String>() }) {
            plantService.customPlantSortOrder()
        }

    /**
     * 확장함수 - list<Plant> 에 applySort를 추가한다.
     * */
    private fun List<Plant>.applySort(customSortOrder: List<String>): List<Plant> {
//        sortedBy()
//        원본 리스트를 바꾸지 않고 소팅된 리스트를 리턴한다.
        return sortedBy { plant ->
            //해당 아이템이 캐시에 존재하면 위치를 반환하고, 아닌 경우 후순위로 미룬다
            val positionForItem = customSortOrder.indexOf(plant.plantId).let { order ->
                if (order > -1) order else Int.MAX_VALUE
            }
            Log.d("PlantRepository", "test - $positionForItem, ${plant.name}")
            ComparablePair(positionForItem, plant.name)
        }
    }

    //plants 및 getPlantsWithGrowZone의 코드를 LiveData 빌더로 바꿉니다.
    val plants: LiveData<List<Plant>> = liveData<List<Plant>> {
        val plantsLiveData = plantDao.getPlants()
        val customSortOrder = plantListSortOrderCache.getOrAwait()
        emitSource(plantsLiveData.map { plantList ->
            plantList.applySort(customSortOrder)
        })
    }

    //TODO emitSource, getOrAwait 등 알아야한다.
//    fun getPlantsWithGrowZone(growZone: GrowZone) = liveData {
//        //DB에서 데이터 가져옴
//        val plantsGrowZoneLiveData = plantDao.getPlantsWithGrowZoneNumber(growZone.number)
//        val customSortOrder = plantListSortOrderCache.getOrAwait()
//
//        Log.d(TAG, "getPlantsWithGrowZone/data - $customSortOrder")
//
//        emitSource(plantsGrowZoneLiveData.map { plantList ->
//            plantList.applySort(customSortOrder)
//        })
//    }

    //    기본 스레드에 안전하게 사용할 수 있는 정렬 알고리즘 버전
    @AnyThread
    suspend fun List<Plant>.applyMainSafeSort(customSortOrder: List<String>) =
        withContext(defaultDispatcher) {
            this@applyMainSafeSort.applySort(customSortOrder)
        }

//    그런 다음 새로운 기본 안전 정렬을 LiveData 빌더에 사용할 수 있습니다. switchMap을 사용하도록 블록을 업데이트합니다. 이렇게 하면 새 값이 수신될 때마다 새 LiveData를 가리키게 됩니다.
//    네트워크에서 맞춤 정렬 순서가 수신되면 이 순서를 새 기본 안전 applyMainSafeSort와 함께 사용할 수 있습니다.
//    그런 다음 이 결과는 getPlantsWithGrowZone에 의해 반환된 새 값으로 switchMap에 내보내집니다.
//    위의 plants LiveData와 유사하게 코루틴은 관찰되면 실행되기 시작하고 완료 시 또는 데이터베이스나 네트워크의 호출이 실패하는 경우 실행이 종료됩니다.
//    여기서 차이점은 캐시되어 있으므로 매핑에서 네트워크 호출을 실행해도 안전하다는 점입니다.
    //이거 다음으로 flow 로 변경로직
    fun getPlantsWithGrowZone(growZone: GrowZone) =
        plantDao.getPlantsWithGrowZoneNumber(growZone.number)
            .switchMap { plantList ->
                liveData {
                    val customSortOrder = plantListSortOrderCache.getOrAwait()
                    emit(plantList.applyMainSafeSort(customSortOrder))
                }
            }



    companion object {

        // For Singleton instantiation
        @Volatile
        private var instance: PlantRepository? = null

        fun getInstance(plantDao: PlantDao, plantService: NetworkService) =
            instance ?: synchronized(this) {
                instance ?: PlantRepository(plantDao, plantService).also { instance = it }
            }

        val TAG: String = PlantRepository::class.java.simpleName
    }
}
