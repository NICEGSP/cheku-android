package com.cheku.app.data

import com.cheku.app.domain.EnergySaveResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerRepositoryTest {
    @Test
    fun deletingOnlyMaintenanceHistoryResetsMileageBaselineToCurrentVehicle() = runTest {
        val dao = FakeLedgerDao()
        val vehicle = testVehicle()
        dao.upsertVehicle(vehicle)
        val repository = LedgerRepository(dao)
        repository.seedIfEmpty(now = 1_000L)
        val item = dao.maintenanceItemsForVehicle(vehicle.id)
            .first { it.itemType == MaintenanceItemType.Periodic }

        repository.completeMaintenance(item.id, 1_500, "done", now = 2_000L)
        repository.addMileageRecord(vehicle.id, 2_000.0, "later", now = 3_000L)
        val history = dao.historyForItem(item.id).maxByOrNull { it.serviceAt }
            ?: error("history missing")
        repository.deleteMaintenanceHistory(history.id)

        assertEquals(2_000, dao.maintenanceItemById(item.id)?.lastServiceMileage)
    }

    @Test
    fun undoMaintenanceFallsBackToPreviousRecordPartNumber() = runTest {
        val dao = FakeLedgerDao()
        val vehicle = testVehicle()
        dao.upsertVehicle(vehicle)
        val repository = LedgerRepository(dao)
        repository.seedIfEmpty(now = 1_000L)
        val item = dao.maintenanceItemsForVehicle(vehicle.id)
            .first { it.itemType == MaintenanceItemType.Periodic }

        repository.completeMaintenance(item.id, 1_500, "first", partNumber = "机滤-A", now = 2_000L)
        repository.completeMaintenance(item.id, 3_000, "second", partNumber = "机滤-B", now = 4_000L)
        assertEquals("机滤-B", dao.maintenanceItemById(item.id)?.lastPartNumber)

        repository.undoMaintenance(item.id, previousMileage = 1_500, previousAt = 2_000L)

        val restored = dao.maintenanceItemById(item.id) ?: error("item missing")
        assertEquals("机滤-A", restored.lastPartNumber)
        assertEquals(1_500, restored.lastServiceMileage)
        assertEquals(1, dao.historyForItem(item.id).size)
    }

    @Test
    fun undoMaintenanceClearsPartNumberWhenNoHistoryLeft() = runTest {
        val dao = FakeLedgerDao()
        val vehicle = testVehicle()
        dao.upsertVehicle(vehicle)
        val repository = LedgerRepository(dao)
        repository.seedIfEmpty(now = 1_000L)
        val item = dao.maintenanceItemsForVehicle(vehicle.id)
            .first { it.itemType == MaintenanceItemType.Periodic }

        repository.completeMaintenance(item.id, 1_500, "only", partNumber = "机滤-A", now = 2_000L)
        repository.undoMaintenance(item.id, previousMileage = 1_200, previousAt = 900L)

        val restored = dao.maintenanceItemById(item.id) ?: error("item missing")
        assertEquals("", restored.lastPartNumber)
        assertEquals(0, dao.historyForItem(item.id).size)
    }

    @Test
    fun protectedInsuranceAndRepairItemsAreAddedWithoutSyntheticHistory() = runTest {
        val dao = FakeLedgerDao()
        val vehicle = testVehicle()
        dao.upsertVehicle(vehicle)
        val repository = LedgerRepository(dao)

        repository.seedIfEmpty(now = 1_000L)
        repository.seedIfEmpty(now = 2_000L)

        val items = dao.maintenanceItemsForVehicle(vehicle.id)
        assertEquals(1, items.count { it.itemType == MaintenanceItemType.CommercialInsurance })
        assertEquals(1, items.count { it.itemType == MaintenanceItemType.CompulsoryInsurance })
        assertEquals(1, items.count { it.itemType == MaintenanceItemType.RepairInspection })
        val protectedIds = items.filter { it.itemType.isProtected }.mapTo(mutableSetOf()) { it.id }
        assertTrue(dao.observeAllHistory().first().none { it.itemId in protectedIds })
    }

    @Test
    fun firstInsuranceRenewalCreatesOnlyOneRealRecordAndRepairKeepsItsOwnCosts() = runTest {
        val dao = FakeLedgerDao()
        val vehicle = testVehicle()
        dao.upsertVehicle(vehicle)
        val repository = LedgerRepository(dao)
        repository.seedIfEmpty(now = 1_000L)
        val items = dao.maintenanceItemsForVehicle(vehicle.id)
        val insurance = items.single { it.itemType == MaintenanceItemType.CommercialInsurance }
        val repair = items.single { it.itemType == MaintenanceItemType.RepairInspection }

        repository.completeInsurance(insurance.id, 5_000L, 365L * 24L * 60L * 60L * 1000L, 2_345.67, "首保")
        repository.addRepairInspection(repair.id, "补胎", 6_000L, 1_020.25, 80.0, 20.0, "右后轮")

        val history = dao.observeAllHistory().first()
        assertEquals(1, history.count { it.itemId == insurance.id })
        assertEquals(2_345.67, history.single { it.itemId == insurance.id }.insuranceAmount, 0.001)
        assertTrue(history.single { it.itemId == insurance.id }.expiresAt != null)
        val repairRecord = history.single { it.itemId == repair.id }
        assertEquals("补胎", repairRecord.recordName)
        assertEquals(80.0, repairRecord.repairAmount, 0.001)
        assertEquals(20.0, repairRecord.laborCost, 0.001)
    }

    @Test
    fun addMileageRecordUpdatesVehicleCurrentMileage() = runTest {
        val dao = FakeLedgerDao()
        val vehicle = testVehicle()
        dao.upsertVehicle(vehicle)

        val saved = LedgerRepository(dao).addMileageRecord(vehicle.id, 1_250.0, "test", now = 10L)

        assertTrue(saved)
        assertEquals(1_250.0, dao.vehicleById(vehicle.id)!!.currentMileage, 0.001)
    }

    @Test
    fun fuelSettlementUsesPaidAmountForCostWithoutChangingLiters() = runTest {
        val dao = FakeLedgerDao()
        val vehicle = testVehicle()
        dao.upsertVehicle(vehicle)
        val repository = LedgerRepository(dao)

        repository.addEnergyRecord(vehicle.id, fuel(1_000, 20.0, 160.0, 160.0, true), now = 10L)
        repository.addEnergyRecord(vehicle.id, fuel(1_100, 10.0, 80.0, 70.0, false), now = 20L)
        val result = repository.addEnergyRecord(vehicle.id, fuel(1_300, 8.0, 64.0, 50.0, true), now = 30L)

        val settled = result as EnergySaveResult.Settled
        assertEquals(6.0, settled.consumptionPer100Km, 0.001)
        assertEquals(0.4, settled.costPerKm, 0.001)
        assertEquals(18.0, dao.settlements.single { it.status == SettlementStatus.Valid }.totalAmount, 0.001)
    }

    @Test
    fun chargeRecordUpdatesVehicleDefaultAndKeepsSnapshot() = runTest {
        val dao = FakeLedgerDao()
        val vehicle = testVehicle(EnergyType.Electric)
        dao.upsertVehicle(vehicle)
        val repository = LedgerRepository(dao)

        repository.addEnergyRecord(vehicle.id, charge(1_000, 10.0, true, 90), now = 10L)

        assertEquals(90, dao.vehicleById(vehicle.id)!!.defaultChargeLimitSoc)
        assertEquals(90, dao.energyRecords.single().chargeLimitSocSnapshot)
    }

    @Test
    fun changedChargeLimitStartsNewBaselineAndLaterSettlesAtNewLimit() = runTest {
        val dao = FakeLedgerDao()
        val vehicle = testVehicle(EnergyType.Electric)
        dao.upsertVehicle(vehicle)
        val repository = LedgerRepository(dao)

        repository.addEnergyRecord(vehicle.id, charge(1_000, 10.0, true, 80), now = 10L)
        repository.addEnergyRecord(vehicle.id, charge(1_200, 20.0, true, 90), now = 20L)
        val result = repository.addEnergyRecord(vehicle.id, charge(1_500, 30.0, true, 90), now = 30L)

        assertTrue(result is EnergySaveResult.Settled)
        assertEquals(10.0, (result as EnergySaveResult.Settled).consumptionPer100Km, 0.001)
        assertTrue(dao.settlements.any { it.invalidReason == "充电上限已变更" })
    }

    @Test
    fun editingEnergyEndpointRebuildsSettlement() = runTest {
        val dao = FakeLedgerDao()
        val vehicle = testVehicle()
        dao.upsertVehicle(vehicle)
        val repository = LedgerRepository(dao)
        repository.addEnergyRecord(vehicle.id, fuel(1_000, 20.0, 160.0, 160.0, true), now = 10L)
        repository.addEnergyRecord(vehicle.id, fuel(1_300, 18.0, 144.0, 120.0, true), now = 20L)
        val endpoint = dao.energyRecords.maxBy { it.createdAt }

        repository.updateEnergyRecord(
            endpoint.id,
            fuel(1_400, 20.0, 160.0, 100.0, true),
            createdAt = 20L
        )

        val valid = dao.settlements.single { it.status == SettlementStatus.Valid }
        assertEquals(5.0, valid.consumptionPer100Km!!, 0.001)
        assertEquals(0.25, valid.costPerKm!!, 0.001)
    }

    @Test
    fun changingEnergyTypeReturnsNewTypeSettlementResult() = runTest {
        val dao = FakeLedgerDao()
        val vehicle = testVehicle(EnergyType.PlugInHybrid)
        dao.upsertVehicle(vehicle)
        val repository = LedgerRepository(dao)
        repository.addEnergyRecord(vehicle.id, fuel(1_000, 20.0, 160.0, 160.0, true), now = 10L)
        repository.addEnergyRecord(vehicle.id, fuel(1_200, 10.0, 80.0, 80.0, true), now = 20L)
        repository.addEnergyRecord(vehicle.id, charge(1_050, 10.0, true, 100), now = 15L)
        val endpoint = dao.energyRecords.single { it.type == EnergyRecordType.Fuel && it.mileage == 1_200.0 }

        val result = repository.updateEnergyRecord(
            endpoint.id,
            charge(1_250, 20.0, true, 100),
            createdAt = 30L
        )

        assertTrue(result is EnergySaveResult.Settled)
        assertEquals(10.0, (result as EnergySaveResult.Settled).consumptionPer100Km, 0.001)
        assertTrue(dao.settlements.none { it.type == EnergyRecordType.Fuel && it.status == SettlementStatus.Valid })
        assertTrue(dao.settlements.any { it.type == EnergyRecordType.Charge && it.status == SettlementStatus.Valid })
    }

    @Test
    fun deletingLatestMileageRecordRecalculatesVehicleMileage() = runTest {
        val dao = FakeLedgerDao()
        val vehicle = testVehicle().copy(currentMileage = 1_000.0)
        dao.upsertVehicle(vehicle)
        val repository = LedgerRepository(dao)
        repository.addMileageRecord(vehicle.id, 1_200.0, "first", now = 10L)
        repository.addMileageRecord(vehicle.id, 1_300.0, "latest", now = 20L)
        val latest = dao.allMileageRecords().single { it.mileage == 1_300.0 }

        repository.deleteMileageRecord(latest.id)

        assertEquals(1_200.0, dao.vehicleById(vehicle.id)!!.currentMileage, 0.001)
    }

    @Test
    fun qinPlusDemoIncludesFuelAndChargeConsumption() = runTest {
        val dao = FakeLedgerDao()
        val repository = LedgerRepository(dao)

        repository.seedQinPlusDemoIfMissing(now = 30L * 24L * 60L * 60L * 1000L)
        repository.seedQinPlusDemoIfMissing(now = 31L * 24L * 60L * 60L * 1000L)

        val vehicle = dao.vehicleById("seed-qin-plus")!!
        val validSettlements = dao.settlements.filter { it.status == SettlementStatus.Valid }
        assertEquals(EnergyType.PlugInHybrid, vehicle.energyType)
        assertEquals(100, vehicle.defaultChargeLimitSoc)
        assertEquals(4, dao.energyRecords.count { it.vehicleId == vehicle.id })
        assertEquals(
            4.4,
            validSettlements.single { it.type == EnergyRecordType.Fuel }.consumptionPer100Km!!,
            0.001
        )
        assertEquals(
            13.2,
            validSettlements.single { it.type == EnergyRecordType.Charge }.consumptionPer100Km!!,
            0.001
        )
    }

    @Test
    fun clearAllDataLeavesDatabaseEmpty() = runTest {
        val dao = FakeLedgerDao()
        dao.upsertVehicle(testVehicle())

        LedgerRepository(dao).clearAllData()

        assertEquals(0, dao.observeVehicles().first().size)
    }

    private fun fuel(
        mileage: Int,
        amount: Double,
        orderAmount: Double,
        paidAmount: Double,
        full: Boolean
    ) = EnergyRecordInput(
        type = EnergyRecordType.Fuel,
        mileage = mileage.toDouble(),
        amount = amount,
        orderAmount = orderAmount,
        paidAmount = paidAmount,
        isFull = full,
        missedPrevious = false,
        fuelGrade = "95",
        unitPrice = 8.0
    )

    private fun charge(mileage: Int, amount: Double, full: Boolean, limit: Int) = EnergyRecordInput(
        type = EnergyRecordType.Charge,
        mileage = mileage.toDouble(),
        amount = amount,
        orderAmount = amount,
        paidAmount = amount,
        isFull = full,
        missedPrevious = false,
        chargeMethod = "家充",
        electricityPrice = 1.0,
        chargeLimitSocSnapshot = limit
    )
}

private class FakeLedgerDao : LedgerDao {
    private val vehiclesFlow = MutableStateFlow<List<VehicleEntity>>(emptyList())
    private val mileageFlow = MutableStateFlow<List<MileageRecordEntity>>(emptyList())
    private val energyFlow = MutableStateFlow<List<EnergyRecordEntity>>(emptyList())
    private val settlementFlow = MutableStateFlow<List<EnergySettlementEntity>>(emptyList())
    private val maintenanceFlow = MutableStateFlow<List<MaintenanceItemEntity>>(emptyList())
    private val maintenanceHistoryFlow = MutableStateFlow<List<MaintenanceHistoryEntity>>(emptyList())
    private val fitActivitiesFlow = MutableStateFlow<List<FitActivityEntity>>(emptyList())
    private val fitSamplesFlow = MutableStateFlow<List<FitSampleEntity>>(emptyList())
    private val fitEventsFlow = MutableStateFlow<List<FitTimerEventEntity>>(emptyList())

    val energyRecords: List<EnergyRecordEntity> get() = energyFlow.value
    val settlements: List<EnergySettlementEntity> get() = settlementFlow.value

    override suspend fun vehicleCount() = vehiclesFlow.value.size
    override suspend fun mileageRecordCount() = mileageFlow.value.size
    override suspend fun energyRecordCount() = energyFlow.value.size
    override fun observeVehicles(): Flow<List<VehicleEntity>> = vehiclesFlow
    override suspend fun allVehicles() = vehiclesFlow.value
    override suspend fun latestVehicle() = vehiclesFlow.value.maxByOrNull { it.updatedAt }
    override suspend fun vehicleById(id: String) = vehiclesFlow.value.firstOrNull { it.id == id }

    override suspend fun upsertVehicle(vehicle: VehicleEntity) {
        vehiclesFlow.value = vehiclesFlow.value.filterNot { it.id == vehicle.id } + vehicle
    }

    override suspend fun updateVehicle(vehicle: VehicleEntity) {
        vehiclesFlow.value = vehiclesFlow.value.map { if (it.id == vehicle.id) vehicle else it }
    }

    override suspend fun upsertVehicles(vehicles: List<VehicleEntity>) {
        vehicles.forEach { upsertVehicle(it) }
    }

    override suspend fun deleteVehicle(id: String) {
        vehiclesFlow.value = vehiclesFlow.value.filterNot { it.id == id }
        mileageFlow.value = mileageFlow.value.filterNot { it.vehicleId == id }
        energyFlow.value = energyFlow.value.filterNot { it.vehicleId == id }
        settlementFlow.value = settlementFlow.value.filterNot { it.vehicleId == id }
        maintenanceFlow.value = maintenanceFlow.value.filterNot { it.vehicleId == id }
        maintenanceHistoryFlow.value = maintenanceHistoryFlow.value.filterNot { it.vehicleId == id }
    }

    override suspend fun insertMileageRecord(record: MileageRecordEntity) {
        mileageFlow.value = mileageFlow.value.filterNot { it.id == record.id } + record
    }

    override fun observeMileageRecords(limit: Int): Flow<List<MileageRecordEntity>> = mileageFlow
    override suspend fun allMileageRecords() = mileageFlow.value
    override suspend fun mileageRecordById(id: String) = mileageFlow.value.firstOrNull { it.id == id }

    override suspend fun insertEnergyRecord(record: EnergyRecordEntity) {
        energyFlow.value = energyFlow.value.filterNot { it.id == record.id } + record
    }

    override fun observeEnergyRecords(limit: Int): Flow<List<EnergyRecordEntity>> = energyFlow
    override suspend fun allEnergyRecords() = energyFlow.value
    override suspend fun energyRecordById(id: String) = energyFlow.value.firstOrNull { it.id == id }
    override suspend fun energyRecordsForVehicle(vehicleId: String, type: EnergyRecordType) =
        energyFlow.value.filter { it.vehicleId == vehicleId && it.type == type }

    override fun observeEnergySettlements(): Flow<List<EnergySettlementEntity>> = settlementFlow
    override suspend fun energySettlementsForVehicle(vehicleId: String, type: EnergyRecordType) =
        settlementFlow.value.filter { it.vehicleId == vehicleId && it.type == type }

    override suspend fun upsertEnergySettlements(settlements: List<EnergySettlementEntity>) {
        val ids = settlements.mapTo(mutableSetOf()) { it.id }
        settlementFlow.value = settlementFlow.value.filterNot { it.id in ids } + settlements
    }

    override suspend fun deleteEnergySettlements(vehicleId: String, type: EnergyRecordType) {
        settlementFlow.value = settlementFlow.value.filterNot { it.vehicleId == vehicleId && it.type == type }
    }

    override suspend fun mileageRecordsForVehicle(vehicleId: String) =
        mileageFlow.value.filter { it.vehicleId == vehicleId }

    override suspend fun allEnergyRecordsForVehicle(vehicleId: String) =
        energyFlow.value.filter { it.vehicleId == vehicleId }

    override suspend fun upsertMaintenanceItems(items: List<MaintenanceItemEntity>) {
        items.forEach { upsertMaintenanceItem(it) }
    }

    override suspend fun upsertMaintenanceItem(item: MaintenanceItemEntity) {
        maintenanceFlow.value = maintenanceFlow.value.filterNot { it.id == item.id } + item
    }

    override fun observeMaintenanceItems(): Flow<List<MaintenanceItemEntity>> = maintenanceFlow
    override suspend fun maintenanceItemById(id: String) = maintenanceFlow.value.firstOrNull { it.id == id }
    override suspend fun maintenanceItemsForVehicle(vehicleId: String) =
        maintenanceFlow.value.filter { it.vehicleId == vehicleId }
    override suspend fun maintenanceCountForVehicle(vehicleId: String) =
        maintenanceFlow.value.count { it.vehicleId == vehicleId }

    override suspend fun deleteMaintenanceItem(id: String) {
        maintenanceFlow.value = maintenanceFlow.value.filterNot { it.id == id }
    }

    override suspend fun insertMaintenanceHistory(history: MaintenanceHistoryEntity) {
        maintenanceHistoryFlow.value = maintenanceHistoryFlow.value.filterNot { it.id == history.id } + history
    }

    override fun observeHistoryForItem(itemId: String): Flow<List<MaintenanceHistoryEntity>> =
        MutableStateFlow(maintenanceHistoryFlow.value.filter { it.itemId == itemId })
    override fun observeAllHistory(): Flow<List<MaintenanceHistoryEntity>> = maintenanceHistoryFlow
    override suspend fun historyForItem(itemId: String) =
        maintenanceHistoryFlow.value.filter { it.itemId == itemId }.sortedByDescending { it.serviceAt }
    override suspend fun maintenanceHistoryById(id: String) =
        maintenanceHistoryFlow.value.firstOrNull { it.id == id }

    override suspend fun deleteMaintenanceHistory(id: String) {
        maintenanceHistoryFlow.value = maintenanceHistoryFlow.value.filterNot { it.id == id }
    }

    override suspend fun deleteMaintenanceHistoryForItem(itemId: String) {
        maintenanceHistoryFlow.value = maintenanceHistoryFlow.value.filterNot { it.itemId == itemId }
    }

    override suspend fun deleteMileageRecord(id: String) {
        mileageFlow.value = mileageFlow.value.filterNot { it.id == id }
    }

    override suspend fun deleteEnergyRecord(id: String) {
        energyFlow.value = energyFlow.value.filterNot { it.id == id }
    }

    override suspend fun deleteAllVehicles() {
        vehiclesFlow.value = emptyList()
        mileageFlow.value = emptyList()
        energyFlow.value = emptyList()
        settlementFlow.value = emptyList()
        maintenanceFlow.value = emptyList()
        maintenanceHistoryFlow.value = emptyList()
    }

    override fun observeFitActivities(): Flow<List<FitActivityEntity>> = fitActivitiesFlow
    override suspend fun allFitActivities() = fitActivitiesFlow.value
    override suspend fun fitActivityById(id: String) = fitActivitiesFlow.value.firstOrNull { it.id == id }
    override suspend fun activeFitActivity() = fitActivitiesFlow.value.firstOrNull { it.status != FitActivityStatus.Completed }
    override suspend fun upsertFitActivity(activity: FitActivityEntity) { fitActivitiesFlow.value = fitActivitiesFlow.value.filterNot { it.id == activity.id } + activity }
    override suspend fun deleteFitActivity(id: String) { fitActivitiesFlow.value = fitActivitiesFlow.value.filterNot { it.id == id } }
    override suspend fun fitActivityIdsForVehicle(vehicleId: String) = fitActivitiesFlow.value.filter { it.vehicleId == vehicleId }.map { it.id }
    override suspend fun insertFitSample(sample: FitSampleEntity): Long { fitSamplesFlow.value += sample.copy(id = (fitSamplesFlow.value.size + 1).toLong()); return fitSamplesFlow.value.size.toLong() }
    override fun observeFitSamples(activityId: String): Flow<List<FitSampleEntity>> = MutableStateFlow(fitSamplesFlow.value.filter { it.activityId == activityId })
    override suspend fun fitSamples(activityId: String) = fitSamplesFlow.value.filter { it.activityId == activityId }.sortedBy { it.timestamp }
    override suspend fun allFitSamples() = fitSamplesFlow.value
    override suspend fun latestFitSample(activityId: String) = fitSamples(activityId).lastOrNull()
    override suspend fun deleteFitSamples(activityId: String) { fitSamplesFlow.value = fitSamplesFlow.value.filterNot { it.activityId == activityId } }
    override suspend fun fitSampleCount(activityId: String) = fitSamplesFlow.value.count { it.activityId == activityId }
    override suspend fun insertFitTimerEvent(event: FitTimerEventEntity): Long { fitEventsFlow.value += event.copy(id = (fitEventsFlow.value.size + 1).toLong()); return fitEventsFlow.value.size.toLong() }
    override suspend fun fitTimerEvents(activityId: String) = fitEventsFlow.value.filter { it.activityId == activityId }.sortedBy { it.timestamp }
    override suspend fun allFitTimerEvents() = fitEventsFlow.value
    override suspend fun deleteFitTimerEvents(activityId: String) { fitEventsFlow.value = fitEventsFlow.value.filterNot { it.activityId == activityId } }
    override suspend fun deleteAllFitSamples() { fitSamplesFlow.value = emptyList() }
    override suspend fun deleteAllFitTimerEvents() { fitEventsFlow.value = emptyList() }
    override suspend fun deleteAllFitActivities() { fitActivitiesFlow.value = emptyList() }
}

private fun testVehicle(energyType: EnergyType = EnergyType.Fuel) = VehicleEntity(
    id = "v1",
    name = "测试车",
    model = "Model",
    plate = "TEST",
    category = VehicleCategory.Car,
    energyType = energyType,
    transmissionType = if (energyType == EnergyType.Fuel) TransmissionType.Automatic else TransmissionType.ElectricDrive,
    fuelTankCapacityLiters = if (energyType.supportsFuel) 50.0 else null,
    batteryCapacityKwh = if (energyType.hasBattery) 60.0 else null,
    defaultChargeLimitSoc = if (energyType.supportsCharging) 80 else null,
    currentMileage = 1_000.0,
    updatedAt = 0L
)
